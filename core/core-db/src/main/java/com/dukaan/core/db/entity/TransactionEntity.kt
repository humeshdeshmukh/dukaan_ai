package com.dukaan.core.db.entity

import androidx.room.*

enum class TransactionType {
    JAMA,  // Credit (money received)
    BAKI,  // Debit (money owed)
    PAYMENT // General payment entry
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Double,
    val type: TransactionType,
    val date: Long = System.currentTimeMillis(),
    val notes: String? = null,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false
)
