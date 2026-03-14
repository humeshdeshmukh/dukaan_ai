package com.dukaan.feature.khata.domain.repository

import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface KhataRepository {
    fun getAllCustomers(): Flow<List<Customer>>
    suspend fun getCustomerById(customerId: Long): Customer?
    suspend fun addCustomer(name: String, phone: String): Long
    
    fun getTransactionsByCustomer(customerId: Long): Flow<List<Transaction>>
    suspend fun addTransaction(transaction: Transaction)
}
