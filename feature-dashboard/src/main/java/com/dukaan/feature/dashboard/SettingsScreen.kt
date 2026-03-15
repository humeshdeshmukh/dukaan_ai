package com.dukaan.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: (() -> Unit)? = null,
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    shopName: String = "",
    ownerName: String = "",
    phone: String = "",
    address: String = "",
    onSaveProfile: (shopName: String, ownerName: String, phone: String, address: String) -> Unit = { _, _, _, _ -> }
) {
    var editShopName by remember { mutableStateOf(shopName) }
    var editOwnerName by remember { mutableStateOf(ownerName) }
    var editPhone by remember { mutableStateOf(phone) }
    var editAddress by remember { mutableStateOf(address) }
    var showEditProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    onBackClick?.let { click ->
                        IconButton(onClick = click) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Shop Profile Section
            Text(
                "Shop Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (shopName.isNotBlank()) {
                        Text(shopName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (ownerName.isNotBlank()) Text(ownerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Set up your shop profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showEditProfile = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Profile")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Appearance
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Dark Theme", fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onToggleDarkTheme)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsRow(icon = Icons.Outlined.Info, title = "App Version", value = "1.0.0")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(icon = Icons.Outlined.AutoAwesome, title = "Powered by", value = "Gemini AI")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Made with care for Indian Shopkeepers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (showEditProfile) {
            AlertDialog(
                onDismissRequest = { showEditProfile = false },
                title = { Text("Edit Shop Profile") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editShopName, onValueChange = { editShopName = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editOwnerName, onValueChange = { editOwnerName = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = editAddress, onValueChange = { editAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onSaveProfile(editShopName, editOwnerName, editPhone, editAddress)
                        showEditProfile = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditProfile = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
