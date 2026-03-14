package com.dukaan.feature.inventory.domain.model

data class Product(
    val id: Long,
    val name: String,
    val category: String?,
    val unit: String,
    val stockQuantity: Double,
    val costPrice: Double,
    val sellingPrice: Double,
    val lowStockThreshold: Double,
    val isLowStock: Boolean = stockQuantity <= lowStockThreshold
)
