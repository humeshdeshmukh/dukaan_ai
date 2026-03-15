package com.dukaan.core.db.entity

import androidx.room.*

enum class BillSource {
    VOICE,
    OCR
}

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val totalAmount: Double,
    val source: BillSource = BillSource.VOICE,
    val sellerName: String = "",
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
    val isDraft: Boolean = false
)
