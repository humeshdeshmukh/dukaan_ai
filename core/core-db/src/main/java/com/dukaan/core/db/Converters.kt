package com.dukaan.core.db

import androidx.room.TypeConverter
import com.dukaan.core.db.entity.TransactionType
import com.dukaan.core.db.entity.BillSource
import com.dukaan.core.db.entity.OrderStatus

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromBillSource(value: BillSource): String = value.name

    @TypeConverter
    fun toBillSource(value: String): BillSource = BillSource.valueOf(value)

    @TypeConverter
    fun fromOrderStatus(value: OrderStatus): String = value.name

    @TypeConverter
    fun toOrderStatus(value: String): OrderStatus = OrderStatus.valueOf(value)
}
