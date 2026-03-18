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
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlin.math.roundToInt

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
    val editingBillId: Long? = null,
    // Accuracy validation fields
    val subtotalMismatch: SubtotalMismatch? = null,
    val originalExtractedSubtotal: Double = 0.0  // Store original for comparison when items change
)

/**
 * Represents a mismatch between Gemini's extracted subtotal and calculated items sum.
 */
data class SubtotalMismatch(
    val extractedSubtotal: Double,
    val calculatedItemsSum: Double,
    val difference: Double
) {
    val isSignificant: Boolean get() = kotlin.math.abs(difference) > 0.50  // ₹0.50 tolerance
}

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

    // ML Kit text recognizers: Devanagari handles Hindi script, Latin handles English.
    // Both are run in parallel on every image to maximise accuracy on mixed-script Indian bills.
    private val devanagariRecognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )
    private val latinRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    /** Run a single ML Kit recognizer on a bitmap, returning extracted text or "". */
    private suspend fun extractText(bitmap: Bitmap, recognizer: TextRecognizer): String =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resume("") }
        }

    /**
     * Extract text from bitmap using both Devanagari and Latin recognizers in parallel.
     * Merges their results so Gemini receives both Hindi and English content from the bill.
     */
    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String = coroutineScope {
        val devanagariDeferred = async { extractText(bitmap, devanagariRecognizer) }
        val latinDeferred = async { extractText(bitmap, latinRecognizer) }
        val devanagari = devanagariDeferred.await()
        val latin = latinDeferred.await()
        // Merge unique lines from both recognizers — Gemini handles any duplicates
        val combined = (devanagari.lines() + latin.lines())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        combined
    }

    /** Extract text from multiple bitmaps in parallel. */
    private suspend fun extractTextsFromBitmaps(bitmaps: List<Bitmap>): List<String> =
        coroutineScope {
            bitmaps.map { bitmap -> async { extractTextFromBitmap(bitmap) } }.awaitAll()
        }

    /**
     * Resize bitmap so the longest side is at most [maxDim] pixels before sending to Gemini.
     * ML Kit OCR runs on the full-res image before this; Gemini benefits from higher res for handwriting.
     */
    private fun resizeBitmapForGemini(bitmap: Bitmap, maxDim: Int = 2048): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
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

                    // Step 1: Extract text using ML Kit (on-device OCR, full-res)
                    val ocrText = extractTextFromBitmap(bitmap)

                    _uiState.update { it.copy(scanProgress = ScanProgress.PARSING_ITEMS) }

                    // Step 2: Resize for Gemini, then send image + OCR text for best accuracy
                    val geminiImage = withContext(Dispatchers.Default) { resizeBitmapForGemini(bitmap) }
                    val bill = if (ocrText.isNotBlank()) {
                        geminiService.parseBillImageWithOcr(geminiImage, ocrText)
                    } else {
                        geminiService.parseBillImage(geminiImage)
                    }

                    // Validate subtotal accuracy
                    val mismatch = validateSubtotal(bill)

                    _uiState.update { it.copy(
                        scannedBill = bill,
                        isScanning = false,
                        scanProgress = ScanProgress.DONE,
                        subtotalMismatch = mismatch,
                        originalExtractedSubtotal = bill.subtotal
                    ) }
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

                    // Step 1: Extract text using ML Kit (full-res bitmap)
                    val ocrText = extractTextFromBitmap(bitmap)

                    _uiState.update { it.copy(scanProgress = ScanProgress.PARSING_ITEMS) }

                    // Step 2: Resize for Gemini, then send image + OCR text
                    val geminiImage = withContext(Dispatchers.Default) { resizeBitmapForGemini(bitmap) }
                    val bill = if (ocrText.isNotBlank()) {
                        geminiService.parseBillImageWithOcr(geminiImage, ocrText)
                    } else {
                        geminiService.parseBillImage(geminiImage)
                    }

                    // Validate subtotal accuracy
                    val mismatch = validateSubtotal(bill)

                    _uiState.update { it.copy(
                        scannedBill = bill,
                        isScanning = false,
                        scanProgress = ScanProgress.DONE,
                        subtotalMismatch = mismatch,
                        originalExtractedSubtotal = bill.subtotal
                    ) }
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
                val updatedBill = bill.copy(items = newItems).withRecalculatedTotals(bill)
                // Recalculate mismatch with new items using stored original subtotal
                val newMismatch = recalculateMismatch(updatedBill, state.originalExtractedSubtotal)
                state.copy(scannedBill = updatedBill, subtotalMismatch = newMismatch)
            } ?: state
        }
    }

    fun editItem(index: Int, updatedItem: BillItem) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val newItems = bill.items.toMutableList().apply { set(index, updatedItem) }
                val updatedBill = bill.copy(items = newItems).withRecalculatedTotals(bill)
                // Recalculate mismatch with new items using stored original subtotal
                val newMismatch = recalculateMismatch(updatedBill, state.originalExtractedSubtotal)
                state.copy(scannedBill = updatedBill, subtotalMismatch = newMismatch)
            } ?: state
        }
    }

    fun addItem(item: BillItem) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val newItems = bill.items + item
                val updatedBill = bill.copy(items = newItems).withRecalculatedTotals(bill)
                // Recalculate mismatch with new items using stored original subtotal
                val newMismatch = recalculateMismatch(updatedBill, state.originalExtractedSubtotal)
                state.copy(scannedBill = updatedBill, subtotalMismatch = newMismatch)
            } ?: state
        }
    }

    /**
     * Recalculate mismatch when items change.
     * Uses the original extracted subtotal for comparison.
     */
    private fun recalculateMismatch(bill: Bill, originalExtractedSubtotal: Double): SubtotalMismatch? {
        val itemsSum = bill.items.sumOf { it.total }

        // Only show mismatch if original subtotal was > 0
        if (originalExtractedSubtotal <= 0) return null

        val difference = originalExtractedSubtotal - itemsSum

        // Return mismatch if difference exceeds tolerance (₹0.50)
        return if (kotlin.math.abs(difference) > 0.50) {
            SubtotalMismatch(
                extractedSubtotal = originalExtractedSubtotal,
                calculatedItemsSum = itemsSum,
                difference = difference
            )
        } else null
    }

    /**
     * Recalculates subtotal, totalAmount, discountPercent, and taxPercent after items change.
     * discountAmount and taxAmount (rupee values) are preserved — only the derived % fields update.
     */
    private fun Bill.withRecalculatedTotals(original: Bill): Bill {
        val newSubtotal = items.sumOf { it.total }
        val newTotal = (newSubtotal - original.discountAmount + original.taxAmount).coerceAtLeast(0.0)
        // Round discount percent to 2 decimal places to avoid confusing values
        val calculatedDiscountPercent = if (newSubtotal > 0 && original.discountAmount > 0)
            (original.discountAmount / newSubtotal * 100) else original.discountPercent
        val newDiscountPercent = (calculatedDiscountPercent * 100).roundToInt() / 100.0
        val taxableAmount = newSubtotal - original.discountAmount
        val newTaxPercent = if (taxableAmount > 0 && original.taxAmount > 0)
            (original.taxAmount / taxableAmount * 100) else original.taxPercent
        return copy(
            subtotal = newSubtotal,
            totalAmount = newTotal,
            discountAmount = original.discountAmount,
            discountPercent = newDiscountPercent,
            taxAmount = original.taxAmount,
            taxPercent = newTaxPercent
        )
    }

    fun updateSellerName(name: String) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                state.copy(scannedBill = bill.copy(sellerName = name))
            } ?: state
        }
    }

    fun updateSellerPhone(phone: String) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                state.copy(scannedBill = bill.copy(sellerPhone = phone))
            } ?: state
        }
    }

    /**
     * Validates subtotal accuracy by comparing Gemini's extracted subtotal
     * with the calculated sum of item totals.
     */
    private fun validateSubtotal(bill: Bill): SubtotalMismatch? {
        val itemsSum = bill.items.sumOf { it.total }
        val extractedSubtotal = bill.subtotal

        // Only check if Gemini returned a subtotal > 0
        if (extractedSubtotal <= 0) return null

        val difference = extractedSubtotal - itemsSum

        // Return mismatch if difference exceeds tolerance (₹0.50)
        return if (kotlin.math.abs(difference) > 0.50) {
            SubtotalMismatch(
                extractedSubtotal = extractedSubtotal,
                calculatedItemsSum = itemsSum,
                difference = difference
            )
        } else null
    }

    /**
     * User chose to use the calculated items sum as subtotal (fixes mismatch).
     */
    fun useCalculatedSubtotal() {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val itemsSum = bill.items.sumOf { it.total }
                val updatedBill = bill.copy(subtotal = itemsSum)
                state.copy(
                    scannedBill = updatedBill.withRecalculatedTotals(updatedBill),
                    subtotalMismatch = null
                )
            } ?: state
        }
    }

    /**
     * User chose to ignore the subtotal mismatch warning.
     */
    fun dismissSubtotalMismatch() {
        _uiState.update { it.copy(subtotalMismatch = null) }
    }

    /**
     * Updates discount percentage and recalculates totals.
     */
    fun updateDiscountPercent(percent: Double) {
        // Round to 2 decimal places to avoid confusing values like "5.263157894736842%"
        val roundedPercent = (percent * 100).roundToInt() / 100.0
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val subtotal = bill.items.sumOf { it.total }
                val discountAmount = subtotal * roundedPercent / 100.0
                val afterDiscount = subtotal - discountAmount
                val taxAmount = afterDiscount * bill.taxPercent / 100.0
                val total = afterDiscount + taxAmount
                state.copy(scannedBill = bill.copy(
                    discountPercent = roundedPercent,
                    discountAmount = discountAmount,
                    taxAmount = taxAmount,
                    totalAmount = total
                ))
            } ?: state
        }
    }

    /**
     * Updates tax percentage and recalculates totals.
     */
    fun updateTaxPercent(percent: Double) {
        _uiState.update { state ->
            state.scannedBill?.let { bill ->
                val subtotal = bill.items.sumOf { it.total }
                val afterDiscount = subtotal - bill.discountAmount
                val taxAmount = afterDiscount * percent / 100.0
                val total = afterDiscount + taxAmount
                state.copy(scannedBill = bill.copy(
                    taxPercent = percent,
                    taxAmount = taxAmount,
                    totalAmount = total
                ))
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
                // Use the bill's totalAmount as-is (already accounts for discount/GST from Gemini,
                // or recalculated correctly when user edited items).
                val billToSave = bill.copy(items = validItems)
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

                // Step 2: Resize for Gemini (OCR already done on full-res above), then send to Gemini
                val geminiImages = withContext(Dispatchers.Default) {
                    bitmaps.map { resizeBitmapForGemini(it) }
                }
                val hasOcrText = ocrTexts.any { it.isNotBlank() }
                val bill = if (geminiImages.size == 1) {
                    if (hasOcrText) {
                        geminiService.parseBillImageWithOcr(geminiImages.first(), ocrTexts.first())
                    } else {
                        geminiService.parseBillImage(geminiImages.first())
                    }
                } else {
                    if (hasOcrText) {
                        geminiService.parseMultiPageBillWithOcr(geminiImages, ocrTexts)
                    } else {
                        geminiService.parseMultiPageBill(geminiImages)
                    }
                }

                // Validate subtotal accuracy
                val mismatch = validateSubtotal(bill)

                _uiState.update { it.copy(
                    scannedBill = bill,
                    isScanning = false,
                    scanProgress = ScanProgress.DONE,
                    subtotalMismatch = mismatch,
                    originalExtractedSubtotal = bill.subtotal
                ) }
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
                // Save as new — use bill's totalAmount as-is (respects discount/GST)
                val billToSave = bill.copy(items = validItems)
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
