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
    val price: Double,
    val priceUnit: String = ""
) {
    val total: Double get() {
        if (priceUnit.isBlank() || priceUnit.equals(unit, ignoreCase = true)) {
            return quantity * price
        }
        val factor = unitConversionFactor(unit, priceUnit)
        return quantity * factor * price
    }

    companion object {
        private fun unitConversionFactor(fromUnit: String, toUnit: String): Double {
            val from = fromUnit.lowercase().trim()
            val to = toUnit.lowercase().trim()
            if (from == to) return 1.0

            // Weight: g -> kg
            if (from in listOf("g", "gm", "gram", "grams") && to in listOf("kg", "kilo", "kilogram")) return 0.001
            if (from in listOf("kg", "kilo", "kilogram") && to in listOf("g", "gm", "gram", "grams")) return 1000.0

            // Volume: ml -> L
            if (from in listOf("ml", "millilitre", "milliliter") && to in listOf("l", "ltr", "litre", "liter")) return 0.001
            if (from in listOf("l", "ltr", "litre", "liter") && to in listOf("ml", "millilitre", "milliliter")) return 1000.0

            // Dozen <-> pieces
            if (from in listOf("pc", "pcs", "piece", "pieces") && to in listOf("dz", "dozen")) return 1.0 / 12.0
            if (from in listOf("dz", "dozen") && to in listOf("pc", "pcs", "piece", "pieces")) return 12.0

            return 1.0
        }
    }
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
