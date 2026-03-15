package com.dukaan.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.core.ui.translation.LocalAppStrings

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
    gstNumber: String = "",
    email: String = "",
    upiId: String = "",
    tagline: String = "",
    bankName: String = "",
    bankAccountNumber: String = "",
    bankIfscCode: String = "",
    onSaveProfile: (shopName: String, ownerName: String, phone: String, address: String,
                    gstNumber: String, email: String, upiId: String, tagline: String,
                    bankName: String, bankAccountNumber: String, bankIfscCode: String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    languageCode: String = "en",
    onLanguageChange: (String) -> Unit = {},
    onApplyLanguage: (String) -> Unit = {},
    isTranslating: Boolean = false,
    onPreviewPdf: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    var showEditProfile by remember { mutableStateOf(false) }
    var pendingLanguageCode by remember(languageCode) { mutableStateOf(languageCode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    onBackClick?.let { click ->
                        IconButton(onClick = click) {
                            Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
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
                strings.shopProfile,
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
                        if (tagline.isNotBlank()) {
                            Text(tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (ownerName.isNotBlank()) Text(ownerName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (email.isNotBlank()) Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (gstNumber.isNotBlank() || upiId.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            if (gstNumber.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GSTIN: $gstNumber", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (upiId.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("UPI: $upiId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (bankName.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$bankName | A/c: $bankAccountNumber | IFSC: $bankIfscCode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(strings.setUpYourShopProfile, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(strings.profileOnInvoices, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showEditProfile = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.editProfile)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Appearance
            Text(
                strings.appearance,
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
                        Text(strings.darkTheme, fontWeight = FontWeight.Medium)
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onToggleDarkTheme)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language
            Text(
                strings.appLanguage,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var showLanguagePicker by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguagePicker = !showLanguagePicker },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(strings.language, fontWeight = FontWeight.Medium)
                                Text(
                                    SupportedLanguages.getLanguageName(languageCode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            if (showLanguagePicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }

                    AnimatedVisibility(visible = showLanguagePicker) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            SupportedLanguages.languages.chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { lang ->
                                        FilterChip(
                                            selected = pendingLanguageCode == lang.code,
                                            onClick = { pendingLanguageCode = lang.code },
                                            label = {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(lang.nativeName, style = MaterialTheme.typography.labelSmall)
                                                    Text(lang.englishName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Apply button
                            if (pendingLanguageCode != languageCode) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onApplyLanguage(pendingLanguageCode) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isTranslating
                                ) {
                                    if (isTranslating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.translatingApp)
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.applyLanguage)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PDF Preview
            OutlinedButton(
                onClick = onPreviewPdf,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = shopName.isNotBlank()
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.previewInvoicePdf)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            Text(
                strings.about,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsRow(icon = Icons.Outlined.Info, title = strings.appVersion, value = "1.0.0")
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsRow(icon = Icons.Outlined.AutoAwesome, title = strings.poweredBy, value = "Gemini AI")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                strings.madeWithCare,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (showEditProfile) {
            EditProfileDialog(
                shopName = shopName,
                ownerName = ownerName,
                phone = phone,
                address = address,
                gstNumber = gstNumber,
                email = email,
                upiId = upiId,
                tagline = tagline,
                bankName = bankName,
                bankAccountNumber = bankAccountNumber,
                bankIfscCode = bankIfscCode,
                onDismiss = { showEditProfile = false },
                onSave = { sn, on, ph, addr, gst, em, upi, tag, bn, ban, bic ->
                    onSaveProfile(sn, on, ph, addr, gst, em, upi, tag, bn, ban, bic)
                    showEditProfile = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    shopName: String,
    ownerName: String,
    phone: String,
    address: String,
    gstNumber: String,
    email: String,
    upiId: String,
    tagline: String,
    bankName: String,
    bankAccountNumber: String,
    bankIfscCode: String,
    onDismiss: () -> Unit,
    onSave: (shopName: String, ownerName: String, phone: String, address: String,
             gstNumber: String, email: String, upiId: String, tagline: String,
             bankName: String, bankAccountNumber: String, bankIfscCode: String) -> Unit
) {
    var editShopName by remember { mutableStateOf(shopName) }
    var editOwnerName by remember { mutableStateOf(ownerName) }
    var editPhone by remember { mutableStateOf(phone) }
    var editAddress by remember { mutableStateOf(address) }
    var editGstNumber by remember { mutableStateOf(gstNumber) }
    var editEmail by remember { mutableStateOf(email) }
    var editUpiId by remember { mutableStateOf(upiId) }
    var editTagline by remember { mutableStateOf(tagline) }
    var editBankName by remember { mutableStateOf(bankName) }
    var editBankAccountNumber by remember { mutableStateOf(bankAccountNumber) }
    var editBankIfscCode by remember { mutableStateOf(bankIfscCode) }
    var showBankSection by remember { mutableStateOf(bankName.isNotBlank()) }
    val strings = LocalAppStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editShopProfile, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Basic Info
                Text(strings.basicInfo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = editShopName, onValueChange = { editShopName = it },
                    label = { Text(strings.shopNameLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Store, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = editOwnerName, onValueChange = { editOwnerName = it },
                    label = { Text(strings.ownerNameLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Person, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = editPhone, onValueChange = { editPhone = it },
                    label = { Text(strings.phoneLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Phone, null, Modifier.size(20.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = editAddress, onValueChange = { editAddress = it },
                    label = { Text(strings.addressLabel) },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2
                )
                OutlinedTextField(
                    value = editTagline, onValueChange = { editTagline = it },
                    label = { Text(strings.taglineLabel) },
                    leadingIcon = { Icon(Icons.Outlined.FormatQuote, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tax & Payment
                Text(strings.taxAndPayment, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = editGstNumber, onValueChange = { editGstNumber = it.uppercase() },
                    label = { Text(strings.gstNumberLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Receipt, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = editEmail, onValueChange = { editEmail = it },
                    label = { Text(strings.emailLabel) },
                    leadingIcon = { Icon(Icons.Outlined.Email, null, Modifier.size(20.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = editUpiId, onValueChange = { editUpiId = it },
                    label = { Text(strings.upiIdLabel) },
                    leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bank Details (collapsible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.bankDetails, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { showBankSection = !showBankSection }) {
                        Text(if (showBankSection) strings.hide else strings.show)
                    }
                }
                AnimatedVisibility(visible = showBankSection) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = editBankName, onValueChange = { editBankName = it },
                            label = { Text(strings.bankNameLabel) },
                            leadingIcon = { Icon(Icons.Outlined.AccountBalance, null, Modifier.size(20.dp)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = editBankAccountNumber, onValueChange = { editBankAccountNumber = it },
                            label = { Text(strings.accountNumberLabel) },
                            leadingIcon = { Icon(Icons.Outlined.CreditCard, null, Modifier.size(20.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = editBankIfscCode, onValueChange = { editBankIfscCode = it.uppercase() },
                            label = { Text(strings.ifscCodeLabel) },
                            leadingIcon = { Icon(Icons.Outlined.Pin, null, Modifier.size(20.dp)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(editShopName, editOwnerName, editPhone, editAddress,
                        editGstNumber, editEmail, editUpiId, editTagline,
                        editBankName, editBankAccountNumber, editBankIfscCode)
                },
                enabled = editShopName.isNotBlank()
            ) { Text(strings.save) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
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
