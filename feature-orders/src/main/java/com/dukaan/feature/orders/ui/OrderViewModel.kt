package com.dukaan.feature.orders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.core.db.dao.ShopProfileDao
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.voice.SpeechManager
import com.dukaan.core.network.model.Order
import com.dukaan.core.network.model.OrderItem
import com.dukaan.feature.orders.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderUiState(
    val items: List<OrderItem> = emptyList(),
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager,
    private val repository: OrderRepository,
    private val shopProfileDao: ShopProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val languageCode: StateFlow<String> = shopProfileDao.getProfile()
        .map { it?.languageCode ?: "en" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

    val allOrders: StateFlow<List<Order>> = repository.getAllOrders()
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
            processOrderSpeech(_uiState.value.recognizedText)
        } else {
            speechManager.startListening(
                speechCode = SupportedLanguages.getSpeechCode(languageCode.value)
            )
        }
        _uiState.update { it.copy(isRecording = !currentlyRecording) }
    }

    private fun processOrderSpeech(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val newItems = geminiService.parseOrderSpeech(text, languageCode.value)
                _uiState.update {
                    it.copy(
                        items = it.items + newItems,
                        isProcessing = false,
                        recognizedText = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Failed to parse order. Please try again."
                    )
                }
            }
        }
    }

    fun removeItem(item: OrderItem) {
        _uiState.update { it.copy(items = it.items - item) }
    }

    fun saveOrder() {
        viewModelScope.launch {
            val order = Order(
                items = _uiState.value.items,
                timestamp = System.currentTimeMillis()
            )
            repository.saveOrder(order)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun clearOrder() {
        _uiState.value = OrderUiState()
    }

    fun deleteOrder(orderId: Long) {
        viewModelScope.launch {
            repository.deleteOrder(orderId)
        }
    }

    fun getWhatsAppMessage(): String {
        val builder = StringBuilder("*Wholesale Order - Dukaan AI*\n")
        builder.append("━━━━━━━━━━━━━━━━━━\n")
        _uiState.value.items.forEachIndexed { index, item ->
            builder.append("${index + 1}. ${item.name} - ${item.quantity} ${item.unit}\n")
        }
        builder.append("━━━━━━━━━━━━━━━━━━\n")
        builder.append("Total Items: ${_uiState.value.items.size}\n")
        builder.append("\n_Sent via Dukaan AI_")
        return builder.toString()
    }
}
