package com.dukaan.feature.khata.domain.model

data class StatementShareData(
    val customerName: String,
    val customerPhone: String,
    val period: String,
    val transactions: List<Transaction>,
    val totalCredit: Double,
    val totalPayment: Double,
    val netBalance: Double
)
