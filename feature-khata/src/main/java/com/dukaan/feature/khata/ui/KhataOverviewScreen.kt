package com.dukaan.feature.khata.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.ui.components.getRelativeTime
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

    // AI insight
    val overallInsight by viewModel.overallInsight.collectAsState()
    val isInsightLoading by viewModel.isOverallInsightLoading.collectAsState()

    val topDebtors = remember(customers) {
        customers.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)
    }
    val topCreditors = remember(customers) {
        customers.filter { it.balance < 0 }.sortedBy { it.balance }.take(5)
    }
    val recentlyActive = remember(customers) {
        customers.sortedByDescending { it.lastActivityAt }.take(5)
    }
    val bigAccounts = remember(customers) {
        customers.filter { Math.abs(it.balance) >= 5000.0 }.sortedByDescending { Math.abs(it.balance) }
    }
    val lateCustomers = remember(customers) {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000L
        customers.filter { it.balance > 0 && it.lastActivityAt < thirtyDaysAgo }
            .sortedByDescending { it.balance }.take(5)
    }
    val netPosition = uiState.totalReceivable + uiState.totalPayable

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Grid - 2x2 with better sizing
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowDownward,
                        iconTint = Color(0xFFEF4444),
                        label = strings.toCollect,
                        value = currencyFormat.format(Math.abs(uiState.totalReceivable))
                    )
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowUpward,
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
                        icon = Icons.Default.AccountBalance,
                        iconTint = if (netPosition >= 0) Color(0xFFEF4444) else Color(0xFF00B37E),
                        label = strings.netPosition,
                        value = "${if (netPosition >= 0) "+" else "-"}${currencyFormat.format(Math.abs(netPosition))}"
                    )
                    OverviewStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.People,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        label = strings.customers,
                        value = "${uiState.customerCount}"
                    )
                }
            }

            // AI Insight
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.overallInsight, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (overallInsight == null && !isInsightLoading) {
                                FilledTonalButton(
                                    onClick = { viewModel.loadOverallInsight() },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(strings.getAiInsight, fontSize = 12.sp)
                                }
                            }
                        }
                        AnimatedVisibility(visible = isInsightLoading || overallInsight != null) {
                            if (isInsightLoading) {
                                Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.analyzing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                overallInsight?.let {
                                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp), lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Late Payments (customers who owe and haven't been active for 30+ days)
            if (lateCustomers.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Default.Warning,
                        title = strings.latePayments,
                        subtitle = "${lateCustomers.size} customers inactive 30+ days",
                        color = Color(0xFFFF6B00)
                    )
                }
                items(lateCustomers, key = { "late-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        showLastActivity = true,
                        onClick = { onCustomerClick(customer.id) },
                        tintColor = Color(0xFFFF6B00)
                    )
                }
            }

            // Top Debtors
            if (topDebtors.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Default.TrendingUp,
                        title = strings.topDebtorsOweYou,
                        subtitle = "${topDebtors.size} customers",
                        color = Color(0xFFEF4444)
                    )
                }
                items(topDebtors, key = { "debtor-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        onClick = { onCustomerClick(customer.id) },
                        tintColor = Color(0xFFEF4444)
                    )
                }
            }

            // Top Creditors
            if (topCreditors.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Default.TrendingDown,
                        title = strings.topCreditorsYouOwe,
                        subtitle = "${topCreditors.size} customers",
                        color = Color(0xFF00B37E)
                    )
                }
                items(topCreditors, key = { "creditor-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        onClick = { onCustomerClick(customer.id) },
                        tintColor = Color(0xFF00B37E)
                    )
                }
            }

            // Big Accounts
            if (bigAccounts.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Default.MenuBook,
                        title = strings.bigAccounts,
                        subtitle = "${bigAccounts.size} accounts above ₹5,000",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(bigAccounts.take(5), key = { "big-${it.id}" }) { customer ->
                    OverviewCustomerRow(
                        customer = customer,
                        currencyFormat = currencyFormat,
                        showLastActivity = true,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }

            // Recently Active
            if (recentlyActive.isNotEmpty()) {
                item {
                    SectionHeader(
                        icon = Icons.Default.Schedule,
                        title = strings.recentlyActive,
                        subtitle = "Last 5 active customers",
                        color = MaterialTheme.colorScheme.secondary
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
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        shape = RoundedCornerShape(14.dp),
        color = iconTint.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewCustomerRow(
    customer: Customer,
    currencyFormat: NumberFormat,
    showLastActivity: Boolean = false,
    onClick: () -> Unit,
    tintColor: Color? = null
) {
    val strings = LocalAppStrings.current
    val balanceColor = tintColor ?: if (customer.balance >= 0) Color(0xFFEF4444) else Color(0xFF00B37E)

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
                shape = CircleShape,
                color = balanceColor.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = customer.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = balanceColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showLastActivity && customer.lastActivityAt > 0) {
                    Text(
                        text = getRelativeTime(customer.lastActivityAt, strings),
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
                color = balanceColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
