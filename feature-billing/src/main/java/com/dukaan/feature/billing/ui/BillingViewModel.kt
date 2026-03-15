package com.dukaan.feature.billing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.dao.ShopProfileDao
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
    val isContinuousListening: Boolean = false,
    val recognizedText: String = "",
    val isParsing: Boolean = false,
    val audioLevel: Float = 0f,
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
    val selectedTab: Int = 0,
    // Editing existing bill
    val editingBillId: Long? = null
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val repository: BillingRepository,
    private val geminiService: GeminiBillingService,
    private val speechManager: SpeechManager,
    private val khataDao: KhataDao,
    private val shopProfileDao: ShopProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    // Language code from user's settings — used for AI parsing
    private val languageCode: StateFlow<String> = shopProfileDao.getProfile()
        .map { it?.languageCode ?: "en" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "en")

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
        // Collect continuous mode state
        viewModelScope.launch {
            speechManager.isContinuousMode.collect { continuous ->
                _uiState.update { it.copy(isContinuousListening = continuous) }
            }
        }
        // Collect audio level for UI indicator
        viewModelScope.launch {
            speechManager.audioLevel.collect { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }
        // Collect final result and auto-process it
        viewModelScope.launch {
            speechManager.finalResult.collect { text ->
                if (text.isNotBlank()) {
                    processSpeech(text)
                }
            }
        }
        // Collect speech errors - suppress transient errors during continuous mode
        viewModelScope.launch {
            speechManager.error.collect { errorCode ->
                val msg = when (errorCode) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard. Tap mic and speak."
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic and speak."
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                    android.speech.SpeechRecognizer.ERROR_NETWORK,
                    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet not available."
                    android.speech.SpeechRecognizer.ERROR_AUDIO -> "Mic error. Try again."
                    android.speech.SpeechRecognizer.ERROR_CLIENT -> "Speech not available on this device."
                    android.speech.SpeechRecognizer.ERROR_SERVER -> "Server error. Retrying..."
                    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Wait and try again."
                    11 -> "Server disconnected. Retrying..." // ERROR_SERVER_DISCONNECTED
                    10 -> "Too many requests. Retrying..." // ERROR_TOO_MANY_REQUESTS
                    else -> "Voice error ($errorCode). Try again."
                }
                _uiState.update { it.copy(error = msg) }
            }
        }
        loadCustomers()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording || _uiState.value.isContinuousListening) {
            speechManager.stopListening()
        } else {
            // Start in continuous mode — auto-restarts after each result
            speechManager.startListening(
                continuous = true,
                speechCode = SupportedLanguages.getSpeechCode(languageCode.value)
            )
        }
    }

    private fun processSpeech(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isParsing = true, error = null) }
            try {
                val parsedItems = geminiService.parseBillingSpeech(text, languageCode.value)
                _uiState.update { state ->
                    val mergedItems = mergeItems(state.items, parsedItems)
                    state.copy(
                        items = mergedItems,
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

    /**
     * Smart merge: if a new item has the same name (case-insensitive) and unit as an existing item,
     * combine quantities and sum prices. Otherwise append as a new item.
     */
    private fun mergeItems(existing: List<BillItem>, incoming: List<BillItem>): List<BillItem> {
        val result = existing.toMutableList()
        for (newItem in incoming) {
            val matchIndex = result.indexOfFirst {
                it.name.equals(newItem.name, ignoreCase = true) && it.unit.equals(newItem.unit, ignoreCase = true)
            }
            if (matchIndex >= 0) {
                val old = result[matchIndex]
                result[matchIndex] = BillItem(
                    name = old.name,
                    quantity = old.quantity + newItem.quantity,
                    unit = old.unit,
                    price = old.price + newItem.price
                )
            } else {
                result.add(newItem)
            }
        }
        return result
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
            // If editing an existing bill, delete the old one first
            state.editingBillId?.let { oldId ->
                repository.deleteBill(oldId)
            }
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
                customerPhone = state.selectedCustomerPhone,
                paymentMode = state.paymentMode,
                notes = state.notes,
                isDraft = asDraft
            )
            repository.saveBill(bill, "VOICE")
            val msg = if (asDraft) "Draft saved!" else "Bill saved!"
            // Stop listening if still active
            speechManager.stopListening()
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
            customerPhone = state.selectedCustomerPhone,
            paymentMode = state.paymentMode,
            notes = state.notes
        )
    }

    fun clearBill() {
        speechManager.stopListening()
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

    fun loadBillForEditing(billId: Long) {
        viewModelScope.launch {
            val bill = repository.getBillById(billId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    items = bill.items,
                    selectedCustomerName = bill.customerName,
                    selectedCustomerId = bill.customerId,
                    selectedCustomerPhone = bill.customerPhone,
                    discountPercent = bill.discountPercent,
                    taxPercent = bill.taxPercent,
                    paymentMode = bill.paymentMode,
                    notes = bill.notes,
                    editingBillId = bill.id,
                    selectedTab = 0
                )
            }
            recalculate()
        }
    }

    fun formatWhatsAppMessage(): String {
        val state = _uiState.value
        val sb = StringBuilder()
        sb.append("*Dukaan AI - Bill*\n")
        if (state.selectedCustomerName.isNotEmpty()) {
            sb.append("Customer: ${state.selectedCustomerName}\n")
        }
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        state.items.forEach { item ->
            sb.append("${item.name}: ${item.quantity} ${item.unit} \u2014 \u20B9${String.format("%.2f", item.total)}\n")
        }
        sb.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        sb.append("Subtotal: \u20B9${String.format("%.2f", state.subtotal)}\n")
        if (state.discountPercent > 0) {
            sb.append("Discount (${state.discountPercent}%): -\u20B9${String.format("%.2f", state.discountAmount)}\n")
        }
        if (state.taxPercent > 0) {
            sb.append("Tax/GST (${state.taxPercent}%): +\u20B9${String.format("%.2f", state.taxAmount)}\n")
        }
        sb.append("*Total: \u20B9${String.format("%.2f", state.grandTotal)}*\n")
        sb.append("Payment: ${state.paymentMode}\n")
        if (state.notes.isNotEmpty()) {
            sb.append("Note: ${state.notes}\n")
        }
        sb.append("\n_Sent via Dukaan AI_")
        return sb.toString()
    }
}
