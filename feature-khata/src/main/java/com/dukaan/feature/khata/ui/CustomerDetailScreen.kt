package com.dukaan.feature.khata.ui

import androidx.compose.animation.AnimatedVisibility
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
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.khata.ui.components.KhataAiChatSheet
import com.dukaan.feature.khata.ui.components.DateRangePickerDialog
import com.dukaan.core.ui.translation.LocalAppStrings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    viewModel: KhataViewModel,
    shopName: String = "",
    languageCode: String = "en",
    onAddTransaction: (TransactionType) -> Unit,
    onStatementClick: () -> Unit = {},
    onShareReminder: (String) -> Unit = {},
    onBackClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val customer by viewModel.getCustomerFlow(customerId).collectAsState(initial = null)
    var selectedFilter by remember { mutableStateOf("All") }
    val transactions by viewModel.getTransactions(customerId).collectAsState(initial = emptyList())
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    var showEditDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Long?>(null) }
    var showAiChat by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableLongStateOf(0L) }
    var customEndDate by remember { mutableLongStateOf(0L) }

    // AI States
    val customerInsight by viewModel.customerInsight.collectAsState()
    val isInsightLoading by viewModel.isInsightLoading.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isAiTyping by viewModel.isAiTyping.collectAsState()
    val reminderMessage by viewModel.reminderMessage.collectAsState()
    var showInsight by remember { mutableStateOf(false) }

    // Handle reminder message
    LaunchedEffect(reminderMessage) {
        reminderMessage?.let {
            onShareReminder(it)
            viewModel.clearReminder()
        }
    }

    // Filter transactions by date
    val filteredTransactions = remember(transactions, selectedFilter, customStartDate, customEndDate) {
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            "Week" -> {
                val weekAgo = now - 7 * 24 * 60 * 60 * 1000L
                transactions.filter { it.date >= weekAgo }
            }
            "Month" -> {
                val monthAgo = now - 30L * 24 * 60 * 60 * 1000L
                transactions.filter { it.date >= monthAgo }
            }
            "Custom" -> {
                if (customStartDate > 0 && customEndDate > 0) {
                    transactions.filter { it.date in customStartDate..customEndDate }
                } else transactions
            }
            else -> transactions
        }
    }

    if (showAiChat) {
        customer?.let { c ->
            KhataAiChatSheet(
                customerName = c.name,
                messages = chatMessages,
                isAiTyping = isAiTyping,
                onSendMessage = { message ->
                    viewModel.sendChatMessage(c.name, c.balance, transactions, message, languageCode)
                },
                onDismiss = {
                    showAiChat = false
                    viewModel.clearChat()
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: strings.customer, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = onStatementClick) {
                        Icon(Icons.Default.Description, contentDescription = strings.statement)
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = strings.edit)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiChat = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "AI Chat")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Balance Hero Card
            customer?.let { c ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (c.balance >= 0) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF00B37E).copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (c.balance >= 0) strings.totalBakiToCollect else strings.totalJamaToPay,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currencyFormat.format(Math.abs(c.balance)),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (c.balance >= 0) Color(0xFFEF4444) else Color(0xFF00B37E)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = c.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Send Reminder button for customers who owe money
                        if (c.balance > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.generateReminder(c.name, Math.abs(c.balance), shopName, languageCode)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.sendReminder, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // AI Insight Section
            customer?.let { c ->
                if (customerInsight == null && !isInsightLoading) {
                    OutlinedButton(
                        onClick = {
                            viewModel.loadCustomerInsight(c.name, c.balance, transactions, languageCode)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.getAiInsight)
                    }
                } else {
                    AnimatedVisibility(visible = customerInsight != null || isInsightLoading) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        strings.aiInsight,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { showInsight = !showInsight },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            if (showInsight) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                AnimatedVisibility(visible = showInsight || isInsightLoading) {
                                    if (isInsightLoading) {
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                strings.analyzing,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        customerInsight?.let { insight ->
                                            Text(
                                                text = insight,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date Filter Chips
            val filterLabels = mapOf(
                "All" to strings.all,
                "Week" to strings.week,
                "Month" to strings.month,
                "Custom" to strings.custom
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Week", "Month", "Custom").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            if (filter == "Custom") {
                                showDatePicker = true
                            } else {
                                selectedFilter = filter
                            }
                        },
                        label = { Text(filterLabels[filter] ?: filter) },
                        leadingIcon = if (selectedFilter == filter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Show selected date range for Custom filter
            if (selectedFilter == "Custom" && customStartDate > 0 && customEndDate > 0) {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                Text(
                    text = "${sdf.format(Date(customStartDate))} - ${sdf.format(Date(customEndDate))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${strings.transactions} (${filteredTransactions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.ReceiptLong,
                        title = strings.noTransactions,
                        subtitle = strings.addPaymentOrCredit
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            currencyFormat = currencyFormat,
                            onDeleteClick = { transactionToDelete = transaction.id }
                        )
                    }
                }
            }

            // Quick Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onAddTransaction(TransactionType.JAMA) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B37E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.youGot, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onAddTransaction(TransactionType.BAKI) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.youGave, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Edit Customer Dialog
        if (showEditDialog && customer != null) {
            EditCustomerDialog(
                currentName = customer!!.name,
                currentPhone = customer!!.phone,
                onDismiss = { showEditDialog = false },
                onConfirm = { name, phone ->
                    viewModel.updateCustomer(customerId, name, phone)
                    showEditDialog = false
                }
            )
        }

        // Delete Transaction Confirmation
        transactionToDelete?.let { txnId ->
            ConfirmationDialog(
                title = strings.deleteTransaction,
                message = strings.deleteTransactionMessage,
                confirmText = strings.delete,
                onConfirm = {
                    viewModel.deleteTransaction(txnId)
                    transactionToDelete = null
                },
                onDismiss = { transactionToDelete = null }
            )
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
}

@Composable
fun EditCustomerDialog(
    currentName: String,
    currentPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(currentPhone) }
    val strings = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editCustomer) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.customerNameLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phoneNumberLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && phone.isNotBlank()) onConfirm(name, phone) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    onDeleteClick: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val isJama = transaction.type == TransactionType.JAMA
    val tint = if (isJama) Color(0xFF00B37E) else Color(0xFFEF4444)

    Card(
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
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = if (isJama) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(8.dp)
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
                text = "${if (isJama) "+" else "-"}${currencyFormat.format(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint
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
