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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    onSendChatMessage: (String) -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingItem by remember { mutableStateOf<BillItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }
    var hasNavigatedAfterSave by remember { mutableStateOf(false) }
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

                    // Seller name field + existing wholesaler suggestions
                    item {
                        var sellerName by remember(state.scannedBill) {
                            mutableStateOf(state.scannedBill.sellerName)
                        }
                        OutlinedTextField(
                            value = sellerName,
                            onValueChange = {
                                sellerName = it
                                onSellerNameChanged(it)
                            },
                            label = { Text(strings.wholesalerSellerName) },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Existing wholesaler suggestions
                        if (existingSellerNames.isNotEmpty()) {
                            Text(
                                text = strings.selectExistingWholesaler,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(existingSellerNames) { name ->
                                    FilterChip(
                                        selected = sellerName == name,
                                        onClick = {
                                            sellerName = name
                                            onSellerNameChanged(name)
                                        },
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
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
                                    Text(
                                        text = "${item.quantity} ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                // Total amount card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.totalAmount, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "₹${"%.2f".format(state.scannedBill.totalAmount)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
    var price by remember { mutableStateOf(item.price.toString()) }
    val strings = LocalAppStrings.current

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
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(strings.priceLabel) },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        BillItem(
                            name = name.ifBlank { "Item" },
                            quantity = quantity.toDoubleOrNull() ?: item.quantity,
                            unit = unit.ifBlank { "pc" },
                            price = price.toDoubleOrNull() ?: item.price
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
