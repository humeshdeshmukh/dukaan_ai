package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE stockQuantity <= lowStockThreshold")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET stockQuantity = stockQuantity - :quantity, updatedAt = :timestamp WHERE id = :productId")
    suspend fun deductStock(productId: Long, quantity: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity, updatedAt = :timestamp WHERE id = :productId")
    suspend fun addStock(productId: Long, quantity: Double, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM products")
    fun getProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE stockQuantity <= lowStockThreshold")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT * FROM products WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByNameExact(name: String): ProductEntity?
}
