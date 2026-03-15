package com.dukaan.feature.khata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.ui.components.AddCustomerDialog
import com.dukaan.feature.khata.ui.components.CustomerItem
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    viewModel: KhataViewModel,
    onCustomerClick: (Long) -> Unit,
    onOverviewClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val customers by viewModel.filteredCustomers.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<Long?>(null) }
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

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
                },
                actions = {
                    IconButton(onClick = onOverviewClick) {
                        Icon(Icons.Outlined.Analytics, contentDescription = strings.khataOverview)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = strings.addCustomer)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Summary Card
            if (uiState.customerCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.TrendingDown, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currencyFormat.format(Math.abs(uiState.totalReceivable)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                            Text(strings.toCollect, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Color(0xFF00B37E), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currencyFormat.format(Math.abs(uiState.totalPayable)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00B37E)
                            )
                            Text(strings.toPay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.customerCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(strings.customers, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(strings.searchByNameOrPhone) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Chips
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
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (sortOption == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (customers.isEmpty() && uiState.searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.MenuBook,
                        title = strings.noCustomersYet,
                        subtitle = strings.addFirstCustomer
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
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
                onConfirm = { name, phone ->
                    viewModel.addCustomer(name, phone)
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
