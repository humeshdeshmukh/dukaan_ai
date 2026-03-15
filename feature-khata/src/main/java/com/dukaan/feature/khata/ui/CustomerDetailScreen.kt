package com.dukaan.feature.khata.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.AppStrings
import com.dukaan.feature.khata.domain.model.Customer
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.khata.ui.components.KhataAiChatSheet
import com.dukaan.feature.khata.ui.components.DateRangePickerDialog
import com.dukaan.feature.khata.ui.components.getRelativeTime
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
    onShareReminder: (String, String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
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

    // Editable reminder dialog state
    var showReminderDialog by remember { mutableStateOf(false) }
    var editableReminderText by remember { mutableStateOf("") }
    var reminderCustomerPhone by remember { mutableStateOf("") }

    LaunchedEffect(reminderMessage) {
        reminderMessage?.let {
            editableReminderText = it
            reminderCustomerPhone = customer?.phone ?: ""
            showReminderDialog = true
            viewModel.clearReminder()
        }
    }

    // Reminder Edit Dialog
    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(strings.sendReminder, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (reminderCustomerPhone.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(reminderCustomerPhone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedTextField(
                        value = editableReminderText,
                        onValueChange = { editableReminderText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShareReminder(editableReminderText, reminderCustomerPhone)
                        showReminderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text(strings.cancel, fontSize = 13.sp)
                }
            }
        )
    }

    // Filter transactions
    val filteredTransactions = remember(transactions, selectedFilter, customStartDate, customEndDate) {
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            "Week" -> transactions.filter { it.date >= now - 7 * 24 * 60 * 60 * 1000L }
            "Month" -> transactions.filter { it.date >= now - 30L * 24 * 60 * 60 * 1000L }
            "Custom" -> if (customStartDate > 0 && customEndDate > 0) transactions.filter { it.date in customStartDate..customEndDate } else transactions
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
                onDismiss = { showAiChat = false; viewModel.clearChat() }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    customer?.let { c ->
                        Column {
                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    } ?: Text(strings.customer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back, modifier = Modifier.size(22.dp))
                    }
                },
                actions = {
                    // Quick Call
                    customer?.let { c ->
                        if (c.phone.isNotBlank()) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))
                                context.startActivity(intent)
                            }) {
                                Icon(Icons.Default.Phone, contentDescription = strings.call, modifier = Modifier.size(22.dp), tint = Color(0xFF00B37E))
                            }
                        }
                    }
                    IconButton(onClick = onStatementClick) {
                        Icon(Icons.Default.Description, contentDescription = strings.statement, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = strings.edit, modifier = Modifier.size(22.dp))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiChat = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = "AI Chat", modifier = Modifier.size(22.dp))
            }
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onAddTransaction(TransactionType.JAMA) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B37E)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.CallReceived, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.youGot, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onAddTransaction(TransactionType.BAKI) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.CallMade, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.youGave, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            // Balance Card
            customer?.let { c ->
                val balanceColor = if (c.balance >= 0) Color(0xFFEF4444) else Color(0xFF00B37E)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = balanceColor.copy(alpha = 0.07f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (c.balance >= 0) strings.totalBakiToCollect else strings.totalJamaToPay,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    currencyFormat.format(Math.abs(c.balance)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = balanceColor
                                )
                            }
                            if (c.balance > 0) {
                                FilledTonalButton(
                                    onClick = { viewModel.generateReminder(c.name, Math.abs(c.balance), shopName, languageCode) },
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.sendReminder, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Quick Stats Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Receipt,
                            label = strings.totalTransactions,
                            value = "${transactions.size}"
                        )
                        QuickStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            label = strings.lastActivity,
                            value = getRelativeTime(c.lastActivityAt, strings)
                        )
                        QuickStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TrendingUp,
                            label = strings.avgTransaction,
                            value = if (transactions.isNotEmpty()) currencyFormat.format(transactions.map { it.amount }.average()) else "—"
                        )
                    }
                }
            }

            // AI Insight
            customer?.let { c ->
                item {
                    if (customerInsight == null && !isInsightLoading) {
                        TextButton(
                            onClick = { viewModel.loadCustomerInsight(c.name, c.balance, transactions, languageCode) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(strings.getAiInsight, fontSize = 13.sp)
                        }
                    } else {
                        AnimatedVisibility(visible = customerInsight != null || isInsightLoading) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.SmartToy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.aiInsight, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = { showInsight = !showInsight }, modifier = Modifier.size(24.dp)) {
                                            Icon(
                                                if (showInsight) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                "Toggle", Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    AnimatedVisibility(visible = showInsight || isInsightLoading) {
                                        if (isInsightLoading) {
                                            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text(strings.analyzing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        } else {
                                            customerInsight?.let {
                                                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp), lineHeight = 18.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Date Filter Chips
            item {
                val filterLabels = mapOf("All" to strings.all, "Week" to strings.week, "Month" to strings.month, "Custom" to strings.custom)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Week", "Month", "Custom").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { if (filter == "Custom") showDatePicker = true else selectedFilter = filter },
                            label = { Text(filterLabels[filter] ?: filter, fontSize = 12.sp) },
                            leadingIcon = if (selectedFilter == filter) {
                                { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
                if (selectedFilter == "Custom" && customStartDate > 0 && customEndDate > 0) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    Text(
                        "${sdf.format(Date(customStartDate))} - ${sdf.format(Date(customEndDate))}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            // Transaction Table Header
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.date, modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.type, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.amount, modifier = Modifier.weight(1.2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.notes, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(30.dp)) // delete icon space
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        EmptyStateView(
                            icon = Icons.Default.ReceiptLong,
                            title = strings.noTransactions,
                            subtitle = strings.addPaymentOrCredit
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    TransactionTableRow(
                        transaction = transaction,
                        currencyFormat = currencyFormat,
                        onDeleteClick = { transactionToDelete = transaction.id },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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

        transactionToDelete?.let { txnId ->
            ConfirmationDialog(
                title = strings.deleteTransaction,
                message = strings.deleteTransactionMessage,
                confirmText = strings.delete,
                onConfirm = { viewModel.deleteTransaction(txnId); transactionToDelete = null },
                onDismiss = { transactionToDelete = null }
            )
        }

        if (showDatePicker) {
            DateRangePickerDialog(
                onDismiss = { showDatePicker = false },
                onConfirm = { start, end -> customStartDate = start; customEndDate = end; selectedFilter = "Custom"; showDatePicker = false }
            )
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun TransactionTableRow(
    transaction: Transaction,
    currencyFormat: NumberFormat,
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val isJama = transaction.type == TransactionType.JAMA
    val tint = if (isJama) Color(0xFF00B37E) else Color(0xFFEF4444)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isJama) Color(0xFF00B37E).copy(alpha = 0.03f) else Color(0xFFEF4444).copy(alpha = 0.03f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date
            Text(
                dateFormat.format(Date(transaction.date)),
                modifier = Modifier.weight(1.2f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Type with icon
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.15f)
                ) {
                    Icon(
                        if (isJama) Icons.Default.CallReceived else Icons.Default.CallMade,
                        null, tint = tint, modifier = Modifier.padding(3.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isJama) strings.jama else strings.baki,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = tint
                )
            }
            // Amount
            Text(
                "${if (isJama) "+" else "-"}${currencyFormat.format(transaction.amount)}",
                modifier = Modifier.weight(1.2f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            // Notes
            Text(
                transaction.notes ?: "—",
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Delete
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, strings.delete, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
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
        title = { Text(strings.editCustomer, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.customerNameLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phoneNumberLabel) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && phone.isNotBlank()) onConfirm(name, phone) }, enabled = name.isNotBlank() && phone.isNotBlank()) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}
