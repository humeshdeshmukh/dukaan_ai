package com.dukaan.feature.orders.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dukaan.core.ui.components.LargeActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WholesaleOrderScreen(
    viewModel: OrderViewModel,
    onBackClick: () -> Unit,
    onShareClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Order") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording State / Pulse Animation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isRecording) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale)
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }

                FloatingActionButton(
                    onClick = { viewModel.toggleRecording() },
                    containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Record",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }
            }

            if (uiState.recognizedText.isNotBlank()) {
                Text(
                    text = uiState.recognizedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Text(
                text = if (uiState.isRecording) "Listening..." else "Tap to speak your order",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Order List
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (uiState.items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No items added yet", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.items) { item ->
                            ListItem(
                                headlineContent = { Text(item.name, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text("${item.quantity} ${item.unit}") },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeItem(item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                                    }
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.items.isNotEmpty()) {
                LargeActionButton(
                    label = "Share via WhatsApp",
                    onClick = { onShareClick(viewModel.getWhatsAppMessage()) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Share
                )
            }
        }
    }
}
