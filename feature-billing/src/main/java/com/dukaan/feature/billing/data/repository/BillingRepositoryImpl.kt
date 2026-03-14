package com.dukaan.feature.billing.data.repository

import com.dukaan.core.network.model.Bill
import com.dukaan.feature.billing.domain.repository.BillingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor() : BillingRepository {
    
    // For MVP, using an in-memory list or simple storage simulation
    // In production, this would use Room (feature-specific or shared core-db)
    private val bills = mutableListOf<Bill>()

    override suspend fun saveBill(bill: Bill): Long {
        val newBill = bill.copy(id = (bills.size + 1).toLong())
        bills.add(newBill)
        return newBill.id
    }

    override fun getAllBills(): Flow<List<Bill>> {
        return flowOf(bills.toList())
    }

    override suspend fun getBillById(id: Long): Bill? {
        return bills.find { it.id == id }
    }
}
