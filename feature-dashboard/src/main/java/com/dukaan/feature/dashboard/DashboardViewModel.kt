package com.dukaan.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.db.dao.BillDao
import com.dukaan.core.db.dao.BillWithItems
import com.dukaan.core.db.dao.KhataDao
import com.dukaan.core.db.dao.ShopProfileDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.Calendar
import javax.inject.Inject

data class RecentBillItem(
    val id: Long,
    val itemCount: Int,
    val totalAmount: Double,
    val timestamp: Long,
    val source: String
)

data class DashboardUiState(
    val shopName: String = "",
    val ownerName: String = "",
    val todaySales: Double = 0.0,
    val totalBills: Int = 0,
    val totalReceivable: Double = 0.0,
    val customerCount: Int = 0,
    val recentBills: List<RecentBillItem> = emptyList(),
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
        khataDao.getCustomerCount(),
        billDao.getAllBillsWithItems()
    ) { values ->
        val profile = values[0] as? com.dukaan.core.db.entity.ShopProfileEntity
        @Suppress("UNCHECKED_CAST")
        val allBills = values[5] as List<BillWithItems>

        DashboardUiState(
            shopName = profile?.shopName ?: "",
            ownerName = profile?.ownerName ?: "",
            todaySales = values[1] as? Double ?: 0.0,
            totalBills = values[2] as? Int ?: 0,
            totalReceivable = kotlin.math.abs(values[3] as? Double ?: 0.0),
            customerCount = values[4] as? Int ?: 0,
            recentBills = allBills.take(5).map { bwi ->
                RecentBillItem(
                    id = bwi.bill.id,
                    itemCount = bwi.items.size,
                    totalAmount = bwi.bill.totalAmount,
                    timestamp = bwi.bill.timestamp,
                    source = bwi.bill.source.name
                )
            },
            languageCode = profile?.languageCode ?: "en"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
