package com.dukaan.feature.billing.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dukaan.core.db.entity.CustomerEntity
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.ui.translation.LocalAppStrings
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBillingScreen(
    viewModel: BillingViewModel,
    onBackClick: (() -> Unit)?,
    onShareClick: (String) -> Unit,
    onShareToPhone: (String, String) -> Unit,
    onBillClick: (Long) -> Unit,
    onGeneratePdf: (Bill) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val allBills by viewModel.allBills.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Runtime permission for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording()
        }
    }

    val onMicClick: () -> Unit = {
        if (uiState.isRecording) {
            viewModel.toggleRecording()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.toggleRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(strings.navVoiceBill, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        onBackClick?.let { click ->
                            IconButton(onClick = click) {
                                Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                            }
                        } ?: run {}
                    }
                )
                // Tabs - New Bill / History
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text(strings.newBill, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text(strings.billHistory, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Outlined.Receipt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.selectedTab == 0) {
                ActionButtonsRow(
                    hasItems = uiState.items.isNotEmpty(),
                    onSaveDraft = { viewModel.saveBill(asDraft = true) },
                    onWhatsApp = {
                        val msg = viewModel.formatWhatsAppMessage()
                        val phone = uiState.selectedCustomerPhone
                        viewModel.saveBill()
                        if (phone.isNotBlank()) {
                            onShareToPhone(msg, phone)
                        } else {
                            onShareClick(msg)
                        }
                    },
                    onSavePdf = {
                        val bill = viewModel.buildBillForPdf()
                        viewModel.saveBill()
                        onGeneratePdf(bill)
                    }
                )
            }
        }
    ) { padding ->
        when (uiState.selectedTab) {
            0 -> NewBillTab(
                uiState = uiState,
                currencyFormat = currencyFormat,
                viewModel = viewModel,
                onMicClick = onMicClick,
                onShowCustomerPicker = { showCustomerPicker = true },
                onShowAddItem = { showAddItemDialog = true },
                onShowClearConfirm = { showClearConfirm = true },
                modifier = Modifier.padding(padding)
            )
            1 -> HistoryTab(
                bills = allBills,
                currencyFormat = currencyFormat,
                onBillClick = onBillClick,
                onDeleteBill = viewModel::deleteBill,
                modifier = Modifier.padding(padding)
            )
        }
    }

    // Edit Item Dialog
    if (uiState.editingItemIndex >= 0 && uiState.editingItemIndex < uiState.items.size) {
        EditItemDialog(
            item = uiState.items[uiState.editingItemIndex],
            onDismiss = { viewModel.setEditingItem(-1) },
            onSave = { updated -> viewModel.updateItem(uiState.editingItemIndex, updated) }
        )
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        EditItemDialog(
            item = null,
            onDismiss = { showAddItemDialog = false },
            onSave = { item ->
                viewModel.addItemManually(item.name, item.quantity, item.unit, item.price)
                showAddItemDialog = false
            }
        )
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = uiState.customers,
            onSelect = { customer ->
                viewModel.selectCustomer(customer.id, customer.name, customer.phone)
                showCustomerPicker = false
            },
            onNewCustomer = { name, phone ->
                viewModel.selectCustomer(null, name, phone)
                showCustomerPicker = false
            },
            onClear = {
                viewModel.clearCustomer()
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    // Clear Confirmation
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(strings.clearAll) },
            text = { Text(strings.clearBillConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearBill()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text(strings.delete) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text(strings.cancel) } }
        )
    }
}

// ─── New Bill Tab ────────────────────────────────────────────────────────────

@Composable
private fun NewBillTab(
    uiState: BillingUiState,
    currencyFormat: NumberFormat,
    viewModel: BillingViewModel,
    onMicClick: () -> Unit,
    onShowCustomerPicker: () -> Unit,
    onShowAddItem: () -> Unit,
    onShowClearConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Customer Picker Row
        item {
            CustomerPickerRow(
                selectedName = uiState.selectedCustomerName,
                selectedPhone = uiState.selectedCustomerPhone,
                onClick = onShowCustomerPicker
            )
        }

        // Compact Voice Input
        item {
            CompactVoiceInput(
                isRecording = uiState.isRecording,
                isParsing = uiState.isParsing,
                recognizedText = uiState.recognizedText,
                onToggleRecording = onMicClick
            )
        }

        // Error message
        if (uiState.error != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uiState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Items Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${strings.billItems} (${uiState.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onShowAddItem) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addItem)
                    }
                    if (uiState.items.isNotEmpty()) {
                        TextButton(
                            onClick = onShowClearConfirm,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Text(strings.clearAll)
                        }
                    }
                }
            }
        }

        // Items or Empty
        if (uiState.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strings.noItemsAddedHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        } else {
            itemsIndexed(uiState.items) { index, item ->
                BillItemCard(
                    item = item,
                    currencyFormat = currencyFormat,
                    onEdit = { viewModel.setEditingItem(index) },
                    onDelete = { viewModel.removeItem(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Bill Summary
        if (uiState.items.isNotEmpty()) {
            item {
                BillSummaryCard(
                    subtotal = uiState.subtotal,
                    discountPercent = uiState.discountPercent,
                    discountAmount = uiState.discountAmount,
                    taxPercent = uiState.taxPercent,
                    taxAmount = uiState.taxAmount,
                    grandTotal = uiState.grandTotal,
                    currencyFormat = currencyFormat,
                    onDiscountChange = { viewModel.setDiscount(it) },
                    onTaxChange = { viewModel.setTax(it) }
                )
            }

            // Payment Mode
            item {
                PaymentModeChips(
                    selected = uiState.paymentMode,
                    onSelect = viewModel::setPaymentMode
                )
            }

            // Notes Field
            item {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(strings.addNote) },
                    leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ─── History Tab ─────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    bills: List<Bill>,
    currencyFormat: NumberFormat,
    onBillClick: (Long) -> Unit,
    onDeleteBill: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val todayFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val calendar = remember { Calendar.getInstance() }
    var deleteConfirmBillId by remember { mutableStateOf<Long?>(null) }

    // Group bills by date
    val todayStart = remember {
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todayBills = remember(bills) { bills.filter { it.timestamp >= todayStart } }
    val olderBills = remember(bills) { bills.filter { it.timestamp < todayStart } }
    val todayTotal = remember(todayBills) { todayBills.sumOf { it.totalAmount } }

    if (bills.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    strings.noBillsYet,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Today's Summary Card
            if (todayBills.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Today, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.todayBills, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${todayBills.size} ${strings.items}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                currencyFormat.format(todayTotal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Today's Bills
            if (todayBills.isNotEmpty()) {
                item {
                    Text(
                        strings.todayBills,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(todayBills, key = { it.id }) { bill ->
                    BillHistoryItem(
                        bill = bill,
                        currencyFormat = currencyFormat,
                        timeFormat = todayFormat,
                        showDate = false,
                        onClick = { onBillClick(bill.id) },
                        onDelete = { deleteConfirmBillId = bill.id }
                    )
                }
            }

            // Older Bills
            if (olderBills.isNotEmpty()) {
                item {
                    Text(
                        strings.allBills,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(olderBills, key = { it.id }) { bill ->
                    BillHistoryItem(
                        bill = bill,
                        currencyFormat = currencyFormat,
                        timeFormat = dateFormat,
                        showDate = true,
                        onClick = { onBillClick(bill.id) },
                        onDelete = { deleteConfirmBillId = bill.id }
                    )
                }
            }
        }
    }

    // Delete Confirmation
    deleteConfirmBillId?.let { billId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmBillId = null },
            title = { Text(strings.deleteBill) },
            text = { Text(strings.deleteBillMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBill(billId)
                        deleteConfirmBillId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text(strings.delete) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmBillId = null }) { Text(strings.cancel) } }
        )
    }
}

@Composable
private fun BillHistoryItem(
    bill: Bill,
    currencyFormat: NumberFormat,
    timeFormat: SimpleDateFormat,
    showDate: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (bill.isDraft) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (bill.isDraft) Icons.Outlined.Edit else Icons.Outlined.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (bill.isDraft) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (bill.customerName.isNotEmpty()) bill.customerName
                        else "${bill.items.size} ${strings.items}",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (bill.isDraft) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                strings.saveDraft,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeFormat.format(Date(bill.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bill.paymentMode.isNotEmpty()) {
                        Text(
                            " · ${bill.paymentMode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    currencyFormat.format(bill.totalAmount),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Customer Picker Row ────────────────────────────────────────────────────

@Composable
private fun CustomerPickerRow(selectedName: String, selectedPhone: String, onClick: () -> Unit) {
    val strings = LocalAppStrings.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedName.ifEmpty { strings.selectCustomer },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedName.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selectedName.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                )
                if (selectedPhone.isNotBlank()) {
                    Text(
                        text = selectedPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Compact Voice Input ────────────────────────────────────────────────────

@Composable
private fun CompactVoiceInput(
    isRecording: Boolean,
    isParsing: Boolean,
    recognizedText: String,
    onToggleRecording: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isParsing) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(strings.aiIsParsing, style = MaterialTheme.typography.bodyLarge)
            } else {
                RecordingButton(isRecording = isRecording, onClick = onToggleRecording)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRecording) strings.listening else strings.tapToSpeakItems,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    if (recognizedText.isNotEmpty()) {
                        Text(
                            text = "\"$recognizedText\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingButton(isRecording: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val color by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "color"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = Modifier
            .size(56.dp)
            .scale(if (isRecording) scale else 1f),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─── Bill Item Card ─────────────────────────────────────────────────────────

@Composable
private fun BillItemCard(
    item: BillItem,
    currencyFormat: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                val priceLabel = if (item.priceUnit.isNotBlank() && !item.priceUnit.equals(item.unit, ignoreCase = true)) {
                    "${item.quantity} ${item.unit} @ ${currencyFormat.format(item.price)}/${item.priceUnit}"
                } else {
                    "${item.quantity} ${item.unit} x ${currencyFormat.format(item.price)}"
                }
                Text(
                    priceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                currencyFormat.format(item.total),
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.Red.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Edit / Add Item Dialog ─────────────────────────────────────────────────

@Composable
private fun EditItemDialog(
    item: BillItem?,
    onDismiss: () -> Unit,
    onSave: (BillItem) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf(item?.name ?: "") }
    var qty by remember { mutableStateOf(item?.quantity?.toString() ?: "") }
    var unit by remember { mutableStateOf(item?.unit ?: "pc") }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var priceUnit by remember { mutableStateOf(item?.priceUnit ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item != null) strings.editItem else strings.addItem) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.itemName) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text(strings.quantity) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(strings.unitLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text(strings.pricePerUnit) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Text("₹", style = MaterialTheme.typography.bodyLarge) }
                    )
                    OutlinedTextField(
                        value = priceUnit,
                        onValueChange = { priceUnit = it },
                        label = { Text("Per") },
                        modifier = Modifier.weight(0.6f),
                        singleLine = true,
                        placeholder = { Text(unit.ifBlank { "kg" }, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    val p = price.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && q > 0) {
                        onSave(BillItem(
                            name = name.trim(),
                            quantity = q,
                            unit = unit.trim().ifEmpty { "pc" },
                            price = p,
                            priceUnit = priceUnit.trim().ifBlank { unit.trim().ifEmpty { "pc" } }
                        ))
                    }
                },
                enabled = name.isNotBlank() && (qty.toDoubleOrNull() ?: 0.0) > 0
            ) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

// ─── Bill Summary Card ──────────────────────────────────────────────────────

@Composable
private fun BillSummaryCard(
    subtotal: Double,
    discountPercent: Double,
    discountAmount: Double,
    taxPercent: Double,
    taxAmount: Double,
    grandTotal: Double,
    currencyFormat: NumberFormat,
    onDiscountChange: (Double) -> Unit,
    onTaxChange: (Double) -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(label = strings.subtotal, value = currencyFormat.format(subtotal))
            Spacer(Modifier.height(8.dp))
            SummaryRowWithInput(
                label = strings.discount,
                percent = discountPercent,
                amount = "-${currencyFormat.format(discountAmount)}",
                onPercentChange = onDiscountChange,
                amountColor = Color(0xFF00B37E)
            )
            Spacer(Modifier.height(8.dp))
            SummaryRowWithInput(
                label = strings.taxGst,
                percent = taxPercent,
                amount = "+${currencyFormat.format(taxAmount)}",
                onPercentChange = onTaxChange,
                amountColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    strings.grandTotal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    currencyFormat.format(grandTotal),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SummaryRowWithInput(
    label: String,
    percent: Double,
    amount: String,
    onPercentChange: (Double) -> Unit,
    amountColor: Color
) {
    var text by remember(percent) {
        mutableStateOf(if (percent == 0.0) "" else percent.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(52.dp).height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onPercentChange(it.toDoubleOrNull() ?: 0.0)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                    Text("%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = amountColor)
    }
}

// ─── Payment Mode Chips ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModeChips(selected: String, onSelect: (String) -> Unit) {
    val strings = LocalAppStrings.current
    val modes = listOf("CASH" to strings.cash, "UPI" to strings.upi, "CREDIT" to strings.credit)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { (code, label) ->
            FilterChip(
                selected = selected == code,
                onClick = { onSelect(code) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (selected == code) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

// ─── Action Buttons Row ─────────────────────────────────────────────────────

@Composable
private fun ActionButtonsRow(
    hasItems: Boolean,
    onSaveDraft: () -> Unit,
    onWhatsApp: () -> Unit,
    onSavePdf: () -> Unit
) {
    val strings = LocalAppStrings.current
    if (!hasItems) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSaveDraft,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.saveDraft, style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onWhatsApp,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(strings.whatsApp, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Button(
                onClick = onSavePdf,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.savePdf, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─── Customer Picker Dialog ─────────────────────────────────────────────────

@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    onSelect: (CustomerEntity) -> Unit,
    onNewCustomer: (String, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var search by remember { mutableStateOf("") }
    var showNewForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val filtered = remember(search, customers) {
        if (search.isBlank()) customers
        else customers.filter {
            it.name.contains(search, ignoreCase = true) || it.phone.contains(search)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectCustomer) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (showNewForm) {
                    // New Customer Form
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(strings.customerNameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text(strings.phoneNumberLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNewForm = false },
                            modifier = Modifier.weight(1f)
                        ) { Text(strings.back) }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onNewCustomer(newName.trim(), newPhone.trim())
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = newName.isNotBlank()
                        ) { Text(strings.save) }
                    }
                } else {
                    // Search + List
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(strings.searchCustomers) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Add New Customer Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNewForm = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(strings.addCustomer, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(strings.noCustomersFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filtered) { cust ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(cust) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    cust.name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Medium)
                                            if (cust.phone.isNotBlank()) {
                                                Text(
                                                    cust.phone,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showNewForm) {
                TextButton(onClick = onClear) { Text(strings.clearAll) }
            }
        },
        dismissButton = {
            if (!showNewForm) {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
            }
        }
    )
}
