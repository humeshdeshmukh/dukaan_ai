package com.dukaan.feature.ocr.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.ui.components.LargeActionButton
import com.dukaan.core.ui.translation.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultScreen(
    state: OcrUiState,
    existingSellerNames: List<String> = emptyList(),
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onNavigateAfterSave: () -> Unit = {},
    onDeleteItem: (BillItem) -> Unit,
    onEditItem: (Int, BillItem) -> Unit,
    onAddItem: (BillItem) -> Unit,
    onSellerNameChanged: (String) -> Unit,
    onSellerPhoneChanged: (String) -> Unit = {},
    onSendChatMessage: (String) -> Unit = {},
    onUseCalculatedSubtotal: () -> Unit = {},
    onDismissSubtotalMismatch: () -> Unit = {},
    onDiscountPercentChanged: (Double) -> Unit = {},
    onTaxPercentChanged: (Double) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingItem by remember { mutableStateOf<BillItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }
    var hasNavigatedAfterSave by remember { mutableStateOf(false) }
    
    var sellerName by remember(state.scannedBill) {
        mutableStateOf(state.scannedBill?.sellerName ?: "")
    }
    var showSuggestions by remember { mutableStateOf(false) }
    var showFullSearchDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    // Show save success feedback then navigate (with guard against double-fire)
    LaunchedEffect(state.isSaved) {
        if (state.isSaved && !hasNavigatedAfterSave) {
            hasNavigatedAfterSave = true
            Toast.makeText(context, "Bill saved!", Toast.LENGTH_SHORT).show()
            onNavigateAfterSave()
        }
    }

    // Show save error
    LaunchedEffect(state.saveError) {
        state.saveError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.editingBillId != null) strings.editPurchaseBill else strings.verifyScannedBill)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        },
        bottomBar = {
            if (!state.isSaved) {
                Box(modifier = Modifier.padding(16.dp)) {
                    LargeActionButton(
                        icon = if (state.isSaving) Icons.Default.HourglassTop else Icons.Default.Check,
                        label = if (state.isSaving) strings.saving else if (state.editingBillId != null) strings.updateBill else strings.confirmAndSaveBill,
                        onClick = onSaveClick,
                        enabled = !state.isSaving && (state.scannedBill?.items?.isNotEmpty() == true),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            strings.aiIsReadingBill,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (state.scannedBill != null) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    // Bill photo thumbnail(s) — tap to open fullscreen viewer
                    item {
                        if (state.scannedPageUris.size > 1) {
                            MultiPageThumbnailStrip(
                                pageUris = state.scannedPageUris,
                                onPageClick = { index ->
                                    showImageViewer = true
                                }
                            )
                        } else {
                            BillImageThumbnail(
                                imagePath = state.capturedImageUri,
                                onClick = {
                                    if (state.capturedImageUri != null) {
                                        showImageViewer = true
                                    }
                                }
                            )
                        }
                    }

                    item {
                        WholesalerPickerRow(
                            selectedName = sellerName,
                            selectedPhone = state.scannedBill.sellerPhone,
                            onClick = { showFullSearchDialog = true }
                        )
                    }

                    // Bill number if present
                    if (state.scannedBill.billNumber.isNotBlank()) {
                        item {
                            Text(
                                text = "Bill No: ${state.scannedBill.billNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Subtotal mismatch warning banner
                    if (state.subtotalMismatch != null && state.subtotalMismatch.isSignificant) {
                        item {
                            SubtotalMismatchBanner(
                                mismatch = state.subtotalMismatch,
                                onUseCalculated = onUseCalculatedSubtotal,
                                onDismiss = onDismissSubtotalMismatch
                            )
                        }
                    }

                    // Items header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Items (${state.scannedBill.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Item cards
                    itemsIndexed(state.scannedBill.items) { index, item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Show rate × qty breakdown
                                    val unitPrice = item.effectiveUnitPrice
                                    val rateDisplay = if (unitPrice > 0 && item.quantity > 0) {
                                        "${item.quantity} ${item.unit} × ₹${"%.2f".format(unitPrice)}/${item.unit}"
                                    } else {
                                        "${item.quantity} ${item.unit}"
                                    }
                                    Text(
                                        text = rateDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // Show item discount if present
                                    if (item.itemDiscountAmount > 0 || item.itemDiscountPercent > 0) {
                                        val discountText = if (item.itemDiscountPercent > 0) {
                                            "Discount: ${item.itemDiscountPercent}% (-₹${"%.2f".format(item.grossTotal - item.total)})"
                                        } else {
                                            "Discount: -₹${"%.2f".format(item.itemDiscountAmount)}"
                                        }
                                        Text(
                                            text = discountText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Text(
                                    text = "₹${"%.2f".format(item.total)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    editingIndex = index
                                    editingItem = item
                                    showEditDialog = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = strings.edit,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteItem(item) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = strings.delete,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Add missing item button
                    item {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.addMissingItem)
                        }
                    }
                }

                // Total amount / breakdown card
                val bill = state.scannedBill!!
                val hasBreakdown = bill.discountAmount > 0 || bill.taxAmount > 0 || bill.discountPercent > 0 || bill.taxPercent > 0
                var showEditBreakdown by remember { mutableStateOf(false) }

                // Calculate consistent values based on items sum
                val itemsSubtotal = bill.items.sumOf { it.total }
                val calculatedDiscountAmount = if (bill.discountPercent > 0) itemsSubtotal * bill.discountPercent / 100.0 else bill.discountAmount
                val afterDiscount = (itemsSubtotal - calculatedDiscountAmount).coerceAtLeast(0.0)
                val calculatedTaxAmount = if (bill.taxPercent > 0) afterDiscount * bill.taxPercent / 100.0 else bill.taxAmount
                val calculatedTotal = afterDiscount + calculatedTaxAmount

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        // Subtotal row (always show)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Subtotal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                "₹${"%.2f".format(itemsSubtotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        // Editable discount row
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Discount",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (calculatedDiscountAmount > 0) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                                if (!showEditBreakdown) {
                                    if (bill.discountPercent > 0) {
                                        Text(
                                            " (${bill.discountPercent}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            if (calculatedDiscountAmount > 0) {
                                Text(
                                    "- ₹${"%.2f".format(calculatedDiscountAmount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    "₹0.00",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Editable GST row
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "GST",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                if (!showEditBreakdown) {
                                    if (bill.taxPercent > 0) {
                                        Text(
                                            " (${bill.taxPercent}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            if (calculatedTaxAmount > 0) {
                                Text(
                                    "+ ₹${"%.2f".format(calculatedTaxAmount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            } else {
                                Text(
                                    "₹0.00",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Edit discount/tax button
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { showEditBreakdown = !showEditBreakdown },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                if (showEditBreakdown) Icons.Default.ExpandLess else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showEditBreakdown) "Hide" else "Edit Discount/GST")
                        }

                        // Expandable edit fields
                        if (showEditBreakdown) {
                            var discountPercentText by remember(bill.discountPercent) {
                                mutableStateOf(if (bill.discountPercent > 0) "%.2f".format(bill.discountPercent).trimEnd('0').trimEnd('.') else "")
                            }
                            var taxPercentText by remember(bill.taxPercent) {
                                mutableStateOf(if (bill.taxPercent > 0) bill.taxPercent.toString() else "")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = discountPercentText,
                                    onValueChange = {
                                        discountPercentText = it
                                        it.toDoubleOrNull()?.let { percent -> onDiscountPercentChanged(percent) }
                                    },
                                    label = { Text(strings.discountPercent) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                                OutlinedTextField(
                                    value = taxPercentText,
                                    onValueChange = {
                                        taxPercentText = it
                                        it.toDoubleOrNull()?.let { percent -> onTaxPercentChanged(percent) }
                                    },
                                    label = { Text(strings.gstPercent) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                        )

                        // Final total row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(strings.totalAmount, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "₹${"%.2f".format(calculatedTotal)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // AI Assistant button (between total and save)
                if (!state.isSaved) {
                    OutlinedButton(
                        onClick = { showAiChat = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.aiAssistant)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(strings.noBillDataFound)
                    }
                }
            }
        }
    }

    // Fullscreen zoomable image viewer
    if (showImageViewer && state.capturedImageUri != null) {
        ZoomableImageViewer(
            imagePath = state.capturedImageUri,
            onDismiss = { showImageViewer = false }
        )
    }

    // AI Chat bottom sheet
    if (showAiChat) {
        ModalBottomSheet(
            onDismissRequest = { showAiChat = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            BillAiChatSheet(
                messages = state.chatMessages,
                isAiTyping = state.isAiTyping,
                onSendMessage = onSendChatMessage,
                onDismiss = { showAiChat = false }
            )
        }
    }

    // Edit item dialog
    if (showEditDialog && editingItem != null) {
        EditItemDialog(
            item = editingItem!!,
            onConfirm = { updatedItem ->
                onEditItem(editingIndex, updatedItem)
                showEditDialog = false
                editingItem = null
            },
            onDismiss = {
                showEditDialog = false
                editingItem = null
            }
        )
    }

    // Add item dialog
    if (showAddDialog) {
        EditItemDialog(
            item = BillItem(name = "", quantity = 1.0, unit = "pc", price = 0.0),
            isAddMode = true,
            onConfirm = { newItem ->
                onAddItem(newItem)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showFullSearchDialog) {
        WholesalerSelectDialog(
            sellers = existingSellerNames,
            onSelect = { name ->
                sellerName = name
                onSellerNameChanged(name)
                showFullSearchDialog = false
            },
            onNewWholesaler = { name, phone ->
                sellerName = name
                onSellerNameChanged(name)
                onSellerPhoneChanged(phone)
                showFullSearchDialog = false
            },
            onDismiss = { showFullSearchDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WholesalerSelectDialog(
    sellers: List<String>,
    onSelect: (String) -> Unit,
    onNewWholesaler: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showNewForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    val strings = LocalAppStrings.current

    val filtered = remember(query, sellers) {
        if (query.isBlank()) sellers else sellers.filter { it.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showNewForm) "Add New Wholesaler" else "Search Wholesaler") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (showNewForm) {
                    // New Wholesaler Form
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(strings.wholesalerSellerName) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Store, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text(strings.phoneNumber) },
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
                                    onNewWholesaler(newName.trim(), newPhone.trim())
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = newName.isNotBlank()
                        ) { Text(strings.save) }
                    }
                } else {
                    // Search + List
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(strings.searchName) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Add New Wholesaler Button
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
                            Text(strings.addNewWholesaler, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(strings.noMatchingWholesalers, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filtered) { name ->
                                Card(
                                    onClick = { onSelect(name) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(name, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { if (!showNewForm) TextButton(onClick = onDismiss) { Text(strings.cancel) } }
    )
}

@Composable
private fun BillImageThumbnail(imagePath: String?, onClick: () -> Unit = {}) {
    if (imagePath == null) return
    val strings = LocalAppStrings.current

    val bitmap = remember(imagePath) {
        try {
            BitmapFactory.decodeFile(imagePath)
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured bill photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.tapToViewFullImage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Zoom",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemDialog(
    item: BillItem,
    isAddMode: Boolean = false,
    onConfirm: (BillItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.toString()) }
    var unit by remember { mutableStateOf(item.unit) }
    var unitPrice by remember { mutableStateOf(
        if (item.effectiveUnitPrice > 0) item.effectiveUnitPrice.toString() else ""
    ) }
    var itemDiscountPercent by remember { mutableStateOf(
        if (item.itemDiscountPercent > 0) item.itemDiscountPercent.toString() else ""
    ) }
    val strings = LocalAppStrings.current

    // Calculate line total from rate × qty - discount
    val calculatedTotal = remember(quantity, unitPrice, itemDiscountPercent) {
        val qty = quantity.toDoubleOrNull() ?: 0.0
        val rate = unitPrice.toDoubleOrNull() ?: 0.0
        val discPercent = itemDiscountPercent.toDoubleOrNull() ?: 0.0
        val gross = qty * rate
        val discountAmount = gross * discPercent / 100.0
        (gross - discountAmount).coerceAtLeast(0.0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAddMode) strings.addItem else strings.editItem) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.itemNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(strings.qtyLabel) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(strings.unitLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                // Unit price (rate) field
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it },
                    label = { Text(strings.ratePerUnit.replace("%s", unit.ifBlank { "unit" })) },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Item discount field
                OutlinedTextField(
                    value = itemDiscountPercent,
                    onValueChange = { itemDiscountPercent = it },
                    label = { Text(strings.itemDiscountOptional) },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Calculated line total
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strings.lineTotal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "₹${"%.2f".format(calculatedTotal)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: item.quantity
                    val rate = unitPrice.toDoubleOrNull() ?: 0.0
                    val discPercent = itemDiscountPercent.toDoubleOrNull() ?: 0.0
                    val gross = qty * rate
                    val discAmount = gross * discPercent / 100.0
                    val finalPrice = (gross - discAmount).coerceAtLeast(0.0)

                    onConfirm(
                        BillItem(
                            name = name.ifBlank { "Item" },
                            quantity = qty,
                            unit = unit.ifBlank { "pc" },
                            price = finalPrice,
                            unitPrice = rate,
                            itemDiscountPercent = discPercent,
                            itemDiscountAmount = discAmount
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (isAddMode) strings.add else strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

/**
 * Warning banner shown when items sum doesn't match extracted subtotal.
 */
@Composable
private fun SubtotalMismatchBanner(
    mismatch: SubtotalMismatch,
    onUseCalculated: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    strings.subtotalMismatch,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${strings.itemsTotal}:", color = MaterialTheme.colorScheme.onErrorContainer)
                Text("₹${"%.2f".format(mismatch.calculatedItemsSum)}", fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${strings.billSubtotal}:", color = MaterialTheme.colorScheme.onErrorContainer)
                Text("₹${"%.2f".format(mismatch.extractedSubtotal)}", fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${strings.difference}:", color = MaterialTheme.colorScheme.error)
                Text(
                    "₹${"%.2f".format(kotlin.math.abs(mismatch.difference))}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                strings.itemsMayBeMissing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(strings.ignore)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onUseCalculated,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(strings.useItemsTotal)
                }
            }
        }
    }
}

@Composable
private fun WholesalerPickerRow(
    selectedName: String,
    selectedPhone: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Store,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedName.ifBlank { "Select Wholesaler" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (selectedPhone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MultiPageThumbnailStrip(
    pageUris: List<String>,
    onPageClick: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "${pageUris.size} pages scanned",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(pageUris) { index, uri ->
                val bitmap = remember(uri) {
                    try {
                        BitmapFactory.decodeFile(uri)
                    } catch (_: Exception) {
                        null
                    }
                }
                Card(
                    modifier = Modifier
                        .width(120.dp)
                        .height(160.dp)
                        .clickable { onPageClick(index) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Page label at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Page ${index + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
