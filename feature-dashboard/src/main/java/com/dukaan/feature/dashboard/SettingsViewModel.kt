package com.dukaan.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.dao.ShopProfileDao
import com.dukaan.core.db.entity.ShopProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val shopName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val gstNumber: String = "",
    val email: String = "",
    val upiId: String = "",
    val tagline: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankIfscCode: String = "",
    val isDarkTheme: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val shopProfileDao: ShopProfileDao
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = shopProfileDao.getProfile()
        .map { profile ->
            if (profile != null) {
                SettingsUiState(
                    shopName = profile.shopName,
                    ownerName = profile.ownerName,
                    phone = profile.phone,
                    address = profile.address,
                    gstNumber = profile.gstNumber ?: "",
                    email = profile.email,
                    upiId = profile.upiId,
                    tagline = profile.tagline,
                    bankName = profile.bankName,
                    bankAccountNumber = profile.bankAccountNumber,
                    bankIfscCode = profile.bankIfscCode,
                    isDarkTheme = profile.isDarkTheme
                )
            } else {
                SettingsUiState()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveProfile(
        shopName: String, ownerName: String, phone: String, address: String,
        gstNumber: String, email: String, upiId: String, tagline: String,
        bankName: String, bankAccountNumber: String, bankIfscCode: String
    ) {
        viewModelScope.launch {
            val existing = shopProfileDao.getProfileOnce()
            val entity = (existing ?: ShopProfileEntity()).copy(
                shopName = shopName,
                ownerName = ownerName,
                phone = phone,
                address = address,
                gstNumber = gstNumber.ifBlank { null },
                email = email,
                upiId = upiId,
                tagline = tagline,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                bankIfscCode = bankIfscCode
            )
            shopProfileDao.upsertProfile(entity)
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            shopProfileDao.updateDarkTheme(enabled)
        }
    }
}
