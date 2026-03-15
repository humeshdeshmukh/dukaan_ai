package com.dukaan.feature.orders.data.repository

import com.dukaan.core.db.dao.OrderDao
import com.dukaan.core.db.dao.OrderWithItems
import com.dukaan.core.db.entity.OrderEntity
import com.dukaan.core.db.entity.OrderItemEntity
import com.dukaan.core.db.entity.OrderStatus
import com.dukaan.core.network.model.Order
import com.dukaan.core.network.model.OrderItem
import com.dukaan.feature.orders.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao
) : OrderRepository {

    private fun mapToOrder(owi: OrderWithItems): Order {
        return Order(
            id = owi.order.id.toString(),
            items = owi.items.map { item ->
                OrderItem(name = item.name, quantity = item.quantity, unit = item.unit)
            },
            timestamp = owi.order.timestamp,
            supplierName = owi.order.supplierName,
            status = owi.order.status.name,
            notes = owi.order.notes
        )
    }

    override suspend fun saveOrder(order: Order): Long {
        val orderEntity = OrderEntity(
            itemCount = order.items.size,
            timestamp = order.timestamp,
            supplierName = order.supplierName,
            status = try { OrderStatus.valueOf(order.status) } catch (_: Exception) { OrderStatus.PENDING },
            notes = order.notes
        )
        val itemEntities = order.items.map { item ->
            OrderItemEntity(
                orderId = 0,
                name = item.name,
                quantity = item.quantity,
                unit = item.unit
            )
        }
        return orderDao.insertOrderWithItems(orderEntity, itemEntities)
    }

    override fun getAllOrders(): Flow<List<Order>> {
        return orderDao.getAllOrdersWithItems().map { ordersWithItems ->
            ordersWithItems.map { owi -> mapToOrder(owi) }
        }
    }

    override suspend fun getOrderById(id: Long): Order? {
        return orderDao.getOrderWithItems(id)?.let { owi -> mapToOrder(owi) }
    }

    override suspend fun deleteOrder(id: Long) {
        orderDao.deleteOrder(id)
    }

    override suspend fun updateOrderStatus(id: Long, status: String) {
        orderDao.updateOrderStatus(id, status)
    }

    override fun getOrdersByStatus(status: String): Flow<List<Order>> {
        return orderDao.getOrdersByStatus(status).map { ordersWithItems ->
            ordersWithItems.map { owi -> mapToOrder(owi) }
        }
    }

    override fun searchOrders(query: String): Flow<List<Order>> {
        return orderDao.searchOrders(query).map { ordersWithItems ->
            ordersWithItems.map { owi -> mapToOrder(owi) }
        }
    }

    override suspend fun updateOrder(order: Order) {
        val orderId = order.id.toLong()
        val orderEntity = OrderEntity(
            id = orderId,
            supplierName = order.supplierName,
            status = try { OrderStatus.valueOf(order.status) } catch (_: Exception) { OrderStatus.PENDING },
            itemCount = order.items.size,
            timestamp = order.timestamp,
            notes = order.notes
        )
        val itemEntities = order.items.map { item ->
            OrderItemEntity(orderId = orderId, name = item.name, quantity = item.quantity, unit = item.unit)
        }
        orderDao.updateOrderWithItems(orderEntity, itemEntities)
    }

    override suspend fun duplicateOrder(orderId: Long): Long {
        val existing = orderDao.getOrderWithItems(orderId) ?: return -1
        val newEntity = existing.order.copy(
            id = 0,
            timestamp = System.currentTimeMillis(),
            status = OrderStatus.PENDING
        )
        val newItems = existing.items.map { it.copy(id = 0, orderId = 0) }
        return orderDao.insertOrderWithItems(newEntity, newItems)
    }
}
