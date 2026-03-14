package com.dukaan.core.db.entity

import androidx.room.*

enum class OrderStatus {
    PENDING,
    SENT,
    COMPLETED
}

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierName: String? = null,
    val status: OrderStatus = OrderStatus.PENDING,
    val itemCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
