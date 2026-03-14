package com.dukaan.feature.khata.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.voice.SpeechManager
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KhataUiState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val totalReceivable: Double = 0.0,
    val totalPayable: Double = 0.0,
    val customerCount: Int = 0
)

@HiltViewModel
class KhataViewModel @Inject constructor(
    private val repository: KhataRepository,
    private val speechManager: SpeechManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(KhataUiState())
    val uiState: StateFlow<KhataUiState> = _uiState.asStateFlow()

    val speechText: StateFlow<String> = speechManager.speechText

    private val _searchQuery = MutableStateFlow("")

    val filteredCustomers: StateFlow<List<Customer>> = combine(
        repository.getAllCustomers(),
        _searchQuery
    ) { customers, query ->
        if (query.isBlank()) {
            customers
        } else {
            customers.filter { it.name.contains(query, ignoreCase = true) || it.phone.contains(query) }
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

    fun addCustomer(name: String, phone: String) {
        viewModelScope.launch {
            repository.addCustomer(name, phone)
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

    fun startVoiceInput() {
        speechManager.startListening()
    }

    fun stopVoiceInput() {
        speechManager.stopListening()
    }
}
