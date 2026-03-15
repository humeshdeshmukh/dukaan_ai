package com.dukaan.core.db.entity

import androidx.room.*

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val balance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val khataType: String = "SMALL"  // "SMALL" or "BIG"
)
