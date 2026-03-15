package com.dukaan.feature.khata.domain.repository

import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface KhataRepository {
    fun getAllCustomers(): Flow<List<Customer>>
    suspend fun getCustomerById(customerId: Long): Customer?
    fun getCustomerFlow(customerId: Long): Flow<Customer?>
    suspend fun addCustomer(name: String, phone: String, khataType: String = "SMALL"): Long
    suspend fun updateCustomer(customerId: Long, name: String, phone: String)
    suspend fun deleteCustomer(customerId: Long)

    fun getTransactionsByCustomer(customerId: Long): Flow<List<Transaction>>
    fun getTransactionsByDateRange(customerId: Long, startDate: Long, endDate: Long): Flow<List<Transaction>>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun deleteTransaction(transactionId: Long)

    fun getTotalReceivable(): Flow<Double>
    fun getTotalPayable(): Flow<Double>
    fun getCustomerCount(): Flow<Int>
}
