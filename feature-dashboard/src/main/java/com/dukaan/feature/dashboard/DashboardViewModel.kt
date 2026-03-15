package com.dukaan.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.dao.BillDao
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.dao.ShopProfileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val shopName: String = "",
    val ownerName: String = "",
    val todaySales: Double = 0.0,
    val totalBills: Int = 0,
    val totalReceivable: Double = 0.0,
    val customerCount: Int = 0,
    val languageCode: String = "en"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    billDao: BillDao,
    khataDao: KhataDao,
    shopProfileDao: ShopProfileDao
) : ViewModel() {

    private val todayStartMillis: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val uiState: StateFlow<DashboardUiState> = combine(
        shopProfileDao.getProfile(),
        billDao.getTotalSalesSince(todayStartMillis),
        billDao.getBillCount(),
        khataDao.getTotalReceivable(),
        khataDao.getCustomerCount()
    ) { profile, todaySales, billCount, totalReceivable, customerCount ->
        DashboardUiState(
            shopName = profile?.shopName ?: "",
            ownerName = profile?.ownerName ?: "",
            todaySales = todaySales ?: 0.0,
            totalBills = billCount ?: 0,
            totalReceivable = kotlin.math.abs(totalReceivable ?: 0.0),
            customerCount = customerCount ?: 0,
            languageCode = profile?.languageCode ?: "en"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
