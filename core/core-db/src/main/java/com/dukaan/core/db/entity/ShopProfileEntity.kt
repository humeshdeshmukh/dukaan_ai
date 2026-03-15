package com.dukaan.core.db.entity

import androidx.room.*

@Entity(tableName = "shop_profile")
data class ShopProfileEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val gstNumber: String? = null,
    val email: String = "",
    val upiId: String = "",
    val tagline: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankIfscCode: String = "",
    val isDarkTheme: Boolean = false,
    val languageCode: String = "en"
)
