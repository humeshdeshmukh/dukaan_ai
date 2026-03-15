package com.dukaan.core.network.model

import java.util.UUID

data class Bill(
    val id: Long = 0,
    val items: List<BillItem>,
    val totalAmount: Double,
    val sellerName: String = "",
    val billNumber: String = "",
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val customerName: String = "",
    val customerId: Long? = null,
    val discountPercent: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val subtotal: Double = 0.0,
    val paymentMode: String = "CASH",
    val notes: String = "",
    val isDraft: Boolean = false
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
