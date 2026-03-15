package com.dukaan.ai.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun shareViaWhatsApp(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_TEXT, message)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(fallback, "Share via"))
    }
}

fun shareViaWhatsAppToPhone(context: Context, message: String, phone: String) {
    val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
    val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone
        else if (cleanPhone.length == 10) "+91$cleanPhone"
        else cleanPhone
    val url = "https://wa.me/${formattedPhone.removePrefix("+")}?text=${android.net.Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        shareViaWhatsApp(context, message)
    }
}

fun shareText(context: Context, message: String, title: String = "Share") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun sharePdfFile(context: Context, file: File, title: String = "Share PDF") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun sharePdfViaWhatsApp(context: Context, file: File, caption: String = "") {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_STREAM, uri)
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        sharePdfFile(context, file, "Share PDF")
    }
}

fun sharePdfViaWhatsAppToPhone(context: Context, file: File, phone: String, caption: String = "") {
    val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
    val formattedPhone = if (cleanPhone.startsWith("+")) cleanPhone
        else if (cleanPhone.length == 10) "+91$cleanPhone"
        else cleanPhone
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra("jid", "${formattedPhone.removePrefix("+")}@s.whatsapp.net")
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        sharePdfViaWhatsApp(context, file, caption)
    }
}
