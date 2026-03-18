package com.dukaan.feature.ocr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dukaan.core.network.model.Bill
import com.dukaan.feature.billing.domain.repository.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SellerSummary(
    val sellerName: String,
    val billCount: Int,
    val totalAmount: Double,
    val lastBillDate: Long
)

@HiltViewModel
class ScannedBillHistoryViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val sellerSummaries: StateFlow<List<SellerSummary>> = billingRepository.getScannedBills()
        .map { bills ->
            bills.groupBy { it.sellerName.ifBlank { "Unknown Seller" } }
                .map { (name, groupedBills) ->
                    SellerSummary(
                        sellerName = name,
                        billCount = groupedBills.size,
                        totalAmount = groupedBills.sumOf { it.totalAmount },
                        lastBillDate = groupedBills.maxOf { it.timestamp }
                    )
                }
                .sortedByDescending { it.lastBillDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getBillsByWholesaler(name: String): Flow<List<Bill>> =
        billingRepository.getBillsBySellerName(name)

    fun deleteBill(id: Long) {
        viewModelScope.launch {
            billingRepository.deleteBill(id)
        }
    }

    fun deleteWholesaler(sellerName: String) {
        viewModelScope.launch {
            billingRepository.deleteBillsBySellerName(sellerName)
        }
    }
}
