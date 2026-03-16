package com.dukaan.feature.ocr.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.feature.billing.domain.repository.BillingRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

enum class ScanProgress {
    IDLE,
    READING_TEXT,
    PARSING_ITEMS,
    DONE
}

data class OcrUiState(
    val isScanning: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val scannedBill: Bill? = null,
    val error: String? = null,
    val isSaved: Boolean = false,
    val capturedImageUri: String? = null,
    val scannedPageUris: List<String> = emptyList(),
    val scanProgress: ScanProgress = ScanProgress.IDLE,
    val docScannerAvailable: Boolean = true,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isAiTyping: Boolean = false,
    val editingBillId: Long? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val geminiService: GeminiBillingService,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    val existingSellerNames: StateFlow<List<String>> = billingRepository.getAllSellerNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cached bitmap for AI chat with image context
    private var cachedBillBitmap: Bitmap? = null

    // ML Kit text recognizer for extracting OCR text from bitmaps
    private val textRecognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )

    /** Extract text from bitmap using ML Kit (on-device OCR) */
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        }
    }

    /** Extract text from multiple bitmaps */
    private suspend fun extractTextsFromBitmaps(bitmaps: List<Bitmap>): List<String> {
        return bitmaps.map { extractTextFromBitmap(it) }
    }

    /** Process a captured camera image via ML Kit OCR + Gemini vision (dual extraction) */
    fun processCapturedImage(imagePath: String) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null, capturedImageUri = imagePath, scanProgress = ScanProgress.READING_TEXT) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(imagePath)
                }
                if (bitmap != null) {
                    cachedBillBitmap = bitmap

                    // Step 1: Extract text using ML Kit (on-device OCR)
                    val ocrText = extractTextFromBitmap(bitmap)

                    _uiState.update { it.copy(scanProgress = ScanProgress.PARSING_ITEMS) }

                    // Step 2: Send BOTH image + OCR text to Gemini for best accuracy
                    val bill = if (ocrText.isNotBlank()) {
                        geminiService.parseBillImageWithOcr(bitmap, ocrText)
                    } else {
                        geminiService.parseBillImage(bitmap)
                    }

                    _uiState.update { it.copy(scannedBill = bill, isScanning = false, scanProgress = ScanProgress.DONE) }
                } else {
                    _uiState.update { it.copy(error = "Failed to load captured image.", isScanning = false, scanProgress = ScanProgress.IDLE) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to process bill image.", isScanning = false, scanProgress = ScanProgress.IDLE) }
            }
        }
    }

    /** Process a gallery image via ML Kit OCR + Gemini vision (dual extraction) */
    fun processGalleryImage(context: Context, imageUri: Uri) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null, scanProgress = ScanProgress.READING_TEXT) }
            try {
                val result = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        // Save a copy for bill photo reference
                        val photoDir = File(context.filesDir, "bills")
                        photoDir.mkdirs()
                        val photoFile = File(photoDir, "${System.currentTimeMillis()}.jpg")
                        photoFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        Pair(bitmap, photoFile.absolutePath)
                    } else {
                        null
                    }
                }

                if (result != null) {
                    val (bitmap, savedPath) = result
                    cachedBillBitmap = bitmap
                    _uiState.update { it.copy(capturedImageUri = savedPath) }

                    // Step 1: Extract text using ML Kit
                    val ocrText = extractTextFromBitmap(bitmap)

                    _uiState.update { it.copy(scanProgress = ScanProgress.PARSING_ITEMS) }

                    // Step 2: Send BOTH image + OCR text to Gemini
                    val bill = if (ocrText.isNotBlank()) {
                        geminiService.parseBillImageWithOcr(bitmap, ocrText)
                    } else {
                        geminiService.parseBillImage(bitmap)
                    }

                    _uiState.update { it.copy(scannedBill = bill, isScanning = false, scanProgress = ScanProgress.DONE) }
                } else {
                    _uiState.update { it.copy(error = "Failed to load image.", isScanning = false, scanProgress = ScanProgress.IDLE) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to process image.", isScanning = false, scanProgress = ScanProgress.IDLE) }
            }
        }
    }

    /** Fallback: process raw OCR text (used when image capture fails) */
    fun onTextRecognized(rawText: String) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val bill = geminiService.parseOcrText(rawText)
                _uiState.update { it.copy(scannedBill = bill, isScanning = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Parsing failed. Please try scanning again.", isScanning = false) }
            }
        }
    }

    /** Send a chat message to AI about the bill */
    fun sendChatMessage(message: String, languageCode: String = "en") {
        val bill = _uiState.value.scannedBill ?: return

        // Add user message
        _uiState.update { state ->
            state.copy(
                chatMessages = state.chatMessages + ChatMessage(isUser = true, text = message),
                isAiTyping = true
            )
        }

        viewModelScope.launch {
            try {
                val billJson = buildString {
                    append("Seller: ${bill.sellerName}, Bill#: ${bill.billNumber}\n")
                    append("Items:\n")
                    bill.items.forEachIndexed { i, item ->
                        append("${i + 1}. ${item.name} - ${item.quantity} ${item.unit} — ₹${item.total}\n")
                    }
                    append("Total: ₹${bill.totalAmount}")
                }
                val response = geminiService.chatAboutBill(
                    billJson = billJson,
                    userMessage = message,
                    image = cachedBillBitmap,
                    languageCode = languageCode
                )
                _uiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages + ChatMessage(isUser = false, text = response),
                        isAiTyping = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages + ChatMessage(
                            isUser = false,
                            text = "Sorry, something went wrong. Please try again."
                        ),
                        isAiTyping = false
                    )
                }
            }
        }
    }

    fun deleteItem(item: BillItem) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val newItems = bill.items.filter { it != item }
                state.copy(scannedBill = bill.copy(items = newItems, totalAmount = newItems.sumOf { it.total }))
            } ?: state
        }
    }

    fun editItem(index: Int, updatedItem: BillItem) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val newItems = bill.items.toMutableList().apply { set(index, updatedItem) }
                state.copy(scannedBill = bill.copy(items = newItems, totalAmount = newItems.sumOf { it.total }))
            } ?: state
        }
    }

    fun addItem(item: BillItem) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val newItems = bill.items + item
                state.copy(scannedBill = bill.copy(items = newItems, totalAmount = newItems.sumOf { it.total }))
            } ?: state
        }
    }

    fun updateSellerName(name: String) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                state.copy(scannedBill = bill.copy(sellerName = name))
            } ?: state
        }
    }

    fun saveBill() {
        val bill = _uiState.value.scannedBill ?: return
        if (_uiState.value.isSaving) return

        // If editing, delegate to saveEditedBill
        if (_uiState.value.editingBillId != null) {
            saveEditedBill()
            return
        }

        val validItems = bill.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _uiState.update { it.copy(saveError = "Cannot save bill with no items.") }
            return
        }

        val imagePath = _uiState.value.capturedImageUri
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val billToSave = bill.copy(
                    items = validItems,
                    totalAmount = validItems.sumOf { it.total }
                )
                billingRepository.saveBill(billToSave, "OCR", imagePath)
                _uiState.update { it.copy(isSaved = true, isSaving = false, scannedBill = null) }
                cachedBillBitmap = null
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSaving = false,
                    saveError = "Failed to save bill: ${e.localizedMessage ?: "Unknown error"}"
                ) }
            }
        }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
    }

    fun setDocScannerUnavailable() {
        _uiState.update { it.copy(docScannerAvailable = false) }
    }

    /** Process scanned pages from ML Kit Document Scanner with dual extraction */
    fun processScannedPages(context: Context, pageUris: List<Uri>) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null, scanProgress = ScanProgress.READING_TEXT) }
            try {
                val bitmaps = withContext(Dispatchers.IO) {
                    pageUris.mapNotNull { uri ->
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }
                }

                if (bitmaps.isEmpty()) {
                    _uiState.update { it.copy(error = "Failed to load scanned pages.", isScanning = false, scanProgress = ScanProgress.IDLE) }
                    return@launch
                }

                // Step 1: Run ML Kit OCR on all bitmaps (on-device, fast)
                val ocrTexts = extractTextsFromBitmaps(bitmaps)

                // Save page images to internal storage
                val savedPaths = withContext(Dispatchers.IO) {
                    val photoDir = File(context.filesDir, "bills")
                    photoDir.mkdirs()
                    bitmaps.mapIndexed { index, bitmap ->
                        val file = File(photoDir, "${System.currentTimeMillis()}_page${index}.jpg")
                        file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        file.absolutePath
                    }
                }

                _uiState.update { it.copy(
                    capturedImageUri = savedPaths.firstOrNull(),
                    scannedPageUris = savedPaths,
                    scanProgress = ScanProgress.PARSING_ITEMS
                ) }

                cachedBillBitmap = bitmaps.first()

                // Step 2: Send images + OCR text to Gemini (dual extraction)
                val hasOcrText = ocrTexts.any { it.isNotBlank() }
                val bill = if (bitmaps.size == 1) {
                    if (hasOcrText) {
                        geminiService.parseBillImageWithOcr(bitmaps.first(), ocrTexts.first())
                    } else {
                        geminiService.parseBillImage(bitmaps.first())
                    }
                } else {
                    if (hasOcrText) {
                        geminiService.parseMultiPageBillWithOcr(bitmaps, ocrTexts)
                    } else {
                        geminiService.parseMultiPageBill(bitmaps)
                    }
                }

                _uiState.update { it.copy(scannedBill = bill, isScanning = false, scanProgress = ScanProgress.DONE) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to process scanned pages: ${e.localizedMessage}",
                    isScanning = false,
                    scanProgress = ScanProgress.IDLE
                ) }
            }
        }
    }

    /** Load an existing purchase bill for editing */
    fun loadBillForEditing(billId: Long) {
        viewModelScope.launch {
            val bill = billingRepository.getBillById(billId) ?: return@launch
            _uiState.update {
                it.copy(
                    scannedBill = bill,
                    capturedImageUri = bill.imagePath,
                    scannedPageUris = if (bill.imagePath != null) listOf(bill.imagePath!!) else emptyList(),
                    isSaved = false,
                    editingBillId = billId
                )
            }
            // Try to load cached bitmap from the image path
            if (bill.imagePath != null) {
                withContext(Dispatchers.IO) {
                    try {
                        cachedBillBitmap = BitmapFactory.decodeFile(bill.imagePath)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /** Save an edited purchase bill (delete old + insert new) */
    fun saveEditedBill() {
        val bill = _uiState.value.scannedBill ?: return
        val editingId = _uiState.value.editingBillId ?: return
        if (_uiState.value.isSaving) return

        val validItems = bill.items.filter { it.name.isNotBlank() }
        if (validItems.isEmpty()) {
            _uiState.update { it.copy(saveError = "Cannot save bill with no items.") }
            return
        }

        val imagePath = _uiState.value.capturedImageUri
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                // Delete the old bill first
                billingRepository.deleteBill(editingId)
                // Save as new
                val billToSave = bill.copy(
                    items = validItems,
                    totalAmount = validItems.sumOf { it.total }
                )
                billingRepository.saveBill(billToSave, "OCR", imagePath)
                _uiState.update { it.copy(isSaved = true, isSaving = false, scannedBill = null, editingBillId = null) }
                cachedBillBitmap = null
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSaving = false,
                    saveError = "Failed to save bill: ${e.localizedMessage ?: "Unknown error"}"
                ) }
            }
        }
    }

    fun resetScan() {
        cachedBillBitmap = null
        _uiState.value = OcrUiState()
    }
}
