package com.dukaan.feature.orders.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dukaan.core.network.model.Order
import com.dukaan.core.network.model.OrderItem
import com.dukaan.core.ui.components.ConfirmationDialog
import com.dukaan.core.ui.components.EmptyStateView
import com.dukaan.core.ui.translation.LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WholesaleOrderScreen(
    viewModel: OrderViewModel,
    onBackClick: (() -> Unit)? = null,
    onShareClick: (String) -> Unit,
    onOrderClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.orders, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    onBackClick?.let { click ->
                        IconButton(onClick = click) {
                            Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.selectedTab == 0 && uiState.items.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.saveOrder() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.save)
                        }
                        Button(
                            onClick = {
                                onShareClick(viewModel.getWhatsAppMessage())
                                viewModel.saveOrder()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareAndSave)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text(strings.newOrder, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text(strings.orderHistory, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (uiState.selectedTab) {
                0 -> CreateOrderTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    strings = strings
                )
                1 -> OrderHistoryTab(
                    uiState = uiState,
                    orders = allOrders,
                    viewModel = viewModel,
                    strings = strings,
                    onOrderClick = onOrderClick
                )
            }
        }
    }

    // Confirmation dialogs
    if (uiState.showClearConfirm) {
        ConfirmationDialog(
            title = strings.clearAll,
            message = strings.clearOrderConfirm,
            confirmText = strings.clearAll,
            onConfirm = { viewModel.confirmClear() },
            onDismiss = { viewModel.dismissClearConfirmation() }
        )
    }

    uiState.showDeleteConfirm?.let { orderId ->
        ConfirmationDialog(
            title = strings.deleteOrderLabel,
            message = strings.deleteOrderMessage,
            onConfirm = { viewModel.confirmDelete(orderId) },
            onDismiss = { viewModel.dismissDeleteConfirmation() }
        )
    }

    // Add Item Dialog
    if (uiState.showAddItemDialog) {
        AddItemDialog(
            title = strings.addItem,
            strings = strings,
            onConfirm = { name, qty, unit ->
                viewModel.addItemManually(name, qty, unit)
            },
            onDismiss = { viewModel.dismissAddItemDialog() }
        )
    }

    // Edit Item Dialog
    if (uiState.showEditItemDialog && uiState.editingItemIndex in uiState.items.indices) {
        val editItem = uiState.items[uiState.editingItemIndex]
        AddItemDialog(
            title = strings.editItem,
            strings = strings,
            initialName = editItem.name,
            initialQty = editItem.quantity.toString(),
            initialUnit = editItem.unit,
            onConfirm = { name, qty, unit ->
                viewModel.updateItem(
                    uiState.editingItemIndex,
                    OrderItem(name = name, quantity = qty, unit = unit)
                )
            },
            onDismiss = { viewModel.dismissEditItemDialog() }
        )
    }
}

// ============== CREATE ORDER TAB ==============

@Composable
private fun CreateOrderTab(
    uiState: OrderUiState,
    viewModel: OrderViewModel,
    strings: com.dukaan.core.ui.translation.AppStrings
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Supplier Name Field
        item {
            OutlinedTextField(
                value = uiState.supplierName,
                onValueChange = { viewModel.setSupplierName(it) },
                label = { Text(strings.supplierNameLabel) },
                leadingIcon = { Icon(Icons.Outlined.Store, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Voice Input Section
        item {
            VoiceInputSection(
                uiState = uiState,
                viewModel = viewModel,
                strings = strings
            )
        }

        // Error Display
        uiState.error?.let { error ->
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Saved confirmation
        if (uiState.isSaved) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF065F46).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF065F46),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            strings.orderSaved,
                            color = Color(0xFF065F46),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Items Header with Add button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.orderItems} (${uiState.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.showAddItemDialog() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addItemManually, style = MaterialTheme.typography.labelMedium)
                    }
                    if (uiState.items.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearOrder() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = strings.clearAll,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Items list or empty state
        if (uiState.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Default.ShoppingCart,
                        title = strings.noItemsYet,
                        subtitle = strings.orderHint
                    )
                }
            }
        } else {
            itemsIndexed(uiState.items) { index, item ->
                OrderItemCard(
                    item = item,
                    onEditClick = { viewModel.setEditingItem(index) },
                    onDeleteClick = { viewModel.removeItem(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // Notes field
        item {
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.setNotes(it) },
                label = { Text(strings.orderNotes) },
                placeholder = { Text(strings.addNotesOptional) },
                leadingIcon = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 4
            )
        }
    }
}

// ============== VOICE INPUT SECTION ==============

@Composable
private fun VoiceInputSection(
    uiState: OrderUiState,
    viewModel: OrderViewModel,
    strings: com.dukaan.core.ui.translation.AppStrings
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (uiState.isProcessing) {
                CircularProgressIndicator()
                Text(
                    strings.aiIsParsingOrder,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    if (uiState.isRecording) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .scale(scale)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }

                    FloatingActionButton(
                        onClick = { viewModel.toggleRecording() },
                        containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = strings.listening,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isRecording) strings.listening else strings.tapToSpeakOrder,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
                )

                if (uiState.recognizedText.isNotBlank()) {
                    Text(
                        text = "\"${uiState.recognizedText}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

// ============== ORDER ITEM CARD ==============

@Composable
private fun OrderItemCard(
    item: OrderItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============== ORDER HISTORY TAB ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderHistoryTab(
    uiState: OrderUiState,
    orders: List<Order>,
    viewModel: OrderViewModel,
    strings: com.dukaan.core.ui.translation.AppStrings,
    onOrderClick: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(strings.searchOrdersHint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Status filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            val filters = listOf(
                "ALL" to strings.filterAll,
                "PENDING" to strings.pendingStatus,
                "SENT" to strings.sentStatus,
                "COMPLETED" to strings.completedStatus
            )
            items(filters) { (value, label) ->
                FilterChip(
                    selected = uiState.statusFilter == value,
                    onClick = { viewModel.setStatusFilter(value) },
                    label = { Text(label) },
                    leadingIcon = if (uiState.statusFilter == value) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    icon = Icons.Default.Inventory,
                    title = strings.noOrdersYet,
                    subtitle = strings.ordersAppearHere
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderHistoryCard(
                        order = order,
                        strings = strings,
                        onClick = {
                            val orderId = order.id.toLongOrNull() ?: return@OrderHistoryCard
                            onOrderClick(orderId)
                        },
                        onDelete = {
                            val orderId = order.id.toLongOrNull() ?: return@OrderHistoryCard
                            viewModel.showDeleteConfirmation(orderId)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ============== ORDER HISTORY CARD ==============

@Composable
private fun OrderHistoryCard(
    order: Order,
    strings: com.dukaan.core.ui.translation.AppStrings,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.1f)
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = Color(0xFF2563EB)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = order.supplierName?.ifBlank { null } ?: "Order #${order.id}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${order.items.size} ${strings.items}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = order.status, strings = strings)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Item preview + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = order.items.take(3).joinToString(", ") { it.name } +
                        if (order.items.size > 3) " +${order.items.size - 3}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateFormat.format(Date(order.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============== STATUS BADGE ==============

@Composable
private fun StatusBadge(status: String, strings: com.dukaan.core.ui.translation.AppStrings) {
    val (label, bgColor, textColor) = when (status) {
        "PENDING" -> Triple(strings.pendingStatus, Color(0xFFFEF3C7), Color(0xFF92400E))
        "SENT" -> Triple(strings.sentStatus, Color(0xFFDBEAFE), Color(0xFF1E40AF))
        "COMPLETED" -> Triple(strings.completedStatus, Color(0xFFD1FAE5), Color(0xFF065F46))
        else -> Triple(status, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ============== ADD/EDIT ITEM DIALOG ==============

@Composable
private fun AddItemDialog(
    title: String,
    strings: com.dukaan.core.ui.translation.AppStrings,
    initialName: String = "",
    initialQty: String = "",
    initialUnit: String = "",
    onConfirm: (name: String, quantity: Double, unit: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var qty by remember { mutableStateOf(initialQty) }
    var unit by remember { mutableStateOf(initialUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.itemNameRequired) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text(strings.qtyRequired) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(strings.unitRequired) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val quantity = qty.toDoubleOrNull()
                    if (name.isNotBlank() && quantity != null && quantity > 0 && unit.isNotBlank()) {
                        onConfirm(name.trim(), quantity, unit.trim())
                    }
                },
                enabled = name.isNotBlank() && qty.toDoubleOrNull() != null && unit.isNotBlank()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
