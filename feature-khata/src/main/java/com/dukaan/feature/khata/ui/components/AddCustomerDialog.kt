package com.dukaan.feature.khata.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dukaan.core.ui.translation.LocalAppStrings

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, khataType: String) -> Unit
) {
    val strings = LocalAppStrings.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var khataType by remember { mutableStateOf("SMALL") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addNewCustomer) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.customerNameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(strings.phoneNumberLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Account Type Selection
                Text(
                    strings.accountType,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Small Khata (Page)
                    Surface(
                        onClick = { khataType = "SMALL" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (khataType == "SMALL") MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (khataType == "SMALL") ButtonDefaults.outlinedButtonBorder
                                else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.StickyNote2,
                                null,
                                Modifier.size(24.dp),
                                tint = if (khataType == "SMALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.smallAccounts,
                                fontSize = 12.sp,
                                fontWeight = if (khataType == "SMALL") FontWeight.Bold else FontWeight.Normal,
                                color = if (khataType == "SMALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Big Khata (Book)
                    Surface(
                        onClick = { khataType = "BIG" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (khataType == "BIG") Color(0xFFEF4444).copy(alpha = 0.12f)
                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (khataType == "BIG") ButtonDefaults.outlinedButtonBorder
                                else null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                null,
                                Modifier.size(24.dp),
                                tint = if (khataType == "BIG") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.bigAccounts,
                                fontSize = 12.sp,
                                fontWeight = if (khataType == "BIG") FontWeight.Bold else FontWeight.Normal,
                                color = if (khataType == "BIG") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name, phone, khataType)
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text(strings.add)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
