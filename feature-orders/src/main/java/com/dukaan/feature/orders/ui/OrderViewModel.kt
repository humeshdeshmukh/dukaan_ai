package com.dukaan.feature.orders.ui

import android.speech.SpeechRecognizer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class OrderUiState(
    // Create/Edit order fields
    val items: List<OrderItem> = emptyList(),
    val supplierName: String = "",
    val supplierPhone: String = "",
    val notes: String = "",
    val isRecording: Boolean = false,
    val isContinuousListening: Boolean = false,
    val audioLevel: Float = 0f,
    val recognizedText: String = "",
    val isProcessing: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val snackbarMessage: String? = null,

    // Tab management (0 = Create, 1 = History)
    val selectedTab: Int = 0,

    // Editing mode
    val editingOrderId: Long? = null,
    val editingItemIndex: Int = -1,

    // History filters
    val statusFilter: String = "ALL",
    val searchQuery: String = "",

    // Detail screen
    val selectedOrder: Order? = null,
    val isLoadingDetail: Boolean = false,

    // Dialogs
    val showClearConfirm: Boolean = false,
    val showDeleteConfirm: Long? = null,
    val showAddItemDialog: Boolean = false,
    val showEditItemDialog: Boolean = false,
    val showSupplierPicker: Boolean = false
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

    private val shopProfile = shopProfileDao.getProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _statusFilter = MutableStateFlow("ALL")
    private val _searchQuery = MutableStateFlow("")

    val allOrders: StateFlow<List<Order>> = combine(
        repository.getAllOrders(),
        _statusFilter,
        _searchQuery
    ) { orders, status, query ->
        orders
            .filter { order ->
                status == "ALL" || order.status == status
            }
            .filter { order ->
                if (query.isBlank()) true
                else {
                    order.supplierName?.contains(query, ignoreCase = true) == true ||
                        order.items.any { it.name.contains(query, ignoreCase = true) }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val existingSuppliers: StateFlow<List<Pair<String, String?>>> =
        repository.getDistinctSuppliers()
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
                    processOrderSpeech(text)
                }
            }
        }
        // Collect speech errors
        viewModelScope.launch {
            speechManager.error.collect { errorCode ->
                val msg = when (errorCode) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard. Tap mic and speak."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap mic and speak."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet not available."
                    SpeechRecognizer.ERROR_AUDIO -> "Mic error. Try again."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech not available on this device."
                    SpeechRecognizer.ERROR_SERVER -> "Server error. Retrying..."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Wait and try again."
                    11 -> "Server disconnected. Retrying..."
                    10 -> "Too many requests. Retrying..."
                    else -> "Voice error ($errorCode). Try again."
                }
                _uiState.update { it.copy(error = msg) }
            }
        }
    }

    // --- Tab ---
    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // --- Voice recording (continuous mode like VoiceBilling) ---
    fun toggleRecording() {
        if (_uiState.value.isRecording || _uiState.value.isContinuousListening) {
            speechManager.stopListening()
        } else {
            speechManager.startListening(
                continuous = true,
                speechCode = SupportedLanguages.getSpeechCode(languageCode.value)
            )
        }
    }

    private fun processOrderSpeech(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val newItems = geminiService.parseOrderSpeech(text, languageCode.value)
                _uiState.update {
                    it.copy(
                        items = mergeItems(it.items, newItems),
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

    // --- Smart merge ---
    private fun mergeItems(existing: List<OrderItem>, incoming: List<OrderItem>): List<OrderItem> {
        val result = existing.toMutableList()
        for (newItem in incoming) {
            val matchIndex = result.indexOfFirst {
                it.name.equals(newItem.name, ignoreCase = true) &&
                    it.unit.equals(newItem.unit, ignoreCase = true)
            }
            if (matchIndex >= 0) {
                val old = result[matchIndex]
                result[matchIndex] = OrderItem(
                    name = old.name,
                    quantity = old.quantity + newItem.quantity,
                    unit = old.unit,
                    notes = if (old.notes.isNotBlank() && newItem.notes.isNotBlank()) "${old.notes} | ${newItem.notes}" else old.notes.ifBlank { newItem.notes }
                )
            } else {
                result.add(newItem)
            }
        }
        return result
    }

    // --- Supplier ---
    fun setSupplierName(name: String) {
        _uiState.update { it.copy(supplierName = name) }
    }

    fun setSupplierPhone(phone: String) {
        _uiState.update { it.copy(supplierPhone = phone) }
    }

    fun showSupplierPicker() {
        _uiState.update { it.copy(showSupplierPicker = true) }
    }

    fun dismissSupplierPicker() {
        _uiState.update { it.copy(showSupplierPicker = false) }
    }

    fun selectSupplier(name: String, phone: String?) {
        _uiState.update {
            it.copy(
                supplierName = name,
                supplierPhone = phone ?: "",
                showSupplierPicker = false
            )
        }
    }

    fun clearSupplier() {
        _uiState.update {
            it.copy(
                supplierName = "",
                supplierPhone = "",
                showSupplierPicker = false
            )
        }
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    // --- Manual item addition ---
    fun showAddItemDialog() {
        _uiState.update { it.copy(showAddItemDialog = true) }
    }

    fun dismissAddItemDialog() {
        _uiState.update { it.copy(showAddItemDialog = false) }
    }

    fun addItemManually(name: String, quantity: Double, unit: String, notes: String = "") {
        val item = OrderItem(name = name, quantity = quantity, unit = unit, notes = notes)
        _uiState.update { state ->
            state.copy(
                items = mergeItems(state.items, listOf(item)),
                showAddItemDialog = false
            )
        }
    }

    // --- Item editing ---
    fun setEditingItem(index: Int) {
        _uiState.update { it.copy(editingItemIndex = index, showEditItemDialog = true) }
    }

    fun dismissEditItemDialog() {
        _uiState.update { it.copy(editingItemIndex = -1, showEditItemDialog = false) }
    }

    fun updateItem(index: Int, updated: OrderItem) {
        _uiState.update { state ->
            val newItems = state.items.toMutableList()
            if (index in newItems.indices) {
                newItems[index] = updated
            }
            state.copy(items = newItems, editingItemIndex = -1, showEditItemDialog = false)
        }
    }

    fun removeItem(item: OrderItem) {
        _uiState.update { it.copy(items = it.items - item) }
    }

    // --- Save / Edit order ---
    fun saveOrder() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.items.isEmpty()) return@launch

            try {
                state.editingOrderId?.let { existingId ->
                    val order = Order(
                        id = existingId.toString(),
                        items = state.items,
                        timestamp = System.currentTimeMillis(),
                        supplierName = state.supplierName.ifBlank { null },
                        supplierPhone = state.supplierPhone.ifBlank { null },
                        status = "PENDING",
                        notes = state.notes.ifBlank { null }
                    )
                    repository.updateOrder(order)
                    _uiState.update {
                        OrderUiState(
                            isSaved = true,
                            snackbarMessage = "Order updated!",
                            selectedTab = 1
                        )
                    }
                } ?: run {
                    val order = Order(
                        items = state.items,
                        timestamp = System.currentTimeMillis(),
                        supplierName = state.supplierName.ifBlank { null },
                        supplierPhone = state.supplierPhone.ifBlank { null },
                        notes = state.notes.ifBlank { null }
                    )
                    repository.saveOrder(order)
                    _uiState.update {
                        OrderUiState(
                            isSaved = true,
                            snackbarMessage = "Order saved!",
                            selectedTab = 1
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to save order. Try again.")
                }
            }
        }
    }

    // --- Clear with confirmation ---
    fun showClearConfirmation() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }

    fun dismissClearConfirmation() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }

    fun confirmClear() {
        speechManager.stopListening()
        _uiState.update { OrderUiState() }
    }

    fun clearOrder() {
        if (_uiState.value.items.isNotEmpty()) {
            showClearConfirmation()
        } else {
            _uiState.update { OrderUiState() }
        }
    }

    // --- Delete with confirmation ---
    fun showDeleteConfirmation(orderId: Long) {
        _uiState.update { it.copy(showDeleteConfirm = orderId) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun confirmDelete(orderId: Long) {
        viewModelScope.launch {
            repository.deleteOrder(orderId)
            _uiState.update { it.copy(showDeleteConfirm = null, snackbarMessage = "Order deleted") }
        }
    }

    // --- History filters ---
    fun setStatusFilter(status: String) {
        _statusFilter.value = status
        _uiState.update { it.copy(statusFilter = status) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    // --- Order Detail ---
    fun loadOrderDetail(orderId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            val order = repository.getOrderById(orderId)
            _uiState.update { it.copy(selectedOrder = order, isLoadingDetail = false) }
        }
    }

    // --- Status update ---
    fun updateOrderStatus(orderId: Long, status: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, status)
            if (_uiState.value.selectedOrder?.id == orderId.toString()) {
                val updated = repository.getOrderById(orderId)
                _uiState.update { it.copy(selectedOrder = updated) }
            }
        }
    }

    // --- Duplicate ---
    fun duplicateOrder(orderId: Long) {
        viewModelScope.launch {
            repository.duplicateOrder(orderId)
            _uiState.update { it.copy(snackbarMessage = "Order duplicated!") }
        }
    }

    // --- Load for editing ---
    fun loadOrderForEditing(orderId: Long) {
        viewModelScope.launch {
            val order = repository.getOrderById(orderId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    items = order.items,
                    supplierName = order.supplierName ?: "",
                    supplierPhone = order.supplierPhone ?: "",
                    notes = order.notes ?: "",
                    editingOrderId = orderId,
                    selectedTab = 0
                )
            }
        }
    }

    // --- Snackbar ---
    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // --- Build current state as Order (for PDF generation) ---
    fun getCurrentOrderAsOrder(): Order {
        val state = _uiState.value
        return Order(
            id = (state.editingOrderId ?: 0).toString(),
            items = state.items,
            timestamp = System.currentTimeMillis(),
            supplierName = state.supplierName.ifBlank { null },
            supplierPhone = state.supplierPhone.ifBlank { null },
            status = "PENDING",
            notes = state.notes.ifBlank { null }
        )
    }

    // --- WhatsApp message ---
    fun getWhatsAppMessage(): String {
        val state = _uiState.value
        return buildOrderMessage(state.items, state.supplierName, state.notes, System.currentTimeMillis(), shopProfile.value)
    }

    fun getWhatsAppMessageForOrder(order: Order): String {
        return buildOrderMessage(order.items, order.supplierName ?: "", order.notes ?: "", order.timestamp, shopProfile.value)
    }

    private fun buildOrderMessage(
        items: List<OrderItem>, 
        supplier: String, 
        notes: String, 
        timestamp: Long,
        shop: com.dukaan.core.db.entity.ShopProfileEntity? = null
    ): String {
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
        val builder = StringBuilder("Purchase Order\n")
        builder.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        
        if (shop != null) {
            builder.append("From: ${shop.shopName.ifBlank { "My Shop" }}\n")
            if (shop.ownerName.isNotBlank()) builder.append("Owner: ${shop.ownerName}\n")
            if (shop.phone.isNotBlank()) builder.append("Ph: ${shop.phone}\n")
            if (shop.address.isNotBlank()) builder.append("Address: ${shop.address}\n")
            builder.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        }

        if (supplier.isNotBlank()) {
            builder.append("Supplier: $supplier\n")
        }
        builder.append("Date: $dateStr\n")
        builder.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        items.forEachIndexed { index, item ->
            val qty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else "%.2f".format(item.quantity)
            builder.append("${index + 1}. ${item.name} \u2013 $qty ${item.unit}")
            if (item.notes.isNotBlank()) {
                builder.append(" (${item.notes})")
            }
            builder.append("\n")
        }
        builder.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n")
        builder.append("Total Items: ${items.size}\n")
        if (notes.isNotBlank()) {
            builder.append("Notes: $notes\n")
        }
        builder.append("\nSent via Dukaan AI")
        return builder.toString()
    }
}
