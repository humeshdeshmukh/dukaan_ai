package com.dukaan.core.network.model

import java.util.UUID

data class Bill(
    val id: Long = 0,
    val items: List<BillItem>,
    val totalAmount: Double,
    val sellerName: String = "",
    val billNumber: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class BillItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double
) {
    val total: Double get() = quantity * price
}

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val items: List<OrderItem>,
    val timestamp: Long = System.currentTimeMillis()
)

data class OrderItem(
    val name: String,
    val quantity: Double,
    val unit: String
)
