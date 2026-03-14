package com.dukaan.feature.inventory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.feature.inventory.domain.model.Product
import com.dukaan.feature.inventory.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryUiState(
    val searchQuery: String = "",
    val productCount: Int = 0,
    val lowStockCount: Int = 0
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val filteredProducts: StateFlow<List<Product>> = combine(
        repository.getAllProducts(),
        _searchQuery
    ) { products, query ->
        if (query.isBlank()) products
        else products.filter { it.name.contains(query, ignoreCase = true) || it.category?.contains(query, ignoreCase = true) == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.getLowStockProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getProductCount().collect { count ->
                _uiState.update { it.copy(productCount = count) }
            }
        }
        viewModelScope.launch {
            repository.getLowStockCount().collect { count ->
                _uiState.update { it.copy(lowStockCount = count) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun addProduct(name: String, category: String?, unit: String, stockQuantity: Double, costPrice: Double, sellingPrice: Double, lowStockThreshold: Double) {
        viewModelScope.launch {
            repository.addProduct(Product(0, name, category, unit, stockQuantity, costPrice, sellingPrice, lowStockThreshold))
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
        }
    }
}
