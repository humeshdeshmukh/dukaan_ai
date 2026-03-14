package com.dukaan.feature.ocr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrUiState(
    val isScanning: Boolean = false,
    val scannedBill: Bill? = null,
    val error: String? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val geminiService: com.dukaan.core.network.ai.GeminiBillingService
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    fun onTextRecognized(rawText: String) {
        if (_uiState.value.isScanning || _uiState.value.scannedBill != null) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val bill = geminiService.parseOcrText(rawText)
                _uiState.update { it.copy(scannedBill = bill, isScanning = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Parsing failed", isScanning = false) }
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
}
