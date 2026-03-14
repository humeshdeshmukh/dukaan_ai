package com.dukaan.feature.orders.domain.repository

import com.dukaan.core.network.model.Order
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    suspend fun saveOrder(order: Order): Long
    fun getAllOrders(): Flow<List<Order>>
    suspend fun getOrderById(id: Long): Order?
    suspend fun deleteOrder(id: Long)
    suspend fun updateOrderStatus(id: Long, status: String)
}
