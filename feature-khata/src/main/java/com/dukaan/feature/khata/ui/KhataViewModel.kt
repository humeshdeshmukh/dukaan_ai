package com.dukaan.feature.khata.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val searchQuery: String = ""
)

@HiltViewModel
class KhataViewModel @Inject constructor(
    private val repository: KhataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KhataUiState())
    val uiState: StateFlow<KhataUiState> = _uiState.asStateFlow()

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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun addCustomer(name: String, phone: String) {
        viewModelScope.launch {
            repository.addCustomer(name, phone)
        }
    }

    fun getTransactions(customerId: Long): Flow<List<Transaction>> {
        return repository.getTransactionsByCustomer(customerId)
    }

    suspend fun getCustomerById(customerId: Long): Customer? {
        return repository.getCustomerById(customerId)
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.addTransaction(transaction)
        }
    }
}
