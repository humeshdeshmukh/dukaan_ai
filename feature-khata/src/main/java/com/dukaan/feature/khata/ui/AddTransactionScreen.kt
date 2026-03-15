package com.dukaan.feature.khata.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.ui.translation.LocalAppStrings
import com.dukaan.feature.khata.domain.model.Transaction
import com.dukaan.feature.khata.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    customerId: Long,
    type: TransactionType,
    viewModel: KhataViewModel,
    onSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isListeningForAmount by remember { mutableStateOf(false) }
    val speechText by viewModel.speechText.collectAsState()
    val voiceParseResult by viewModel.voiceParseResult.collectAsState()
    val isVoiceParsing by viewModel.isVoiceParsing.collectAsState()
    var currentType by remember { mutableStateOf(type) }

    // AI voice parsing
    LaunchedEffect(speechText) {
        if (speechText.isNotBlank() && isListeningForAmount) {
            isListeningForAmount = false
            viewModel.parseVoiceTransaction(speechText)
        }
    }

    // Apply AI parse result
    LaunchedEffect(voiceParseResult) {
        voiceParseResult?.let { result ->
            if (result.amount > 0) {
                amount = result.amount.toLong().let { if (result.amount == it.toDouble()) it.toString() else result.amount.toString() }
            }
            val parsedNotes = result.notes
            if (!parsedNotes.isNullOrBlank()) {
                notes = parsedNotes
            }
            if (result.confidence > 0.7) {
                currentType = if (result.type == "JAMA") TransactionType.JAMA else TransactionType.BAKI
            }
        }
    }

    val isJama = currentType == TransactionType.JAMA
    val accentColor = if (isJama) Color(0xFF00B37E) else Color(0xFFEF4444)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isJama) strings.paymentReceivedJama else strings.creditGivenBaki,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearVoiceParseResult()
                        onBackClick()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Amount indicator
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = if (isJama) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp),
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                placeholder = { Text("0.00", fontSize = 32.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("\u20B9", fontSize = 32.sp, fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(strings.addNotesOptional) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Type toggle (if AI suggests different type)
            if (currentType != type) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI detected this as ${if (isJama) "Jama (Payment)" else "Baki (Credit)"}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { currentType = type }) {
                            Text(strings.undo, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // AI Parse Result Preview
            AnimatedVisibility(visible = isVoiceParsing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        strings.aiParsingVoice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull()
                    if (amountVal != null && amountVal > 0) {
                        viewModel.addTransaction(
                            Transaction(
                                id = 0,
                                customerId = customerId,
                                amount = amountVal,
                                type = currentType,
                                date = System.currentTimeMillis(),
                                notes = notes.ifBlank { null }
                            )
                        )
                        viewModel.clearVoiceParseResult()
                        onSuccess()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.saveEntry, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Voice shortcut - AI-powered
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isListeningForAmount) {
                            viewModel.stopVoiceInput()
                            isListeningForAmount = false
                        } else {
                            viewModel.startVoiceInput()
                            isListeningForAmount = true
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isListeningForAmount) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (isListeningForAmount) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isListeningForAmount) strings.listeningTapToStop else strings.smartVoiceEntry,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            strings.voiceEntryHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
