package com.dukaan.feature.ocr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.ui.components.LargeActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultScreen(
    state: OcrUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteItem: (BillItem) -> Unit,
    onEditItem: (Int, BillItem) -> Unit,
    onAddItem: (BillItem) -> Unit,
    onSellerNameChanged: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editingItem by remember { mutableStateOf<BillItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Scanned Bill") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                LargeActionButton(
                    icon = Icons.Default.Check,
                    label = "Confirm & Save Bill",
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.scannedBill != null) {
                // Seller name field
                var sellerName by remember(state.scannedBill) {
                    mutableStateOf(state.scannedBill.sellerName)
                }
                OutlinedTextField(
                    value = sellerName,
                    onValueChange = {
                        sellerName = it
                        onSellerNameChanged(it)
                    },
                    label = { Text("Wholesaler / Seller Name") },
                    leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Bill number if present
                if (state.scannedBill.billNumber.isNotBlank()) {
                    Text(
                        text = "Bill No: ${state.scannedBill.billNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Items header
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

                LazyColumn(modifier = Modifier.weight(1f)) {
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
                                        text = "${item.quantity} ${item.unit} x ₹${item.price}",
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
                                // Edit button
                                IconButton(onClick = {
                                    editingIndex = index
                                    editingItem = item
                                    showEditDialog = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Delete button
                                IconButton(onClick = { onDeleteItem(item) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
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
                            Text("Add Missing Item")
                        }
                    }
                }

                // Total amount card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                        Text("Total Amount", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "₹${"%.2f".format(state.scannedBill.totalAmount)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                        Text("No bill data found. Please try scanning again.")
                    }
                }
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAddMode) "Add Item" else "Edit Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Qty") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (₹)") },
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
                Text(if (isAddMode) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
