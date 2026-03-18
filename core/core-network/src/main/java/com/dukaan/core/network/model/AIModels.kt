package com.dukaan.core.network.model

import java.util.UUID

data class Bill(
    val id: Long = 0,
    val items: List<BillItem>,
    val totalAmount: Double,
    val sellerName: String = "",
    val sellerPhone: String = "",  // Wholesaler phone number
    val billNumber: String = "",
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val customerName: String = "",
    val customerId: Long? = null,
    val customerPhone: String = "",
    val discountPercent: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val subtotal: Double = 0.0,
    val paymentMode: String = "CASH",
    val notes: String = "",
    val isDraft: Boolean = false,
    val source: String = "VOICE"
)

data class BillItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val price: Double,                      // Line total (backward compatible)
    val unitPrice: Double = 0.0,            // Per-unit rate (new)
    val itemDiscountPercent: Double = 0.0,  // Item-level discount % (new)
    val itemDiscountAmount: Double = 0.0    // Item-level discount ₹ (new)
) {
    /** Gross total before item discount */
    val grossTotal: Double get() = if (unitPrice > 0) unitPrice * quantity else price

    /** Net total after item discount */
    val total: Double get() {
        val gross = grossTotal
        val discount = if (itemDiscountAmount > 0) itemDiscountAmount
                       else if (itemDiscountPercent > 0) gross * itemDiscountPercent / 100.0
                       else 0.0
        return (gross - discount).coerceAtLeast(0.0)
    }

    /** Computed per-unit rate (derives from price if unitPrice not set) */
    val effectiveUnitPrice: Double get() = when {
        unitPrice > 0 -> unitPrice
        quantity > 0 -> price / quantity
        else -> price
    }
}

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val items: List<OrderItem>,
    val timestamp: Long = System.currentTimeMillis(),
    val supplierName: String? = null,
    val supplierPhone: String? = null,
    val status: String = "PENDING",
    val notes: String? = null
)

data class OrderItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val notes: String = ""
)
