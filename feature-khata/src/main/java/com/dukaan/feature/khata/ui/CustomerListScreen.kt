package com.dukaan.feature.khata.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.ui.components.AddCustomerDialog
import com.dukaan.feature.khata.ui.components.CustomerItem
import com.dukaan.feature.khata.ui.components.KhataAiChatSheet
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: KhataViewModel,
    onCustomerClick: (Long) -> Unit,
    onBackClick: (() -> Unit)? = null,
    languageCode: String = "en"
) {
    val strings = LocalAppStrings.current
    val customers by viewModel.filteredCustomers.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<Long?>(null) }
    var showAiChat by remember { mutableStateOf(false) }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    // Account category filter by khataType: All, Big, Small
    var accountFilter by remember { mutableStateOf("All") }

    // AI states
    val overallChatMessages by viewModel.overallChatMessages.collectAsState()
    val isOverallAiTyping by viewModel.isOverallAiTyping.collectAsState()

    val filteredByAccount = remember(customers, accountFilter) {
        when (accountFilter) {
            "BIG" -> customers.filter { it.khataType == "BIG" }
            "SMALL" -> customers.filter { it.khataType == "SMALL" }
            else -> customers
        }
    }

    // Counts
    val bigCount = remember(customers) { customers.count { it.khataType == "BIG" } }
    val smallCount = remember(customers) { customers.count { it.khataType == "SMALL" } }

    // AI Chat full screen
    if (showAiChat) {
        KhataAiChatSheet(
            customerName = strings.smartKhata,
            messages = overallChatMessages,
            isAiTyping = isOverallAiTyping,
            onSendMessage = { message -> viewModel.sendOverallChatMessage(message, languageCode) },
            onDismiss = { showAiChat = false; viewModel.clearOverallChat() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.smartKhata, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    onBackClick?.let { click ->
                        IconButton(onClick = click) {
                            Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // AI Chat small FAB
                SmallFloatingActionButton(
                    onClick = { showAiChat = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = strings.aiKhataAssistant, modifier = Modifier.size(20.dp))
                }
                // Add Customer FAB
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = strings.addCustomer)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Stats Grid - 2x2
            if (uiState.customerCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatGridCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ArrowDownward,
                        label = strings.toCollect,
                        value = currencyFormat.format(Math.abs(uiState.totalReceivable)),
                        color = Color(0xFFEF4444)
                    )
                    StatGridCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccountBalance,
                        label = strings.netPosition,
                        value = currencyFormat.format(Math.abs(uiState.totalReceivable + uiState.totalPayable)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatGridCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MenuBook,
                        label = strings.bigAccounts,
                        value = "$bigCount",
                        color = Color(0xFFEF4444)
                    )
                    StatGridCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.StickyNote2,
                        label = strings.smallAccounts,
                        value = "$smallCount",
                        color = Color(0xFF00B37E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                placeholder = { Text(strings.searchByNameOrPhone, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Account type tabs: All / Big Khata / Small Khata
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val accountOptions = listOf(
                    "All" to strings.allAccounts,
                    "BIG" to strings.bigAccounts,
                    "SMALL" to strings.smallAccounts
                )
                accountOptions.forEach { (key, label) ->
                    FilterChip(
                        selected = accountFilter == key,
                        onClick = { accountFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        leadingIcon = when (key) {
                            "BIG" -> {{ Icon(Icons.Default.MenuBook, null, Modifier.size(14.dp)) }}
                            "SMALL" -> {{ Icon(Icons.Default.StickyNote2, null, Modifier.size(14.dp)) }}
                            else -> if (accountFilter == key) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Total count below filters
            Text(
                "${filteredByAccount.size} ${strings.customers}",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortOptions = listOf(
                    SortOption.NAME to strings.sortName,
                    SortOption.BALANCE_HIGH to strings.sortHighestBalance,
                    SortOption.BALANCE_LOW to strings.sortLowestBalance,
                    SortOption.RECENT to strings.sortRecent
                )
                items(sortOptions) { (option, label) ->
                    FilterChip(
                        selected = sortOption == option,
                        onClick = { viewModel.setSortOption(option) },
                        label = { Text(label, fontSize = 11.sp) },
                        leadingIcon = if (sortOption == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                        } else null,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredByAccount.isEmpty() && uiState.searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = if (accountFilter == "BIG") Icons.Default.MenuBook else if (accountFilter == "SMALL") Icons.Default.StickyNote2 else Icons.Default.MenuBook,
                        title = if (accountFilter != "All") "No ${if (accountFilter == "BIG") "big" else "small"} accounts" else strings.noCustomersYet,
                        subtitle = if (accountFilter != "All") "Add customers with ${if (accountFilter == "BIG") "Big Khata" else "Small Khata"} type" else strings.addFirstCustomer
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredByAccount, key = { it.id }) { customer ->
                        CustomerItem(
                            customer = customer,
                            onClick = { onCustomerClick(customer.id) },
                            onDeleteClick = { customerToDelete = customer.id }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddCustomerDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, phone, khataType ->
                    viewModel.addCustomer(name, phone, khataType)
                    showAddDialog = false
                }
            )
        }

        customerToDelete?.let { customerId ->
            ConfirmationDialog(
                title = strings.deleteCustomer,
                message = strings.deleteCustomerMessage,
                confirmText = strings.delete,
                onConfirm = {
                    viewModel.deleteCustomer(customerId)
                    customerToDelete = null
                },
                onDismiss = { customerToDelete = null }
            )
        }
    }
}

@Composable
private fun StatGridCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
