package com.dukaan.core.db.entity

import androidx.room.*

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String? = null,
    val unit: String,
    val stockQuantity: Double = 0.0,
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
