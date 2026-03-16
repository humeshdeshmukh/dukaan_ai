package com.dukaan.feature.billing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.network.model.Bill
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.LocalAppStrings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private enum class BillSortOption { NEWEST, OLDEST, HIGHEST, LOWEST }
private enum class BillDateRange { ALL, TODAY, WEEK, MONTH, YEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillHistoryScreen(
    viewModel: BillingViewModel,
    onBillClick: (Long) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val voiceBills by viewModel.voiceBills.collectAsState()
    val purchaseBills by viewModel.purchaseBills.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    var billToDelete by remember { mutableStateOf<Long?>(null) }
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Search & filter state
    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(BillSortOption.NEWEST) }
    var paymentFilter by remember { mutableStateOf("All") }
    var dateRange by remember { mutableStateOf(BillDateRange.ALL) }

    val baseBills = if (selectedTab == 0) voiceBills else purchaseBills

    // Apply search
    val searchedBills = remember(baseBills, searchQuery) {
        if (searchQuery.isBlank()) baseBills
        else {
            val q = searchQuery.lowercase()
            baseBills.filter { bill ->
                bill.items.any { it.name.lowercase().contains(q) } ||
                    bill.customerName.lowercase().contains(q) ||
                    bill.sellerName.lowercase().contains(q) ||
                    bill.notes.lowercase().contains(q)
            }
        }
    }

    // Apply payment filter
    val filteredBills = remember(searchedBills, paymentFilter) {
        if (paymentFilter == "All") searchedBills
        else searchedBills.filter { it.paymentMode.equals(paymentFilter, ignoreCase = true) }
    }

    // Apply date range filter
    val dateFilteredBills = remember(filteredBills, dateRange) {
        if (dateRange == BillDateRange.ALL) filteredBills
        else {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            when (dateRange) {
                BillDateRange.TODAY -> { /* already at start of today */ }
                BillDateRange.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
                BillDateRange.MONTH -> cal.add(Calendar.MONTH, -1)
                BillDateRange.YEAR -> cal.add(Calendar.YEAR, -1)
                else -> {}
            }
            val rangeStart = cal.timeInMillis
            filteredBills.filter { it.timestamp >= rangeStart }
        }
    }

    // Apply sort
    val currentBills = remember(dateFilteredBills, sortOption) {
        when (sortOption) {
            BillSortOption.NEWEST -> dateFilteredBills.sortedByDescending { it.timestamp }
            BillSortOption.OLDEST -> dateFilteredBills.sortedBy { it.timestamp }
            BillSortOption.HIGHEST -> dateFilteredBills.sortedByDescending { it.totalAmount }
            BillSortOption.LOWEST -> dateFilteredBills.sortedBy { it.totalAmount }
        }
    }

    val groupedByDate = remember(currentBills) {
        currentBills.groupBy { bill ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = bill.timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }
    }

    val dateTotals = remember(groupedByDate) {
        groupedByDate.mapValues { (_, bills) ->
            bills.sumOf { it.totalAmount }
        }
    }

    val activeFilterCount = (if (sortOption != BillSortOption.NEWEST) 1 else 0) +
        (if (paymentFilter != "All") 1 else 0) +
        (if (dateRange != BillDateRange.ALL) 1 else 0)

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onBackClick?.let { click ->
                        IconButton(onClick = click) {
                            Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                        }
                    }
                    Text(
                        text = strings.billHistory,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = if (onBackClick != null) 4.dp else 16.dp)
                    )
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(44.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    strings.myBills,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    strings.purchaseBills,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search bar + Filter button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    placeholder = { Text(strings.searchBills, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = strings.close, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                FilledTonalIconButton(
                    onClick = { showFilters = !showFilters },
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showFilters || activeFilterCount > 0)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("$activeFilterCount", fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filter",
                            modifier = Modifier.size(22.dp),
                            tint = if (showFilters || activeFilterCount > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Collapsible filter panel
            AnimatedVisibility(visible = showFilters) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sort options
                    Text(
                        strings.sort,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val sortOptions = listOf(
                            BillSortOption.NEWEST to strings.sortNewest,
                            BillSortOption.OLDEST to strings.sortOldest,
                            BillSortOption.HIGHEST to strings.sortHighestAmount,
                            BillSortOption.LOWEST to strings.sortLowestAmount
                        )
                        items(sortOptions) { (option, label) ->
                            FilterChip(
                                selected = sortOption == option,
                                onClick = { sortOption = option },
                                label = { Text(label, fontSize = 11.sp) },
                                leadingIcon = if (sortOption == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // Payment mode filter (only for My Bills tab)
                    if (selectedTab == 0) {
                        Text(
                            strings.paymentType,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val paymentOptions = listOf(
                                "All" to strings.filterAll,
                                "CASH" to strings.cash,
                                "UPI" to strings.upi,
                                "CREDIT" to strings.credit
                            )
                            items(paymentOptions) { (key, label) ->
                                FilterChip(
                                    selected = paymentFilter == key,
                                    onClick = { paymentFilter = key },
                                    label = { Text(label, fontSize = 11.sp) },
                                    leadingIcon = if (paymentFilter == key) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null,
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    // Date range filter
                    Text(
                        strings.dateRange,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dateOptions = listOf(
                            BillDateRange.ALL to strings.dateAll,
                            BillDateRange.TODAY to strings.dateToday,
                            BillDateRange.WEEK to strings.dateThisWeek,
                            BillDateRange.MONTH to strings.dateThisMonth,
                            BillDateRange.YEAR to strings.dateThisYear
                        )
                        items(dateOptions) { (option, label) ->
                            FilterChip(
                                selected = dateRange == option,
                                onClick = { dateRange = option },
                                label = { Text(label, fontSize = 11.sp) },
                                leadingIcon = if (dateRange == option) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                } else null,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // Result count
            Text(
                "${currentBills.size} ${strings.bills}",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            // Bill list or empty state
            if (currentBills.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (searchQuery.isNotBlank() || paymentFilter != "All" || dateRange != BillDateRange.ALL) {
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = strings.noBillsFound,
                            subtitle = ""
                        )
                    } else {
                        EmptyStateView(
                            icon = if (selectedTab == 0) Icons.Default.Receipt else Icons.Default.LocalShipping,
                            title = if (selectedTab == 0) strings.noBillsYet else strings.noPurchaseBillsYet,
                            subtitle = if (selectedTab == 0) strings.billsAppearHere else strings.purchaseBillsAppearHere
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByDate.entries.forEach { entry ->
                        val dateInMillis = entry.key
                        val bills = entry.value
                        item(key = "header_$dateInMillis") {
                            DateHeaderWithTotal(
                                dateInMillis = dateInMillis,
                                total = dateTotals[dateInMillis] ?: 0.0,
                                currencyFormat = currencyFormat
                            )
                        }
                        items(bills, key = { it.id }) { bill ->
                            BillHistoryCard(
                                bill = bill,
                                isPurchaseBill = selectedTab == 1,
                                currencyFormat = currencyFormat,
                                dateFormat = dateFormat,
                                onClick = { onBillClick(bill.id) },
                                onDeleteClick = { billToDelete = bill.id }
                            )
                        }
                    }
                }
            }
        }

        billToDelete?.let { id ->
            ConfirmationDialog(
                title = strings.deleteBill,
                message = strings.deleteBillMessage,
                confirmText = strings.delete,
                onConfirm = {
                    viewModel.deleteBill(id)
                    billToDelete = null
                },
                onDismiss = { billToDelete = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillHistoryCard(
    bill: Bill,
    isPurchaseBill: Boolean = false,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isPurchaseBill)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    if (isPurchaseBill) Icons.Default.LocalShipping else Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (isPurchaseBill)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPurchaseBill && bill.sellerName.isNotBlank())
                        bill.sellerName
                    else
                        "${bill.items.size} ${strings.items}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(bill.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPurchaseBill) {
                    Text(
                        text = "${bill.items.size} ${strings.items}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = currencyFormat.format(bill.totalAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPurchaseBill)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = strings.delete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DateHeaderWithTotal(
    dateInMillis: Long,
    total: Double,
    currencyFormat: NumberFormat
) {
    val strings = LocalAppStrings.current
    val isToday = remember(dateInMillis) {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { timeInMillis = dateInMillis }
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    val isYesterday = remember(dateInMillis) {
        val cal1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val cal2 = Calendar.getInstance().apply { timeInMillis = dateInMillis }
        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    val headerDateText = when {
        isToday -> strings.today
        isYesterday -> strings.yesterday
        else -> {
            val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
            dateFormat.format(Date(dateInMillis))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerDateText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${strings.total}: ${currencyFormat.format(total)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
