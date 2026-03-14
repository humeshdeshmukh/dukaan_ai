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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class OcrUiState(
    val isScanning: Boolean = false,
    val scannedBill: Bill? = null,
    val error: String? = null,
    val isSaved: Boolean = false,
    val capturedImageUri: String? = null
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

    /** Process a captured camera image via Gemini vision */
    fun processCapturedImage(imagePath: String) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null, capturedImageUri = imagePath) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(imagePath)
                }
                if (bitmap != null) {
                    val bill = geminiService.parseBillImage(bitmap)
                    _uiState.update { it.copy(scannedBill = bill, isScanning = false) }
                } else {
                    _uiState.update { it.copy(error = "Failed to load captured image.", isScanning = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to process bill image.", isScanning = false) }
            }
        }
    }

    /** Process a gallery image via Gemini vision */
    fun processGalleryImage(context: Context, imageUri: Uri) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
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
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        Pair(bitmap, photoFile.absolutePath)
                    } else {
                        null
                    }
                }

                if (result != null) {
                    val (bitmap, savedPath) = result
                    _uiState.update { it.copy(capturedImageUri = savedPath) }
                    val bill = geminiService.parseBillImage(bitmap)
                    _uiState.update { it.copy(scannedBill = bill, isScanning = false) }
                } else {
                    _uiState.update { it.copy(error = "Failed to load image.", isScanning = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to process image.", isScanning = false) }
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
        val imagePath = _uiState.value.capturedImageUri
        viewModelScope.launch {
            billingRepository.saveBill(bill, "OCR", imagePath)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun resetScan() {
        _uiState.value = OcrUiState()
    }
}
