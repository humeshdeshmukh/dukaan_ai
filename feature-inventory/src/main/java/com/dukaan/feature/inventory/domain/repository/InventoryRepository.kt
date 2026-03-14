package com.dukaan.feature.inventory.domain.repository

import com.dukaan.feature.inventory.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getAllProducts(): Flow<List<Product>>
    fun searchProducts(query: String): Flow<List<Product>>
    fun getLowStockProducts(): Flow<List<Product>>
    suspend fun getProductById(id: Long): Product?
    suspend fun addProduct(product: Product): Long
    suspend fun updateProduct(product: Product)
    suspend fun deleteProduct(productId: Long)
    fun getLowStockCount(): Flow<Int>
    fun getProductCount(): Flow<Int>
}
