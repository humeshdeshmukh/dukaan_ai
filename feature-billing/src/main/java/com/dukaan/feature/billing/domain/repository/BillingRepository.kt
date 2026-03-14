package com.dukaan.feature.billing.domain.repository

import com.dukaan.core.network.model.Bill
import kotlinx.coroutines.flow.Flow

interface BillingRepository {
    suspend fun saveBill(bill: Bill, source: String = "VOICE", imagePath: String? = null): Long
    fun getAllBills(): Flow<List<Bill>>
    suspend fun getBillById(id: Long): Bill?
    suspend fun deleteBill(id: Long)
    fun getAllSellerNames(): Flow<List<String>>
    fun getBillsBySellerName(sellerName: String): Flow<List<Bill>>
    fun getScannedBills(): Flow<List<Bill>>
}
