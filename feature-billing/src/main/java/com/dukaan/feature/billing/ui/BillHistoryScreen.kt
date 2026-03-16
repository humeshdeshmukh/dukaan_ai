package com.dukaan.feature.billing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dukaan.core.network.model.Bill
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.LocalAppStrings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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

    val currentBills = if (selectedTab == 0) voiceBills else purchaseBills

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(strings.billHistory, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        onBackClick?.let { click ->
                            IconButton(onClick = click) {
                                Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                            }
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                strings.myBills,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                strings.purchaseBills,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (currentBills.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = if (selectedTab == 0) Icons.Default.Receipt else Icons.Default.LocalShipping,
                    title = if (selectedTab == 0) strings.noBillsYet else strings.noPurchaseBillsYet,
                    subtitle = if (selectedTab == 0) strings.billsAppearHere else strings.purchaseBillsAppearHere
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(currentBills, key = { it.id }) { bill ->
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
