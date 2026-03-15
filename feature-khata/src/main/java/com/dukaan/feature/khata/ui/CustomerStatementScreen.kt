package com.dukaan.feature.khata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.StatementShareData
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.khata.ui.components.DateRangePickerDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerStatementScreen(
    customerId: Long,
    viewModel: KhataViewModel,
    onBackClick: () -> Unit,
    onShareClick: (StatementShareData) -> Unit
) {
    val strings = LocalAppStrings.current
    val customer by viewModel.getCustomerFlow(customerId).collectAsState(initial = null)
    val allTransactions by viewModel.getTransactions(customerId).collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf("All") }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    var showDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableLongStateOf(0L) }
    var customEndDate by remember { mutableLongStateOf(0L) }

    val filteredTransactions = remember(allTransactions, selectedFilter, customStartDate, customEndDate) {
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            "Week" -> {
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                allTransactions.filter { it.date >= weekAgo }
            }
            "Month" -> {
                val monthAgo = now - 30L * 24 * 60 * 60 * 1000L
                allTransactions.filter { it.date >= monthAgo }
            }
            "3 Months" -> {
                val threeMonthsAgo = now - 90L * 24 * 60 * 60 * 1000L
                allTransactions.filter { it.date >= threeMonthsAgo }
            }
            "Custom" -> {
                if (customStartDate > 0 && customEndDate > 0) {
                    allTransactions.filter { it.date in customStartDate..customEndDate }
                } else allTransactions
            }
            else -> allTransactions
        }
    }

    val totalCredit = filteredTransactions.filter { it.type == TransactionType.BAKI }.sumOf { it.amount }
    val totalPayment = filteredTransactions.filter { it.type == TransactionType.JAMA }.sumOf { it.amount }
    val netBalance = totalCredit - totalPayment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.statement, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val c = customer ?: return@IconButton
                        val period = if (selectedFilter == "Custom" && customStartDate > 0 && customEndDate > 0) {
                            "${dateFormat.format(Date(customStartDate))} - ${dateFormat.format(Date(customEndDate))}"
                        } else selectedFilter
                        onShareClick(
                            StatementShareData(
                                customerName = c.name,
                                customerPhone = c.phone,
                                period = period,
                                transactions = filteredTransactions,
                                totalCredit = totalCredit,
                                totalPayment = totalPayment,
                                netBalance = netBalance
                            )
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = strings.share)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Customer Header
            item {
                customer?.let { c ->
                    Text(
                        text = c.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = c.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Date Filter
            item {
                val filterLabels = mapOf(
                    "All" to strings.all,
                    "Week" to strings.week,
                    "Month" to strings.month,
                    "3 Months" to strings.threeMonths,
                    "Custom" to strings.custom
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Week", "Month", "3 Months", "Custom").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                if (filter == "Custom") {
                                    showDatePicker = true
                                } else {
                                    selectedFilter = filter
                                }
                            },
                            label = { Text(filterLabels[filter] ?: filter, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (selectedFilter == filter) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Show selected date range for Custom filter
            if (selectedFilter == "Custom" && customStartDate > 0 && customEndDate > 0) {
                item {
                    Text(
                        text = "${dateFormat.format(Date(customStartDate))} - ${dateFormat.format(Date(customEndDate))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Summary Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${strings.summary} ($selectedFilter)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.totalCreditBaki, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currencyFormat.format(totalCredit),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.totalPaymentsJama, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currencyFormat.format(totalPayment),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00B37E)
                            )
                        }
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                strings.netBalance,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                currencyFormat.format(Math.abs(netBalance)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (netBalance > 0) Color(0xFFEF4444) else Color(0xFF00B37E)
                            )
                        }
                        Text(
                            text = if (netBalance > 0) strings.customerOwesYou else if (netBalance < 0) strings.youOweCustomer else strings.settled,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Transactions Header
            item {
                Text(
                    text = "${strings.transactions} (${filteredTransactions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Transaction Items
            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            strings.noTransactionsInPeriod,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    StatementTransactionRow(transaction, currencyFormat, dateFormat)
                }
            }
        }
    }

    // Custom Date Range Picker
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                customStartDate = start
                customEndDate = end
                selectedFilter = "Custom"
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun StatementTransactionRow(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    val strings = LocalAppStrings.current
    val isJama = transaction.type == TransactionType.JAMA
    val tint = if (isJama) Color(0xFF00B37E) else Color(0xFFEF4444)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = tint.copy(alpha = 0.1f)
        ) {
            Icon(
                imageVector = if (isJama) Icons.Default.CallReceived else Icons.Default.CallMade,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isJama) strings.paymentReceived else strings.creditGiven,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dateFormat.format(Date(transaction.date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!transaction.notes.isNullOrBlank()) {
                Text(
                    text = transaction.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "${if (isJama) "-" else "+"}${currencyFormat.format(transaction.amount)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}
