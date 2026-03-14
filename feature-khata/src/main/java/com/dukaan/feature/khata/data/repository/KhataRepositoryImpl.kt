package com.dukaan.feature.khata.data.repository

import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.db.entity.TransactionEntity
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.khata.domain.repository.KhataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class KhataRepositoryImpl @Inject constructor(
    private val khataDao: KhataDao
) : KhataRepository {

    override fun getAllCustomers(): Flow<List<Customer>> {
        return khataDao.getAllCustomers().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCustomerById(customerId: Long): Customer? {
        return khataDao.getCustomerById(customerId)?.toDomain()
    }

    override suspend fun addCustomer(name: String, phone: String): Long {
        return khataDao.insertCustomer(CustomerEntity(name = name, phone = phone))
    }

    override fun getTransactionsByCustomer(customerId: Long): Flow<List<Transaction>> {
        return khataDao.getTransactionsByCustomer(customerId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        khataDao.addTransactionAndUpdateBalance(transaction.toEntity())
    }

    private fun CustomerEntity.toDomain() = Customer(
        id = id,
        name = name,
        phone = phone,
        balance = balance,
        lastActivityAt = lastActivityAt
    )

    private fun TransactionEntity.toDomain() = Transaction(
        id = id,
        customerId = customerId,
        amount = amount,
        type = TransactionType.valueOf(type.name),
        date = date,
        notes = notes
    )

    private fun Transaction.toEntity() = TransactionEntity(
        customerId = customerId,
        amount = amount,
        type = com.dukaan.core.db.entity.TransactionType.valueOf(type.name),
        date = date,
        notes = notes
    )
}
