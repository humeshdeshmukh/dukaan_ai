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
    // Create/Edit order fields
    val items: List<OrderItem> = emptyList(),
    val supplierName: String = "",
    val notes: String = "",
    val isRecording: Boolean = false,
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
    val showEditItemDialog: Boolean = false
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

    init {
        viewModelScope.launch {
            speechManager.speechText.collect { text ->
                _uiState.update { it.copy(recognizedText = text) }
            }
        }
    }

    // --- Tab ---
    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // --- Voice recording ---
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
                    unit = old.unit
                )
            } else {
                result.add(newItem)
            }
        }
        return result
    }

    // --- Supplier & Notes ---
    fun setSupplierName(name: String) {
        _uiState.update { it.copy(supplierName = name) }
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

    fun addItemManually(name: String, quantity: Double, unit: String) {
        val item = OrderItem(name = name, quantity = quantity, unit = unit)
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

            state.editingOrderId?.let { existingId ->
                val order = Order(
                    id = existingId.toString(),
                    items = state.items,
                    timestamp = System.currentTimeMillis(),
                    supplierName = state.supplierName.ifBlank { null },
                    status = "PENDING",
                    notes = state.notes.ifBlank { null }
                )
                repository.updateOrder(order)
                _uiState.value = OrderUiState(
                    isSaved = true,
                    snackbarMessage = "Order updated!",
                    selectedTab = 1
                )
            } ?: run {
                val order = Order(
                    items = state.items,
                    timestamp = System.currentTimeMillis(),
                    supplierName = state.supplierName.ifBlank { null },
                    notes = state.notes.ifBlank { null }
                )
                repository.saveOrder(order)
                _uiState.value = OrderUiState(
                    isSaved = true,
                    snackbarMessage = "Order saved!",
                    selectedTab = 1
                )
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
        _uiState.value = OrderUiState()
    }

    fun clearOrder() {
        if (_uiState.value.items.isNotEmpty()) {
            showClearConfirmation()
        } else {
            _uiState.value = OrderUiState()
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
            // Refresh detail if viewing this order
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

    // --- WhatsApp message ---
    fun getWhatsAppMessage(): String {
        val state = _uiState.value
        return buildOrderMessage(state.items, state.supplierName, state.notes)
    }

    fun getWhatsAppMessageForOrder(order: Order): String {
        return buildOrderMessage(order.items, order.supplierName ?: "", order.notes ?: "")
    }

    private fun buildOrderMessage(items: List<OrderItem>, supplier: String, notes: String): String {
        val builder = StringBuilder("*Wholesale Order - Dukaan AI*\n")
        if (supplier.isNotBlank()) {
            builder.append("Supplier: $supplier\n")
        }
        builder.append("━━━━━━━━━━━━━━━━━━\n")
        items.forEachIndexed { index, item ->
            builder.append("${index + 1}. ${item.name} - ${item.quantity} ${item.unit}\n")
        }
        builder.append("━━━━━━━━━━━━━━━━━━\n")
        builder.append("Total Items: ${items.size}\n")
        if (notes.isNotBlank()) {
            builder.append("Notes: $notes\n")
        }
        builder.append("\n_Sent via Dukaan AI_")
        return builder.toString()
    }
}
