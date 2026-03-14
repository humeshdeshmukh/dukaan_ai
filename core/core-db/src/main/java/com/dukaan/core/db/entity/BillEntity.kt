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
    val timestamp: Long = System.currentTimeMillis()
)
