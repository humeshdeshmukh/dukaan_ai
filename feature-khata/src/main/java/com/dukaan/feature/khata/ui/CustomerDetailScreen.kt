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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Long,
    viewModel: KhataViewModel,
    shopName: String = "",
    onAddTransaction: (TransactionType) -> Unit,
    onStatementClick: () -> Unit = {},
    onShareReminder: (String) -> Unit = {},
    onBackClick: () -> Unit
) {
    val customer by viewModel.getCustomerFlow(customerId).collectAsState(initial = null)
    var selectedFilter by remember { mutableStateOf("All") }
    val transactions by viewModel.getTransactions(customerId).collectAsState(initial = emptyList())
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    var showEditDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Long?>(null) }
    var showAiChat by remember { mutableStateOf(false) }

    // AI States
    val customerInsight by viewModel.customerInsight.collectAsState()
    val isInsightLoading by viewModel.isInsightLoading.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isAiTyping by viewModel.isAiTyping.collectAsState()
    val reminderMessage by viewModel.reminderMessage.collectAsState()
    var showInsight by remember { mutableStateOf(false) }

    // Load AI insight when customer data is available
    LaunchedEffect(customer, transactions) {
        val c = customer ?: return@LaunchedEffect
        if (transactions.isNotEmpty() && customerInsight == null && !isInsightLoading) {
            viewModel.loadCustomerInsight(c.name, c.balance, transactions)
        }
    }

    // Handle reminder message
    LaunchedEffect(reminderMessage) {
        reminderMessage?.let {
            onShareReminder(it)
            viewModel.clearReminder()
        }
    }

    // Filter transactions by date
    val filteredTransactions = remember(transactions, selectedFilter) {
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
                    viewModel.sendChatMessage(c.name, c.balance, transactions, message)
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
                title = { Text(customer?.name ?: "Customer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onStatementClick) {
                        Icon(Icons.Default.Description, contentDescription = "Statement")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
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
                    color = if (c.balance >= 0) Color(0xFF00B37E).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (c.balance >= 0) "Total BAKI (to collect)" else "Total JAMA (to pay)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currencyFormat.format(Math.abs(c.balance)),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (c.balance >= 0) Color(0xFF00B37E) else Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = c.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Send Reminder button for customers who owe money
                        if (c.balance < 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.generateReminder(c.name, Math.abs(c.balance), shopName)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Reminder", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // AI Insight Card
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
                                "AI Insight",
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
                                        "Analyzing...",
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

            Spacer(modifier = Modifier.height(8.dp))

            // Date Filter Chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Week", "Month").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        leadingIcon = if (selectedFilter == filter) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Transactions (${filteredTransactions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        icon = Icons.Default.ReceiptLong,
                        title = "No Transactions",
                        subtitle = "Add a payment or credit to get started"
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
                    Text("YOU GOT", fontWeight = FontWeight.Bold)
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
                    Text("YOU GAVE", fontWeight = FontWeight.Bold)
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
                title = "Delete Transaction",
                message = "This will delete this transaction and reverse the balance. This cannot be undone.",
                confirmText = "Delete",
                onConfirm = {
                    viewModel.deleteTransaction(txnId)
                    transactionToDelete = null
                },
                onDismiss = { transactionToDelete = null }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && phone.isNotBlank()) onConfirm(name, phone) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    onDeleteClick: () -> Unit = {}
) {
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
                    text = if (isJama) "Payment Received" else "Credit Given",
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
