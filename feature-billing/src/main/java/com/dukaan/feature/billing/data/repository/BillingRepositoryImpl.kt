package com.dukaan.feature.billing.data.repository

import com.dukaan.core.db.dao.BillDao
import com.dukaan.core.db.entity.BillEntity
import com.dukaan.core.db.entity.BillItemEntity
import com.dukaan.core.db.entity.BillSource
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.feature.billing.domain.repository.BillingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor(
    private val billDao: BillDao
) : BillingRepository {

    override suspend fun saveBill(bill: Bill, source: String, imagePath: String?): Long {
        val billEntity = BillEntity(
            totalAmount = bill.totalAmount,
            source = try { BillSource.valueOf(source) } catch (e: Exception) { BillSource.VOICE },
            sellerName = bill.sellerName,
            billNumber = bill.billNumber,
            imagePath = imagePath,
            timestamp = bill.timestamp
        )
        val itemEntities = bill.items.map { item ->
            BillItemEntity(
                billId = 0,
                name = item.name,
                quantity = item.quantity,
                unit = item.unit,
                price = item.price
            )
        }
        return billDao.insertBillWithItems(billEntity, itemEntities)
    }

    override fun getAllBills(): Flow<List<Bill>> {
        return billDao.getAllBillsWithItems().map { billsWithItems ->
            billsWithItems.map { it.toBill() }
        }
    }

    override suspend fun getBillById(id: Long): Bill? {
        return billDao.getBillWithItems(id)?.toBill()
    }

    override suspend fun deleteBill(id: Long) {
        billDao.deleteBill(id)
    }

    override fun getAllSellerNames(): Flow<List<String>> {
        return billDao.getAllSellerNames()
    }

    override fun getBillsBySellerName(sellerName: String): Flow<List<Bill>> {
        return billDao.getBillsBySellerName(sellerName).map { billsWithItems ->
            billsWithItems.map { it.toBill() }
        }
    }

    override fun getScannedBills(): Flow<List<Bill>> {
        return billDao.getScannedBills().map { billsWithItems ->
            billsWithItems.map { it.toBill() }
        }
    }

    private fun com.dukaan.core.db.dao.BillWithItems.toBill(): Bill {
        return Bill(
            id = bill.id,
            items = items.map { item ->
                BillItem(
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    price = item.price
                )
            },
            totalAmount = bill.totalAmount,
            sellerName = bill.sellerName,
            billNumber = bill.billNumber,
            imagePath = bill.imagePath,
            timestamp = bill.timestamp
        )
    }
}
