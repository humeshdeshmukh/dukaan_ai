package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.BillEntity
import com.dukaan.core.db.entity.BillItemEntity
import kotlinx.coroutines.flow.Flow

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "billId"
    )
    val items: List<BillItemEntity>
)

@Dao
interface BillDao {

    @Insert
    suspend fun insertBill(bill: BillEntity): Long

    @Insert
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @androidx.room.Transaction
    suspend fun insertBillWithItems(bill: BillEntity, items: List<BillItemEntity>): Long {
        val billId = insertBill(bill)
        val itemsWithBillId = items.map { it.copy(billId = billId) }
        insertBillItems(itemsWithBillId)
        return billId
    }

    @androidx.room.Transaction
    @Query("SELECT * FROM bills ORDER BY timestamp DESC")
    fun getAllBillsWithItems(): Flow<List<BillWithItems>>

    @androidx.room.Transaction
    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun getBillWithItems(billId: Long): BillWithItems?

    @Query("SELECT * FROM bills ORDER BY timestamp DESC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBill(billId: Long)

    @Query("SELECT COUNT(*) FROM bills WHERE source = 'VOICE'")
    fun getBillCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalAmount), 0.0) FROM bills WHERE timestamp >= :since AND source = 'VOICE'")
    fun getTotalSalesSince(since: Long): Flow<Double>

    @Query("SELECT DISTINCT sellerName FROM bills WHERE sellerName != '' ORDER BY sellerName ASC")
    fun getAllSellerNames(): Flow<List<String>>

    @androidx.room.Transaction
    @Query("SELECT * FROM bills WHERE sellerName = :sellerName ORDER BY timestamp DESC")
    fun getBillsBySellerName(sellerName: String): Flow<List<BillWithItems>>

    @androidx.room.Transaction
    @Query("SELECT * FROM bills WHERE source = 'VOICE' ORDER BY timestamp DESC")
    fun getVoiceBills(): Flow<List<BillWithItems>>

    @androidx.room.Transaction
    @Query("SELECT * FROM bills WHERE source = 'OCR' ORDER BY timestamp DESC")
    fun getScannedBills(): Flow<List<BillWithItems>>
}
