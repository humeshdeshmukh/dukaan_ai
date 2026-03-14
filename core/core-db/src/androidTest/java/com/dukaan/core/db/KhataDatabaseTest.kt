package com.dukaan.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.db.entity.TransactionEntity
import com.dukaan.core.db.entity.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KhataDatabaseTest {

    private lateinit var db: KhataDatabase
    private lateinit var dao: KhataDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KhataDatabase::class.java).build()
        dao = db.khataDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndGetCustomer() = runBlocking {
        val customer = CustomerEntity(name = "Ramesh", phone = "9876543210")
        val id = dao.insertCustomer(customer)
        
        val customers = dao.getAllCustomers().first()
        assertEquals(1, customers.size)
        assertEquals("Ramesh", customers[0].name)
        assertEquals(id, customers[0].id)
    }

    @Test
    fun testAddTransactionUpdatesBalance() = runBlocking {
        val customerId = dao.insertCustomer(CustomerEntity(name = "Suresh", phone = "1234567890"))
        
        // Add BAKI (Debit)
        val transaction1 = TransactionEntity(customerId = customerId, amount = 500.0, type = TransactionType.BAKI)
        dao.addTransactionAndUpdateBalance(transaction1)
        
        var customer = dao.getCustomerById(customerId)
        assertEquals(500.0, customer?.balance ?: 0.0, 0.01)
        
        // Add JAMA (Credit)
        val transaction2 = TransactionEntity(customerId = customerId, amount = 200.0, type = TransactionType.JAMA)
        dao.addTransactionAndUpdateBalance(transaction2)
        
        customer = dao.getCustomerById(customerId)
        assertEquals(300.0, customer?.balance ?: 0.0, 0.01)
    }
}
