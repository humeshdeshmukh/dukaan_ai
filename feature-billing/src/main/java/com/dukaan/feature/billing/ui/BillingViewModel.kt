package com.dukaan.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.dao.ShopProfileDao
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.feature.billing.domain.repository.BillingRepository
import com.dukaan.core.voice.SpeechManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

enum class ScanListProgress { IDLE, READING_TEXT, PARSING_ITEMS, DONE }

data class BillingUiState(
    val items: List<BillItem> = emptyList(),
    val isRecording: Boolean = false,
    val isContinuousListening: Boolean = false,
    val recognizedText: String = "",
    val isParsing: Boolean = false,
    val audioLevel: Float = 0f,
    val error: String? = null,
    val isSaved: Boolean = false,
    // Calculation
    val subtotal: Double = 0.0,
    val discountPercent: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    // Customer
    val selectedCustomerName: String = "",
    val selectedCustomerId: Long? = null,
    val selectedCustomerPhone: String = "",
    // Payment
    val paymentMode: String = "CASH",
    val notes: String = "",
    // UI
    val editingItemIndex: Int = -1,
    val snackbarMessage: String? = null,
    // Data
    val customers: List<CustomerEntity> = emptyList(),
    // Tab
    val selectedTab: Int = 0,
    // Editing existing bill
    val editingBillId: Long? = null,
    // Scan List
    val isScanningList: Boolean = false,
    val scanListProgress: ScanListProgress = ScanListProgress.IDLE,
    val scanListPreviewItems: List<BillItem>? = null
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager,
    private val khataDao: KhataDao,
    private val shopProfileDao: ShopProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    // ML Kit text recognizer — Devanagari model covers Hindi + English scripts
    private val textRecognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )

    // Language code from user's settings — used for AI parsing
    private val languageCode: StateFlow<String> = shopProfileDao.getProfile()
        .map { it?.languageCode ?: "en" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    val allBills: StateFlow<List<Bill>> = repository.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceBills: StateFlow<List<Bill>> = repository.getVoiceBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseBills: StateFlow<List<Bill>> = repository.getScannedBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Collect partial speech results for live display
        viewModelScope.launch {
            speechManager.speechText.collect { text ->
                _uiState.update { it.copy(recognizedText = text) }
            }
        }
        // Collect actual listening state from SpeechManager
        viewModelScope.launch {
            speechManager.isListening.collect { listening ->
                _uiState.update { it.copy(isRecording = listening) }
            }
        }
        // Collect continuous mode state
        viewModelScope.launch {
            speechManager.isContinuousMode.collect { continuous ->
                _uiState.update { it.copy(isContinuousListening = continuous) }
            }
        }
        // Collect audio level for UI indicator
        viewModelScope.launch {
            speechManager.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
        // Collect final result and auto-process it
        viewModelScope.launch {
            speechManager.finalResult.collect { text ->
                if (text.isNotBlank()) {
                    processSpeech(text)
                }
            }
        }
        // Collect speech errors - suppress transient errors during continuous mode
        viewModelScope.launch {
            speechManager.error.collect { errorCode ->
                val msg = when (errorCode) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard. Tap mic and speak."
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic and speak."
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                    android.speech.SpeechRecognizer.ERROR_NETWORK,
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet not available."
                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "Mic error. Try again."
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> "Speech not available on this device."
                    android.speech.SpeechRecognizer.ERROR_SERVER -> "Server error. Retrying..."
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Wait and try again."
                    11 -> "Server disconnected. Retrying..." // ERROR_SERVER_DISCONNECTED
                    10 -> "Too many requests. Retrying..." // ERROR_TOO_MANY_REQUESTS
                    else -> "Voice error ($errorCode). Try again."
                }
                _uiState.update { it.copy(error = msg) }
            }
        }
        loadCustomers()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording || _uiState.value.isContinuousListening) {
            speechManager.stopListening()
        } else {
            // Start in continuous mode — auto-restarts after each result
            speechManager.startListening(
                continuous = true,
                speechCode = SupportedLanguages.getSpeechCode(languageCode.value)
            )
        }
    }

    private fun processSpeech(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, error = null) }
            try {
                val parsedItems = geminiService.parseBillingSpeech(text, languageCode.value)
                _uiState.update { state ->
                    val mergedItems = mergeItems(state.items, parsedItems)
                    state.copy(
                        items = mergedItems,
                        isParsing = false,
                        recognizedText = ""
                    )
                }
                recalculate()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        error = "Failed to parse speech. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Smart merge: if a new item has the same name (case-insensitive) and unit as an existing item,
     * combine quantities and sum prices. Otherwise append as a new item.
     */
    private fun mergeItems(existing: List<BillItem>, incoming: List<BillItem>): List<BillItem> {
        val result = existing.toMutableList()
        for (newItem in incoming) {
            val matchIndex = result.indexOfFirst {
                it.name.equals(newItem.name, ignoreCase = true) && it.unit.equals(newItem.unit, ignoreCase = true)
            }
            if (matchIndex >= 0) {
                val old = result[matchIndex]
                result[matchIndex] = BillItem(
                    name = old.name,
                    quantity = old.quantity + newItem.quantity,
                    unit = old.unit,
                    price = old.price + newItem.price
                )
            } else {
                result.add(newItem)
            }
        }
        return result
    }

    fun removeItem(item: BillItem) {
        _uiState.update { state ->
            state.copy(items = state.items.filter { it != item })
        }
        recalculate()
    }

    fun updateItem(index: Int, updated: BillItem) {
        _uiState.update { state ->
            val newItems = state.items.toMutableList()
            if (index in newItems.indices) {
                newItems[index] = updated
            }
            state.copy(items = newItems, editingItemIndex = -1)
        }
        recalculate()
    }

    fun addItemManually(name: String, quantity: Double, unit: String, price: Double) {
        val item = BillItem(name = name, quantity = quantity, unit = unit, price = price)
        _uiState.update { state ->
            state.copy(items = state.items + item)
        }
        recalculate()
    }

    fun setDiscount(percent: Double) {
        _uiState.update { it.copy(discountPercent = percent.coerceIn(0.0, 100.0)) }
        recalculate()
    }

    fun setTax(percent: Double) {
        _uiState.update { it.copy(taxPercent = percent.coerceIn(0.0, 100.0)) }
        recalculate()
    }

    fun setPaymentMode(mode: String) {
        _uiState.update { it.copy(paymentMode = mode) }
    }

    fun selectCustomer(id: Long?, name: String, phone: String = "") {
        _uiState.update { it.copy(selectedCustomerId = id, selectedCustomerName = name, selectedCustomerPhone = phone) }
    }

    fun clearCustomer() {
        _uiState.update { it.copy(selectedCustomerId = null, selectedCustomerName = "", selectedCustomerPhone = "") }
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun setEditingItem(index: Int) {
        _uiState.update { it.copy(editingItemIndex = index) }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun recalculate() {
        _uiState.update { state ->
            val subtotal = state.items.sumOf { it.total }
            val discountAmount = subtotal * state.discountPercent / 100.0
            val afterDiscount = subtotal - discountAmount
            val taxAmount = afterDiscount * state.taxPercent / 100.0
            val grandTotal = afterDiscount + taxAmount
            state.copy(
                subtotal = subtotal,
                discountAmount = discountAmount,
                taxAmount = taxAmount,
                grandTotal = grandTotal
            )
        }
    }

    private fun loadCustomers() {
        viewModelScope.launch {
            khataDao.getAllCustomers().collect { customers ->
                _uiState.update { it.copy(customers = customers) }
            }
        }
    }

    fun saveBill(asDraft: Boolean = false) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.items.isEmpty()) return@launch
            // If editing an existing bill, delete the old one first
            state.editingBillId?.let { oldId ->
                repository.deleteBill(oldId)
            }
            val bill = Bill(
                items = state.items,
                totalAmount = state.grandTotal,
                subtotal = state.subtotal,
                discountPercent = state.discountPercent,
                discountAmount = state.discountAmount,
                taxPercent = state.taxPercent,
                taxAmount = state.taxAmount,
                customerName = state.selectedCustomerName,
                customerId = state.selectedCustomerId,
                customerPhone = state.selectedCustomerPhone,
                paymentMode = state.paymentMode,
                notes = state.notes,
                isDraft = asDraft
            )
            repository.saveBill(bill, "VOICE")
            val msg = if (asDraft) "Draft saved!" else "Bill saved!"
            // Stop listening if still active
            speechManager.stopListening()
            // Clear the bill and show message
            _uiState.value = BillingUiState(
                customers = state.customers,
                snackbarMessage = msg,
                isSaved = !asDraft
            )
        }
    }

    fun buildBillForPdf(): Bill {
        val state = _uiState.value
        return Bill(
            items = state.items,
            totalAmount = state.grandTotal,
            subtotal = state.subtotal,
            discountPercent = state.discountPercent,
            discountAmount = state.discountAmount,
            taxPercent = state.taxPercent,
            taxAmount = state.taxAmount,
            customerName = state.selectedCustomerName,
            customerId = state.selectedCustomerId,
            customerPhone = state.selectedCustomerPhone,
            paymentMode = state.paymentMode,
            notes = state.notes
        )
    }

    fun clearBill() {
        speechManager.stopListening()
        _uiState.value = BillingUiState(customers = _uiState.value.customers)
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch {
            repository.deleteBill(billId)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    suspend fun getBillById(id: Long): Bill? = repository.getBillById(id)

    fun loadBillForEditing(billId: Long) {
        viewModelScope.launch {
            val bill = repository.getBillById(billId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    items = bill.items,
                    selectedCustomerName = bill.customerName,
                    selectedCustomerId = bill.customerId,
                    selectedCustomerPhone = bill.customerPhone,
                    discountPercent = bill.discountPercent,
                    taxPercent = bill.taxPercent,
                    paymentMode = bill.paymentMode,
                    notes = bill.notes,
                    editingBillId = bill.id,
                    selectedTab = 0
                )
            }
            recalculate()
        }
    }

    /** Enhance image contrast to make handwritten text more visible */
    private fun enhanceImageForHandwriting(bitmap: Bitmap): Bitmap {
        val enhancedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint()

        // Increase contrast and slightly increase saturation to make pen marks stand out
        val colorMatrix = ColorMatrix().apply {
            // Contrast: 1.3x, Brightness: slight boost
            set(floatArrayOf(
                1.4f, 0f, 0f, 0f, -30f,  // Red
                0f, 1.4f, 0f, 0f, -30f,  // Green
                0f, 0f, 1.4f, 0f, -30f,  // Blue
                0f, 0f, 0f, 1f, 0f       // Alpha
            ))
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhancedBitmap
    }

    /** Extract text from a bitmap using ML Kit on-device OCR */
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }
    }

    /** Process pages from GmsDocumentScanning: ML Kit OCR + Gemini dual extraction */
    fun processScannedCustomerListPages(context: Context, pageUris: List<Uri>) {
        if (_uiState.value.isScanningList) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningList = true, error = null, scanListProgress = ScanListProgress.READING_TEXT) }
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    pageUris.mapNotNull { uri ->
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }
                if (bitmaps.isEmpty()) {
                    _uiState.update { it.copy(isScanningList = false, error = "Failed to load scanned pages.", scanListProgress = ScanListProgress.IDLE) }
                    return@launch
                }

                // Step 1: ML Kit OCR on original images (better for printed text)
                val ocrTexts = bitmaps.map { extractTextFromBitmap(it) }
                _uiState.update { it.copy(scanListProgress = ScanListProgress.PARSING_ITEMS) }

                // Step 2: Enhance images for better handwriting detection, then send to Gemini
                val allItems = mutableListOf<BillItem>()
                bitmaps.forEachIndexed { i, bitmap ->
                    val ocrText = ocrTexts.getOrElse(i) { "" }
                    // Use contrast-enhanced image for Gemini to better detect handwritten text
                    val enhancedBitmap = withContext(Dispatchers.Default) { enhanceImageForHandwriting(bitmap) }
                    val items = geminiService.parseCustomerListImage(enhancedBitmap, ocrText)
                    allItems.addAll(items)
                    // Clean up enhanced bitmap
                    if (enhancedBitmap != bitmap) enhancedBitmap.recycle()
                }

                // Merge duplicate items (same name + unit → sum quantities & prices)
                val merged = allItems
                    .groupBy { "${it.name.lowercase().trim()}_${it.unit.lowercase().trim()}" }
                    .map { (_, group) ->
                        group.first().copy(
                            quantity = group.sumOf { it.quantity },
                            price = group.sumOf { it.price }
                        )
                    }

                // Directly add items to bill (skip preview screen)
                val validItems = merged.filter { it.name.isNotBlank() }
                _uiState.update { state ->
                    val mergedWithExisting = mergeItems(state.items, validItems)
                    state.copy(
                        items = mergedWithExisting,
                        isScanningList = false,
                        scanListProgress = ScanListProgress.DONE,
                        scanListPreviewItems = null,
                        snackbarMessage = "${validItems.size} ${if (validItems.size == 1) "item" else "items"} added from scanned list!"
                    )
                }
                recalculate()
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningList = false, error = "Could not read the list. Try again.", scanListProgress = ScanListProgress.IDLE) }
            }
        }
    }

    fun processCustomerListFromUri(context: Context, uri: Uri) {
        if (_uiState.value.isScanningList) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningList = true, error = null, scanListProgress = ScanListProgress.READING_TEXT) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    bmp
                }
                if (bitmap != null) {
                    // ML Kit OCR first (on original image)
                    val ocrText = extractTextFromBitmap(bitmap)
                    _uiState.update { it.copy(scanListProgress = ScanListProgress.PARSING_ITEMS) }

                    // Enhance image for better handwriting detection
                    val enhancedBitmap = withContext(Dispatchers.Default) { enhanceImageForHandwriting(bitmap) }
                    val items = geminiService.parseCustomerListImage(enhancedBitmap, ocrText)
                    if (enhancedBitmap != bitmap) enhancedBitmap.recycle()

                    val validItems = items.filter { it.name.isNotBlank() }

                    // Directly add items to bill
                    _uiState.update { state ->
                        val mergedWithExisting = mergeItems(state.items, validItems)
                        state.copy(
                            items = mergedWithExisting,
                            isScanningList = false,
                            scanListProgress = ScanListProgress.DONE,
                            scanListPreviewItems = null,
                            snackbarMessage = "${validItems.size} ${if (validItems.size == 1) "item" else "items"} added from scanned list!"
                        )
                    }
                    recalculate()
                } else {
                    _uiState.update { it.copy(isScanningList = false, error = "Failed to load image.", scanListProgress = ScanListProgress.IDLE) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningList = false, error = "Could not read the list. Try again.", scanListProgress = ScanListProgress.IDLE) }
            }
        }
    }

    fun processCustomerListFromPath(imagePath: String) {
        if (_uiState.value.isScanningList) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningList = true, error = null, scanListProgress = ScanListProgress.READING_TEXT) }
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imagePath) }
                if (bitmap != null) {
                    // ML Kit OCR first (on original image)
                    val ocrText = extractTextFromBitmap(bitmap)
                    _uiState.update { it.copy(scanListProgress = ScanListProgress.PARSING_ITEMS) }

                    // Enhance image for better handwriting detection
                    val enhancedBitmap = withContext(Dispatchers.Default) { enhanceImageForHandwriting(bitmap) }
                    val items = geminiService.parseCustomerListImage(enhancedBitmap, ocrText)
                    if (enhancedBitmap != bitmap) enhancedBitmap.recycle()

                    val validItems = items.filter { it.name.isNotBlank() }

                    // Directly add items to bill
                    _uiState.update { state ->
                        val mergedWithExisting = mergeItems(state.items, validItems)
                        state.copy(
                            items = mergedWithExisting,
                            isScanningList = false,
                            scanListProgress = ScanListProgress.DONE,
                            scanListPreviewItems = null,
                            snackbarMessage = "${validItems.size} ${if (validItems.size == 1) "item" else "items"} added from scanned list!"
                        )
                    }
                    recalculate()
                } else {
                    _uiState.update { it.copy(isScanningList = false, error = "Failed to load image.", scanListProgress = ScanListProgress.IDLE) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanningList = false, error = "Could not read the list. Try again.", scanListProgress = ScanListProgress.IDLE) }
            }
        }
    }

    fun confirmScannedListItems(items: List<BillItem>) {
        val valid = items.filter { it.name.isNotBlank() }
        _uiState.update { state ->
            val merged = mergeItems(state.items, valid)
            state.copy(
                items = merged,
                scanListPreviewItems = null,
                snackbarMessage = "${valid.size} ${if (valid.size == 1) "item" else "items"} added to bill!"
            )
        }
        recalculate()
    }

    fun dismissScanListPreview() {
        _uiState.update { it.copy(scanListPreviewItems = null) }
    }

    fun formatWhatsAppMessage(): String {
        val state = _uiState.value
        val sb = StringBuilder()
        sb.append("*Dukaan AI - Bill*\n")
        if (state.selectedCustomerName.isNotEmpty()) {
            sb.append("Customer: ${state.selectedCustomerName}\n")
        }
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        state.items.forEach { item ->
            sb.append("${item.name}: ${item.quantity} ${item.unit} \u2014 \u20B9${String.format("%.2f", item.total)}\n")
        }
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        sb.append("Subtotal: \u20B9${String.format("%.2f", state.subtotal)}\n")
        if (state.discountPercent > 0) {
            sb.append("Discount (${state.discountPercent}%): -\u20B9${String.format("%.2f", state.discountAmount)}\n")
        }
        if (state.taxPercent > 0) {
            sb.append("Tax/GST (${state.taxPercent}%): +\u20B9${String.format("%.2f", state.taxAmount)}\n")
        }
        sb.append("*Total: \u20B9${String.format("%.2f", state.grandTotal)}*\n")
        sb.append("Payment: ${state.paymentMode}\n")
        if (state.notes.isNotEmpty()) {
            sb.append("Note: ${state.notes}\n")
        }
        sb.append("\n_Sent via Dukaan AI_")
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }
}
