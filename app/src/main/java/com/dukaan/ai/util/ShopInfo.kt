package com.dukaan.ai.util

import com.dukaan.feature.dashboard.SettingsUiState

data class ShopInfo(
    val shopName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val gstNumber: String = "",
    val email: String = "",
    val upiId: String = ""
)

fun SettingsUiState.toShopInfo() = ShopInfo(
    shopName = shopName,
    ownerName = ownerName,
    phone = phone,
    address = address,
    gstNumber = gstNumber,
    email = email,
    upiId = upiId
)
