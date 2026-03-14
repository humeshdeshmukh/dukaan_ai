package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataDao {
    // Customer Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Query("UPDATE customers SET balance = balance + :amountChange, lastActivityAt = :timestamp WHERE id = :customerId")
    suspend fun updateCustomerBalance(customerId: Long, amountChange: Double, timestamp: Long = System.currentTimeMillis())

    // Transaction Operations
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date DESC")
    fun getTransactionsByCustomer(customerId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Transaction
    suspend fun addTransactionAndUpdateBalance(transaction: TransactionEntity) {
        insertTransaction(transaction)
        val balanceChange = if (transaction.type == com.dukaan.core.db.entity.TransactionType.JAMA) {
            -transaction.amount // JAMA reduces credit (or increases balance towards shopkeeper)
        } else {
            transaction.amount // BAKI increases debt
        }
        updateCustomerBalance(transaction.customerId, balanceChange)
    }
}
