package com.dukaan.feature.khata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.domain.model.Customer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataOverviewScreen(
    viewModel: KhataViewModel,
    onBackClick: () -> Unit,
    onCustomerClick: (Long) -> Unit
) {
    val strings = LocalAppStrings.current
    val uiState by viewModel.uiState.collectAsState()
    val customers by viewModel.filteredCustomers.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    val topDebtors = remember(customers) {
        customers.filter { it.balance > 0 }
            .sortedByDescending { it.balance }
            .take(5)
    }

    val topCreditors = remember(customers) {
        customers.filter { it.balance < 0 }
            .sortedBy { it.balance }
            .take(5)
    }

    val recentlyActive = remember(customers) {
        customers.sortedByDescending { it.lastActivityAt }.take(5)
    }

    val netPosition = uiState.totalPayable + uiState.totalReceivable // receivable is negative

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.khataOverview, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.TrendingUp,
                        iconTint = Color(0xFFEF4444),
                        label = strings.toCollect,
                        value = currencyFormat.format(Math.abs(uiState.totalReceivable))
                    )
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.TrendingDown,
                        iconTint = Color(0xFF00B37E),
                        label = strings.toPay,
                        value = currencyFormat.format(Math.abs(uiState.totalPayable))
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.AccountBalance,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label = strings.netPosition,
                        value = currencyFormat.format(Math.abs(netPosition))
                    )
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.People,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        label = strings.customers,
                        value = "${uiState.customerCount}"
                    )
                }
            }

            // Top Debtors (customers who owe you)
            if (topDebtors.isNotEmpty()) {
                item {
                    Text(
                        strings.topDebtorsOweYou,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
                items(topDebtors, key = { "debtor-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }

            // Top Creditors (you owe them)
            if (topCreditors.isNotEmpty()) {
                item {
                    Text(
                        strings.topCreditorsYouOwe,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B37E)
                    )
                }
                items(topCreditors, key = { "creditor-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }

            // Recently Active
            if (recentlyActive.isNotEmpty()) {
                item {
                    Text(
                        strings.recentlyActive,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(recentlyActive, key = { "recent-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        showLastActivity = true,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OverviewStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewCustomerRow(
    customer: Customer,
    currencyFormat: NumberFormat,
    showLastActivity: Boolean = false,
    onClick: () -> Unit
) {
    val balanceColor = if (customer.balance >= 0) Color(0xFFEF4444) else Color(0xFF00B37E)
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = customer.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (showLastActivity) {
                    Text(
                        text = "Active: ${dateFormat.format(Date(customer.lastActivityAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = currencyFormat.format(Math.abs(customer.balance)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = balanceColor
            )
        }
    }
}
