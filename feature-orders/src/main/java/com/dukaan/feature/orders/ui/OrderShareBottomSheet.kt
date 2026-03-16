package com.dukaan.feature.orders.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderShareBottomSheet(
    supplierName: String,
    supplierPhone: String,
    onShareText: () -> Unit,
    onSharePdf: () -> Unit,
    onSendToSupplier: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Share Order",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ShareOptionRow(
                icon = Icons.Outlined.Chat,
                iconTint = Color(0xFF25D366),
                iconBg = Color(0xFF25D366).copy(alpha = 0.1f),
                title = "Text via WhatsApp",
                subtitle = "Send formatted order as a text message",
                onClick = {
                    onShareText()
                    onDismiss()
                }
            )

            Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

            ShareOptionRow(
                icon = Icons.Outlined.PictureAsPdf,
                iconTint = Color(0xFF065F46),
                iconBg = Color(0xFF065F46).copy(alpha = 0.1f),
                title = "PDF (Preview & Share)",
                subtitle = "View professional purchase order PDF before sharing",
                onClick = {
                    onSharePdf()
                    onDismiss()
                }
            )

            if (supplierPhone.isNotBlank()) {
                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                ShareOptionRow(
                    icon = Icons.Outlined.Send,
                    iconTint = Color(0xFF2563EB),
                    iconBg = Color(0xFF2563EB).copy(alpha = 0.1f),
                    title = "Send PDF to${if (supplierName.isNotBlank()) " ${supplierName.take(18)}" else " Supplier"}",
                    subtitle = "Send PDF directly to $supplierPhone",
                    onClick = {
                        onSendToSupplier()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconBg
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
