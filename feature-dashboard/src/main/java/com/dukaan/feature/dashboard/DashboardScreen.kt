package com.dukaan.feature.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onScanBillClick: () -> Unit,
    onVoiceBillingClick: () -> Unit,
    onSmartKhataClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onBillHistoryClick: () -> Unit = {},
    onPurchaseBillsClick: () -> Unit = {},
    onBillClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var animated by remember { mutableStateOf(false) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 } }

    LaunchedEffect(Unit) { animated = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(7.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Dukaan AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // Compact Greeting
            AnimatedVisibility(
                visible = animated,
                enter = fadeIn(tween(400))
            ) {
                CompactGreeting(
                    shopName = uiState.shopName,
                    ownerName = uiState.ownerName
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Business Stats
            AnimatedVisibility(
                visible = animated,
                enter = fadeIn(tween(400, delayMillis = 100))
            ) {
                StatsGrid(
                    todaySales = currencyFormat.format(uiState.todaySales),
                    totalBills = "${uiState.totalBills}",
                    creditDue = currencyFormat.format(uiState.totalReceivable),
                    customers = "${uiState.customerCount}"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Actions
            AnimatedVisibility(
                visible = animated,
                enter = fadeIn(tween(400, delayMillis = 200))
            ) {
                Column {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                    )

                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactFeatureCard(
                            label = "Scan Bill",
                            subtitle = "OCR Scanner",
                            icon = Icons.Default.QrCodeScanner,
                            accentColor = Color(0xFF065F46),
                            onClick = onScanBillClick,
                            modifier = Modifier.weight(1f)
                        )
                        CompactFeatureCard(
                            label = "Voice Bill",
                            subtitle = "Speak Items",
                            icon = Icons.Default.Mic,
                            accentColor = Color(0xFFD97706),
                            onClick = onVoiceBillingClick,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactFeatureCard(
                            label = "Smart Khata",
                            subtitle = "Ledger Book",
                            icon = Icons.Default.MenuBook,
                            accentColor = Color(0xFF7C3AED),
                            onClick = onSmartKhataClick,
                            modifier = Modifier.weight(1f)
                        )
                        CompactFeatureCard(
                            label = "Orders",
                            subtitle = "Wholesale",
                            icon = Icons.Default.ShoppingCart,
                            accentColor = Color(0xFF2563EB),
                            onClick = onOrdersClick,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CompactFeatureCard(
                            label = "Bill History",
                            subtitle = "Past Bills",
                            icon = Icons.Default.Receipt,
                            accentColor = Color(0xFF0891B2),
                            onClick = onBillHistoryClick,
                            modifier = Modifier.weight(1f)
                        )
                        CompactFeatureCard(
                            label = "Purchase Bills",
                            subtitle = "By Wholesaler",
                            icon = Icons.Default.LocalShipping,
                            accentColor = Color(0xFF9333EA),
                            onClick = onPurchaseBillsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Bills
            if (uiState.recentBills.isNotEmpty()) {
                AnimatedVisibility(
                    visible = animated,
                    enter = fadeIn(tween(400, delayMillis = 300))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Bills",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                            TextButton(
                                onClick = onBillHistoryClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    "View All",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        uiState.recentBills.forEach { bill ->
                            RecentBillRow(
                                bill = bill,
                                currencyFormat = currencyFormat,
                                onClick = { onBillClick(bill.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompactGreeting(shopName: String, ownerName: String) {
    val calendar = remember { Calendar.getInstance() }
    val greeting = remember {
        when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM, EEE", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(calendar.time) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (ownerName.isNotBlank()) "$greeting, $ownerName" else greeting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (shopName.isNotBlank()) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = todayDate,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun StatsGrid(
    todaySales: String,
    totalBills: String,
    creditDue: String,
    customers: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Today's Sales",
                value = todaySales,
                icon = Icons.Outlined.TrendingUp,
                accentColor = Color(0xFF00B37E),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Total Bills",
                value = totalBills,
                icon = Icons.Outlined.Receipt,
                accentColor = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Credit Due",
                value = creditDue,
                icon = Icons.Outlined.AccountBalanceWallet,
                accentColor = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Customers",
                value = customers,
                icon = Icons.Outlined.People,
                accentColor = Color(0xFF7C3AED),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(6.dp),
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactFeatureCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.08f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = accentColor
                )
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RecentBillRow(
    bill: RecentBillItem,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val isOcr = bill.source == "OCR"

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (isOcr)
                    Color(0xFF065F46).copy(alpha = 0.1f)
                else
                    Color(0xFFD97706).copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = if (isOcr) Icons.Default.QrCodeScanner else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = if (isOcr) Color(0xFF065F46) else Color(0xFFD97706)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${bill.itemCount} items",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateFormat.format(Date(bill.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = currencyFormat.format(bill.totalAmount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
