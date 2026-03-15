package com.dukaan.feature.khata.domain.model

data class Customer(
    val id: Long,
    val name: String,
    val phone: String,
    val balance: Double,
    val lastActivityAt: Long,
    val khataType: String = "SMALL"  // "SMALL" or "BIG"
)

enum class TransactionType {
    JAMA, BAKI, PAYMENT
}

data class Transaction(
    val id: Long,
    val customerId: Long,
    val amount: Double,
    val type: TransactionType,
    val date: Long,
    val notes: String?
)
