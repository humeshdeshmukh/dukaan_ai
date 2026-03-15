package com.dukaan.feature.khata.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.core.network.ai.GeminiKhataService
import com.dukaan.core.network.ai.TransactionParseResult
import com.dukaan.core.voice.SpeechManager
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption { NAME, BALANCE_HIGH, BALANCE_LOW, RECENT }

data class KhataUiState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val totalReceivable: Double = 0.0,
    val totalPayable: Double = 0.0,
    val customerCount: Int = 0
)

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val isLoading: Boolean = false
)

@HiltViewModel
class KhataViewModel @Inject constructor(
    private val repository: KhataRepository,
    private val speechManager: SpeechManager,
    private val khataAiService: GeminiKhataService
) : ViewModel() {

    private val _uiState = MutableStateFlow(KhataUiState())
    val uiState: StateFlow<KhataUiState> = _uiState.asStateFlow()

    val speechText: StateFlow<String> = speechManager.speechText

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.NAME)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // AI states
    private val _customerInsight = MutableStateFlow<String?>(null)
    val customerInsight: StateFlow<String?> = _customerInsight.asStateFlow()

    private val _isInsightLoading = MutableStateFlow(false)
    val isInsightLoading: StateFlow<Boolean> = _isInsightLoading.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiTyping = MutableStateFlow(false)
    val isAiTyping: StateFlow<Boolean> = _isAiTyping.asStateFlow()

    private val _reminderMessage = MutableStateFlow<String?>(null)
    val reminderMessage: StateFlow<String?> = _reminderMessage.asStateFlow()

    private val _voiceParseResult = MutableStateFlow<TransactionParseResult?>(null)
    val voiceParseResult: StateFlow<TransactionParseResult?> = _voiceParseResult.asStateFlow()

    private val _isVoiceParsing = MutableStateFlow(false)
    val isVoiceParsing: StateFlow<Boolean> = _isVoiceParsing.asStateFlow()

    // Overall khata AI chat states
    private val _overallChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val overallChatMessages: StateFlow<List<ChatMessage>> = _overallChatMessages.asStateFlow()

    private val _isOverallAiTyping = MutableStateFlow(false)
    val isOverallAiTyping: StateFlow<Boolean> = _isOverallAiTyping.asStateFlow()

    private val _overallInsight = MutableStateFlow<String?>(null)
    val overallInsight: StateFlow<String?> = _overallInsight.asStateFlow()

    private val _isOverallInsightLoading = MutableStateFlow(false)
    val isOverallInsightLoading: StateFlow<Boolean> = _isOverallInsightLoading.asStateFlow()

    val filteredCustomers: StateFlow<List<Customer>> = combine(
        repository.getAllCustomers(),
        _searchQuery,
        _sortOption
    ) { customers, query, sort ->
        val filtered = if (query.isBlank()) {
            customers
        } else {
            customers.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
        }
        when (sort) {
            SortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            SortOption.BALANCE_HIGH -> filtered.sortedByDescending { Math.abs(it.balance) }
            SortOption.BALANCE_LOW -> filtered.sortedBy { Math.abs(it.balance) }
            SortOption.RECENT -> filtered.sortedByDescending { it.lastActivityAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getTotalReceivable().collect { receivable ->
                _uiState.update { it.copy(totalReceivable = receivable) }
            }
        }
        viewModelScope.launch {
            repository.getTotalPayable().collect { payable ->
                _uiState.update { it.copy(totalPayable = payable) }
            }
        }
        viewModelScope.launch {
            repository.getCustomerCount().collect { count ->
                _uiState.update { it.copy(customerCount = count) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun addCustomer(name: String, phone: String, khataType: String = "SMALL") {
        viewModelScope.launch {
            repository.addCustomer(name, phone, khataType)
        }
    }

    fun updateCustomer(customerId: Long, name: String, phone: String) {
        viewModelScope.launch {
            repository.updateCustomer(customerId, name, phone)
        }
    }

    fun deleteCustomer(customerId: Long) {
        viewModelScope.launch {
            repository.deleteCustomer(customerId)
        }
    }

    fun getTransactions(customerId: Long): Flow<List<Transaction>> {
        return repository.getTransactionsByCustomer(customerId)
    }

    fun getTransactionsByDateRange(customerId: Long, startDate: Long, endDate: Long): Flow<List<Transaction>> {
        return repository.getTransactionsByDateRange(customerId, startDate, endDate)
    }

    suspend fun getCustomerById(customerId: Long): Customer? {
        return repository.getCustomerById(customerId)
    }

    fun getCustomerFlow(customerId: Long): Flow<Customer?> {
        return repository.getCustomerFlow(customerId)
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
        }
    }

    fun startVoiceInput(languageCode: String = "hi") {
        speechManager.startListening()
    }

    fun stopVoiceInput() {
        speechManager.stopListening()
    }

    // --- AI Features ---

    fun loadCustomerInsight(customerName: String, balance: Double, transactions: List<Transaction>, languageCode: String = "en") {
        viewModelScope.launch {
            _isInsightLoading.value = true
            _customerInsight.value = null
            val summary = transactions.take(20).joinToString("\n") { txn ->
                val type = if (txn.type.name == "JAMA") "Received" else "Credit"
                val date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(txn.date))
                "$date: $type ₹${txn.amount}${if (!txn.notes.isNullOrBlank()) " (${txn.notes})" else ""}"
            }
            val insight = khataAiService.getCustomerInsight(customerName, balance, summary, languageCode)
            _customerInsight.value = insight
            _isInsightLoading.value = false
        }
    }

    fun generateReminder(customerName: String, amount: Double, shopName: String, languageCode: String = "en") {
        viewModelScope.launch {
            _reminderMessage.value = null
            val message = khataAiService.generatePaymentReminder(customerName, amount, shopName, languageCode)
            _reminderMessage.value = message
        }
    }

    fun clearReminder() {
        _reminderMessage.value = null
    }

    fun sendChatMessage(customerName: String, balance: Double, transactions: List<Transaction>, userMessage: String, languageCode: String = "en") {
        viewModelScope.launch {
            _chatMessages.update { it + ChatMessage(isUser = true, text = userMessage) }
            _isAiTyping.value = true

            val context = buildString {
                appendLine("Customer: $customerName")
                appendLine("Balance: ₹$balance (${if (balance > 0) "they owe you" else "you owe them"})")
                appendLine("Recent transactions:")
                transactions.take(15).forEach { txn ->
                    val type = if (txn.type.name == "JAMA") "Received" else "Credit"
                    val date = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(txn.date))
                    appendLine("  $date: $type ₹${txn.amount}")
                }
            }

            val response = khataAiService.chatAboutKhata(context, userMessage, languageCode)
            _isAiTyping.value = false
            _chatMessages.update { it + ChatMessage(isUser = false, text = response) }
        }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
        _customerInsight.value = null
    }

    fun parseVoiceTransaction(speechText: String) {
        viewModelScope.launch {
            _isVoiceParsing.value = true
            _voiceParseResult.value = null
            val result = khataAiService.parseTransactionSpeech(speechText)
            _voiceParseResult.value = result
            _isVoiceParsing.value = false
        }
    }

    fun clearVoiceParseResult() {
        _voiceParseResult.value = null
    }

    // --- Overall Khata AI ---

    private fun buildOverallKhataContext(customers: List<Customer>): String {
        return buildString {
            appendLine("Total Receivable: ₹${_uiState.value.totalReceivable}")
            appendLine("Total Payable: ₹${Math.abs(_uiState.value.totalPayable)}")
            appendLine("Net Position: ₹${_uiState.value.totalReceivable + _uiState.value.totalPayable}")
            appendLine("Total Customers: ${_uiState.value.customerCount}")
            appendLine()
            appendLine("Customer Details:")
            customers.take(20).forEach { c ->
                val status = if (c.balance > 0) "owes you ₹${c.balance}" else if (c.balance < 0) "you owe ₹${Math.abs(c.balance)}" else "settled"
                val lastActive = if (c.lastActivityAt > 0) {
                    val days = (System.currentTimeMillis() - c.lastActivityAt) / (24 * 60 * 60 * 1000L)
                    "${days} days ago"
                } else "unknown"
                appendLine("  ${c.name} (${c.phone}): $status, last active: $lastActive")
            }
        }
    }

    fun loadOverallInsight(languageCode: String = "en") {
        viewModelScope.launch {
            _isOverallInsightLoading.value = true
            _overallInsight.value = null
            val customers = filteredCustomers.value
            val context = buildOverallKhataContext(customers)
            val insight = khataAiService.getOverallKhataInsight(context, languageCode)
            _overallInsight.value = insight
            _isOverallInsightLoading.value = false
        }
    }

    fun sendOverallChatMessage(userMessage: String, languageCode: String = "en") {
        viewModelScope.launch {
            _overallChatMessages.update { it + ChatMessage(isUser = true, text = userMessage) }
            _isOverallAiTyping.value = true
            val customers = filteredCustomers.value
            val context = buildOverallKhataContext(customers)
            val response = khataAiService.chatAboutOverallKhata(context, userMessage, languageCode)
            _isOverallAiTyping.value = false
            _overallChatMessages.update { it + ChatMessage(isUser = false, text = response) }
        }
    }

    fun clearOverallChat() {
        _overallChatMessages.value = emptyList()
        _overallInsight.value = null
    }
}
