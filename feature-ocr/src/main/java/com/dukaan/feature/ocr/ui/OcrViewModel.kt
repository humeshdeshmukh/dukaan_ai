package com.dukaan.feature.ocr.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.feature.billing.domain.repository.BillingRepository
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun processGalleryImage(context: Context, imageUri: Uri) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null, capturedImageUri = imageUri.toString()) }
            try {
                val recognizedText = withContext(Dispatchers.IO) {
                    val image = InputImage.fromFilePath(context, imageUri)
                    val recognizer = TextRecognition.getClient(
                        DevanagariTextRecognizerOptions.Builder().build()
                    )
                    val result = Tasks.await(recognizer.process(image))
                    result.text
                }

                if (recognizedText.isNotBlank()) {
                    val bill = geminiService.parseOcrText(recognizedText)
                    _uiState.update { it.copy(scannedBill = bill, isScanning = false) }
                } else {
                    _uiState.update { it.copy(error = "No text found in image. Try a clearer photo.", isScanning = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to process image.", isScanning = false) }
            }
        }
    }

    fun setCapturedImageUri(path: String) {
        _uiState.update { it.copy(capturedImageUri = path) }
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
