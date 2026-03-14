package com.dukaan.feature.ocr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.ui.components.LargeActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultScreen(
    state: OcrUiState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteItem: (BillItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Verify Scanned Bill") })
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
                Text(
                    text = "Seller: ${state.scannedBill.id}", // Placeholder mapping
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.scannedBill.items) { item ->
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("${item.quantity} ${item.unit} x ₹${item.price}") },
                            trailingContent = {
                                IconButton(onClick = { onDeleteItem(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Amount", style = MaterialTheme.typography.titleLarge)
                        Text("₹${state.scannedBill.totalAmount}", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No bill data found. Please try scanning again.")
                }
            }
        }
    }
}
