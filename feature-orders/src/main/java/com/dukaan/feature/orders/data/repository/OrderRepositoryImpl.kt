package com.dukaan.feature.orders.data.repository

import com.dukaan.core.db.dao.OrderDao
import com.dukaan.core.db.entity.OrderEntity
import com.dukaan.core.db.entity.OrderItemEntity
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

    override suspend fun saveOrder(order: Order): Long {
        val orderEntity = OrderEntity(
            itemCount = order.items.size,
            timestamp = order.timestamp
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
            ordersWithItems.map { owi ->
                Order(
                    id = owi.order.id.toString(),
                    items = owi.items.map { item ->
                        OrderItem(
                            name = item.name,
                            quantity = item.quantity,
                            unit = item.unit
                        )
                    },
                    timestamp = owi.order.timestamp
                )
            }
        }
    }

    override suspend fun getOrderById(id: Long): Order? {
        return orderDao.getOrderWithItems(id)?.let { owi ->
            Order(
                id = owi.order.id.toString(),
                items = owi.items.map { item ->
                    OrderItem(
                        name = item.name,
                        quantity = item.quantity,
                        unit = item.unit
                    )
                },
                timestamp = owi.order.timestamp
            )
        }
    }

    override suspend fun deleteOrder(id: Long) {
        orderDao.deleteOrder(id)
    }

    override suspend fun updateOrderStatus(id: Long, status: String) {
        orderDao.updateOrderStatus(id, status)
    }
}
