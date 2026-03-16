package com.dukaan.feature.orders.ui

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    val existingSuppliers by viewModel.existingSuppliers.collectAsState()
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Mic permission
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleRecording()
    }

    val onMicClick: () -> Unit = {
        if (uiState.isRecording || uiState.isContinuousListening) {
            viewModel.toggleRecording()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.toggleRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                onBackClick?.let { click ->
                    IconButton(onClick = click) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                }
                Text(
                    text = strings.orders,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = if (onBackClick != null) 4.dp else 16.dp)
                )
            }
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
                            onClick = { viewModel.clearOrder() },
                            modifier = Modifier.height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
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
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.height(44.dp)
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.newOrder, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.orderHistory, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            when (uiState.selectedTab) {
                0 -> CreateOrderTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    strings = strings,
                    onMicClick = onMicClick
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

    // Supplier Picker Dialog
    if (uiState.showSupplierPicker) {
        SupplierPickerDialog(
            suppliers = existingSuppliers,
            onSelect = { name, phone -> viewModel.selectSupplier(name, phone) },
            onNewSupplier = { name, phone -> viewModel.selectSupplier(name, phone) },
            onClear = { viewModel.clearSupplier() },
            onDismiss = { viewModel.dismissSupplierPicker() },
            strings = strings
        )
    }
}

// ============== CREATE ORDER TAB ==============

@Composable
private fun CreateOrderTab(
    uiState: OrderUiState,
    viewModel: OrderViewModel,
    strings: com.dukaan.core.ui.translation.AppStrings,
    onMicClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Supplier Picker Row
        item {
            SupplierPickerRow(
                selectedName = uiState.supplierName,
                selectedPhone = uiState.supplierPhone,
                onClick = { viewModel.showSupplierPicker() },
                strings = strings
            )
        }

        // Compact Voice Input — right below supplier
        item {
            CompactVoiceInput(
                isRecording = uiState.isRecording,
                isContinuousListening = uiState.isContinuousListening,
                isProcessing = uiState.isProcessing,
                recognizedText = uiState.recognizedText,
                audioLevel = uiState.audioLevel,
                hasItems = uiState.items.isNotEmpty(),
                onToggleRecording = onMicClick,
                strings = strings
            )
        }

        // Error Display
        uiState.error?.let { error ->
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
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

        // Items Header with Add button — only show when items exist or always for adding
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
                FilledTonalButton(
                    onClick = { viewModel.showAddItemDialog() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.addItemManually, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Items list or empty state
        if (uiState.items.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
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
                    index = index,
                    onEditClick = { viewModel.setEditingItem(index) },
                    onDeleteClick = { viewModel.removeItem(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }

        // Notes field — only show when items are added to keep screen clean
        if (uiState.items.isNotEmpty()) {
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
}

// ============== SUPPLIER PICKER ROW ==============

@Composable
private fun SupplierPickerRow(
    selectedName: String,
    selectedPhone: String,
    onClick: () -> Unit,
    strings: com.dukaan.core.ui.translation.AppStrings
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedName.ifEmpty { strings.selectSupplier },
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

// ============== COMPACT VOICE INPUT ==============

@Composable
private fun CompactVoiceInput(
    isRecording: Boolean,
    isContinuousListening: Boolean,
    isProcessing: Boolean,
    recognizedText: String,
    audioLevel: Float,
    hasItems: Boolean,
    onToggleRecording: () -> Unit,
    strings: com.dukaan.core.ui.translation.AppStrings
) {
    val isActive = isRecording || isContinuousListening
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordingButton(isRecording = isActive, onClick = onToggleRecording)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            isActive -> strings.listening
                            hasItems -> strings.speakNextItems
                            else -> strings.tapToSpeakOrder
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    if (isProcessing) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            strings.aiIsParsingOrder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isActive) {
                    Spacer(Modifier.height(6.dp))
                    AudioLevelIndicator(audioLevel = audioLevel)
                }
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

// ============== AUDIO LEVEL INDICATOR ==============

@Composable
private fun AudioLevelIndicator(audioLevel: Float) {
    val animatedLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = tween(100),
        label = "audioLevel"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        val barCount = 5
        for (i in 0 until barCount) {
            val threshold = i.toFloat() / barCount
            val barHeight = if (animatedLevel > threshold) {
                (8 + (animatedLevel - threshold) * 16).coerceAtMost(16f)
            } else {
                4f
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (animatedLevel > threshold) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

// ============== RECORDING BUTTON ==============

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
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAlpha"
    )
    val color by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "color"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
        if (isRecording) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = ringAlpha),
                modifier = Modifier
                    .size(56.dp)
                    .scale(ringScale)
            ) {}
        }
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
}

// ============== ORDER ITEM CARD ==============

@Composable
private fun OrderItemCard(
    item: OrderItem,
    index: Int,
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
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
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
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
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

// ============== SUPPLIER PICKER DIALOG ==============

@Composable
private fun SupplierPickerDialog(
    suppliers: List<Pair<String, String?>>,
    onSelect: (String, String?) -> Unit,
    onNewSupplier: (String, String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    strings: com.dukaan.core.ui.translation.AppStrings
) {
    var search by remember { mutableStateOf("") }
    var showNewForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val filtered = remember(search, suppliers) {
        if (search.isBlank()) suppliers
        else suppliers.filter { (name, phone) ->
            name.contains(search, ignoreCase = true) || (phone?.contains(search) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectSupplier, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                if (showNewForm) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(strings.supplierNameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Store, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text(strings.supplierPhoneLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp)
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
                                    onNewSupplier(newName.trim(), newPhone.trim().ifBlank { null })
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = newName.isNotBlank()
                        ) { Text(strings.save) }
                    }
                } else {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text(strings.searchSupplier) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Add New Supplier Button
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
                            Text(strings.addNewSupplier, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
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
                            Text(strings.noSuppliersFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filtered) { (name, phone) ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(name, phone) },
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
                                                    name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(name, fontWeight = FontWeight.Medium)
                                            if (!phone.isNullOrBlank()) {
                                                Text(
                                                    phone,
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
