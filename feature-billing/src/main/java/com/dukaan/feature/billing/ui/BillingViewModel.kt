package com.dukaan.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.feature.billing.domain.repository.BillingRepository
import com.dukaan.core.voice.SpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillingUiState(
    val items: List<BillItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val isParsing: Boolean = false
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            speechManager.speechText.collect { text ->
                _uiState.update { it.copy(recognizedText = text) }
            }
        }
    }

    fun toggleRecording() {
        val currentlyRecording = _uiState.value.isRecording
        if (currentlyRecording) {
            speechManager.stopListening()
            processSpeech(_uiState.value.recognizedText)
        } else {
            speechManager.startListening()
        }
        _uiState.update { it.copy(isRecording = !currentlyRecording) }
    }

    fun onSpeechResult(text: String) {
        _uiState.update { it.copy(recognizedText = text) }
    }

    private fun processSpeech(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true) }
            val parsedItems = geminiService.parseBillingSpeech(text)
            _uiState.update { state ->
                val newItems = state.items + parsedItems
                state.copy(
                    items = newItems,
                    totalAmount = newItems.sumOf { it.total },
                    isParsing = false,
                    recognizedText = ""
                )
            }
        }
    }

    fun removeItem(item: BillItem) {
        _uiState.update { state ->
            val newItems = state.items.filter { it != item }
            state.copy(
                items = newItems,
                totalAmount = newItems.sumOf { it.total }
            )
        }
    }

    fun saveBill() {
        viewModelScope.launch {
            val bill = Bill(
                items = _uiState.value.items,
                totalAmount = _uiState.value.totalAmount
            )
            repository.saveBill(bill)
            // Clear current state after saving
            _uiState.value = BillingUiState()
        }
    }

    fun formatWhatsAppMessage(): String {
        val sb = StringBuilder()
        sb.append("*Dukaan AI - New Bill*\n\n")
        _uiState.value.items.forEach { item ->
            sb.append("${item.name}: ${item.quantity} ${item.unit} @ ₹${item.price} = ₹${item.total}\n")
        }
        sb.append("\n*Total: ₹${_uiState.value.totalAmount}*")
        return sb.toString()
    }
}
