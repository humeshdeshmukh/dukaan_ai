package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.db.entity.TransactionEntity
import com.dukaan.core.db.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface KhataDao {
    // Customer Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :customerId")
    fun getCustomerFlow(customerId: Long): Flow<CustomerEntity?>

    @Query("UPDATE customers SET balance = balance + :amountChange, lastActivityAt = :timestamp WHERE id = :customerId")
    suspend fun updateCustomerBalance(customerId: Long, amountChange: Double, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE customerId = :customerId")
    suspend fun deleteTransactionsByCustomer(customerId: Long)

    // Summary queries
    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM customers WHERE balance < 0")
    fun getTotalReceivable(): Flow<Double>

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM customers WHERE balance > 0")
    fun getTotalPayable(): Flow<Double>

    @Query("SELECT COUNT(*) FROM customers")
    fun getCustomerCount(): Flow<Int>

    // Transaction Operations
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY date DESC")
    fun getTransactionsByCustomer(customerId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByCustomerAndDateRange(customerId: Long, startDate: Long, endDate: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)

    @Query("SELECT * FROM transactions WHERE synced = 0")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Transaction
    suspend fun addTransactionAndUpdateBalance(transaction: TransactionEntity) {
        insertTransaction(transaction)
        val balanceChange = if (transaction.type == TransactionType.JAMA) {
            -transaction.amount
        } else {
            transaction.amount
        }
        updateCustomerBalance(transaction.customerId, balanceChange)
    }

    @Transaction
    suspend fun deleteTransactionAndReverseBalance(transactionId: Long) {
        val transaction = getTransactionById(transactionId) ?: return
        deleteTransactionById(transactionId)
        val balanceReversal = if (transaction.type == TransactionType.JAMA) {
            transaction.amount
        } else {
            -transaction.amount
        }
        updateCustomerBalance(transaction.customerId, balanceReversal)
    }
}
