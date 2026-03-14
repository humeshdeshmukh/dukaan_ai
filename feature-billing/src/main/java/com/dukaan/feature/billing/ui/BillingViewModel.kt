package com.dukaan.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.feature.billing.domain.repository.BillingRepository
import com.dukaan.core.voice.SpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillingUiState(
    val items: List<BillItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val isParsing: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    val allBills: StateFlow<List<Bill>> = repository.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun processSpeech(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, error = null) }
            try {
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
            repository.saveBill(bill, "VOICE")
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun clearBill() {
        _uiState.value = BillingUiState()
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

    fun formatWhatsAppMessage(): String {
        val sb = StringBuilder()
        sb.append("*Dukaan AI - Bill*\n")
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        _uiState.value.items.forEach { item ->
            sb.append("${item.name}: ${item.quantity} ${item.unit} @ ₹${item.price} = ₹${item.total}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("*Total: ₹${_uiState.value.totalAmount}*\n")
        sb.append("\n_Sent via Dukaan AI_")
        return sb.toString()
    }
}
