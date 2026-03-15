package com.dukaan.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.entity.CustomerEntity
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
    val isRecording: Boolean = false,
    val recognizedText: String = "",
    val isParsing: Boolean = false,
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
    val selectedTab: Int = 0
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager,
    private val khataDao: KhataDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    val allBills: StateFlow<List<Bill>> = repository.getAllBills()
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
        // Collect final result and auto-process it
        viewModelScope.launch {
            speechManager.finalResult.collect { text ->
                if (text.isNotBlank()) {
                    speechManager.clearFinalResult()
                    processSpeech(text)
                }
            }
        }
        // Collect speech errors
        viewModelScope.launch {
            speechManager.error.collect { errorCode ->
                if (errorCode != null) {
                    val msg = when (errorCode) {
                        android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again."
                        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        android.speech.SpeechRecognizer.ERROR_NETWORK,
                        android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error. Check internet connection."
                        android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        else -> null
                    }
                    msg?.let { _uiState.update { state -> state.copy(error = msg) } }
                }
            }
        }
        loadCustomers()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            speechManager.stopListening()
        } else {
            speechManager.startListening()
        }
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
                paymentMode = state.paymentMode,
                notes = state.notes,
                isDraft = asDraft
            )
            repository.saveBill(bill, "VOICE")
            val msg = if (asDraft) "Draft saved!" else "Bill saved!"
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
            paymentMode = state.paymentMode,
            notes = state.notes
        )
    }

    fun clearBill() {
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

    fun formatWhatsAppMessage(): String {
        val state = _uiState.value
        val sb = StringBuilder()
        sb.append("*Dukaan AI - Bill*\n")
        if (state.selectedCustomerName.isNotEmpty()) {
            sb.append("Customer: ${state.selectedCustomerName}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        state.items.forEach { item ->
            sb.append("${item.name}: ${item.quantity} ${item.unit} @ ₹${item.price} = ₹${item.total}\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━\n")
        sb.append("Subtotal: ₹${String.format("%.2f", state.subtotal)}\n")
        if (state.discountPercent > 0) {
            sb.append("Discount (${state.discountPercent}%): -₹${String.format("%.2f", state.discountAmount)}\n")
        }
        if (state.taxPercent > 0) {
            sb.append("Tax/GST (${state.taxPercent}%): +₹${String.format("%.2f", state.taxAmount)}\n")
        }
        sb.append("*Total: ₹${String.format("%.2f", state.grandTotal)}*\n")
        sb.append("Payment: ${state.paymentMode}\n")
        if (state.notes.isNotEmpty()) {
            sb.append("Note: ${state.notes}\n")
        }
        sb.append("\n_Sent via Dukaan AI_")
        return sb.toString()
    }
}
