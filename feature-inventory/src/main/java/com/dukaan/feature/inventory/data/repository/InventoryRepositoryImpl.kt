package com.dukaan.feature.inventory.data.repository

import com.dukaan.core.db.dao.ProductDao
import com.dukaan.core.db.entity.ProductEntity
import com.dukaan.feature.inventory.domain.model.Product
import com.dukaan.feature.inventory.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val productDao: ProductDao
) : InventoryRepository {

    override fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllProducts().map { entities -> entities.map { it.toDomain() } }
    }

    override fun searchProducts(query: String): Flow<List<Product>> {
        return productDao.searchProducts(query).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getLowStockProducts(): Flow<List<Product>> {
        return productDao.getLowStockProducts().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getProductById(id: Long): Product? {
        return productDao.getProductById(id)?.toDomain()
    }

    override suspend fun addProduct(product: Product): Long {
        return productDao.insertProduct(product.toEntity())
    }

    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.toEntity())
    }

    override suspend fun deleteProduct(productId: Long) {
        val entity = productDao.getProductById(productId) ?: return
        productDao.deleteProduct(entity)
    }

    override fun getLowStockCount(): Flow<Int> = productDao.getLowStockCount()
    override fun getProductCount(): Flow<Int> = productDao.getProductCount()

    private fun ProductEntity.toDomain() = Product(
        id = id, name = name, category = category, unit = unit,
        stockQuantity = stockQuantity, costPrice = costPrice,
        sellingPrice = sellingPrice, lowStockThreshold = lowStockThreshold
    )

    private fun Product.toEntity() = ProductEntity(
        id = id, name = name, category = category, unit = unit,
        stockQuantity = stockQuantity, costPrice = costPrice,
        sellingPrice = sellingPrice, lowStockThreshold = lowStockThreshold
    )
}
