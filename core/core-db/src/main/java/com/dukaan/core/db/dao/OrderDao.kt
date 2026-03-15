package com.dukaan.core.db.dao

import androidx.room.*
import com.dukaan.core.db.entity.OrderEntity
import com.dukaan.core.db.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

@Dao
interface OrderDao {

    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @androidx.room.Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>): Long {
        val orderId = insertOrder(order)
        val itemsWithOrderId = items.map { it.copy(orderId = orderId) }
        insertOrderItems(itemsWithOrderId)
        return orderId
    }

    @androidx.room.Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @androidx.room.Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getOrderWithItems(orderId: Long): OrderWithItems?

    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Query("SELECT COUNT(*) FROM orders")
    fun getOrderCount(): Flow<Int>

    @androidx.room.Transaction
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY timestamp DESC")
    fun getOrdersByStatus(status: String): Flow<List<OrderWithItems>>

    @androidx.room.Transaction
    @Query("SELECT * FROM orders WHERE supplierName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchOrders(query: String): Flow<List<OrderWithItems>>

    @Query("UPDATE orders SET supplierName = :name, notes = :notes WHERE id = :orderId")
    suspend fun updateOrderDetails(orderId: Long, name: String?, notes: String?)

    @Query("UPDATE orders SET itemCount = :count WHERE id = :orderId")
    suspend fun updateItemCount(orderId: Long, count: Int)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)

    @androidx.room.Transaction
    suspend fun updateOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        deleteOrderItems(order.id)
        val itemsWithOrderId = items.map { it.copy(orderId = order.id) }
        insertOrderItems(itemsWithOrderId)
        updateOrderDetails(order.id, order.supplierName, order.notes)
        updateItemCount(order.id, items.size)
        updateOrderStatus(order.id, order.status.name)
    }
}
