package com.dukaan.feature.billing.domain.repository

import com.dukaan.core.network.model.Bill
import kotlinx.coroutines.flow.Flow

interface BillingRepository {
    suspend fun saveBill(bill: Bill, source: String = "VOICE"): Long
    fun getAllBills(): Flow<List<Bill>>
    suspend fun getBillById(id: Long): Bill?
    suspend fun deleteBill(id: Long)
}
