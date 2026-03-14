package com.dukaan.feature.orders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.ai.GeminiBillingService
import com.dukaan.core.voice.SpeechManager
import com.dukaan.core.network.model.OrderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderUiState(
    val items: List<OrderItem> = emptyList(),
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val isProcessing: Boolean = false
)

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

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
            speechManager.startListening()
        }
        _uiState.update { it.copy(isRecording = !currentlyRecording) }
    }

    private fun processOrderSpeech(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val newItems = geminiService.parseOrderSpeech(text)
                _uiState.update { it.copy(
                    items = it.items + newItems,
                    isProcessing = false,
                    recognizedText = ""
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun removeItem(item: OrderItem) {
        _uiState.update { it.copy(items = it.items - item) }
    }

    fun getWhatsAppMessage(): String {
        val shopName = "My Kirana Shop"
        val builder = StringBuilder("Wholesale Order From: $shopName\n")
        builder.append("----------------------------------\n")
        _uiState.value.items.forEachIndexed { index, item ->
            builder.append("${index + 1}. ${item.name} - ${item.quantity} ${item.unit}\n")
        }
        builder.append("----------------------------------\n")
        builder.append("Total Items: ${_uiState.value.items.size}")
        return builder.toString()
    }
}
