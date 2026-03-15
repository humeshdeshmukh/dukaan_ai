package com.dukaan.ai.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dukaan.core.network.model.Bill
import com.dukaan.feature.khata.domain.model.StatementShareData
import com.dukaan.feature.khata.domain.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // Paints
    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun subtitlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.parseColor("#666666")
    }
    private fun headerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE
    }
    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.parseColor("#333333")
    }
    private fun boldPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun largeBoldPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun docTitlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#065F46")
    }
    private fun linePaint() = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f }
    private fun accentBgPaint() = Paint().apply { color = Color.parseColor("#065F46") }
    private fun stripePaint() = Paint().apply { color = Color.parseColor("#F9FAFB") }
    private fun greenPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#00B37E")
    }
    private fun redPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#EF4444")
    }
    private fun footerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f; color = Color.parseColor("#9CA3AF")
    }

    // ===================== BILL PDF =====================

    fun generateBillPdf(context: Context, shopInfo: ShopInfo, bill: Bill): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        var y = MARGIN

        y = drawShopHeader(canvas, shopInfo, y)
        y = drawDocTitle(canvas, "INVOICE", y)
        y = drawBillMeta(canvas, bill, y)
        y = drawBillItemsTable(canvas, bill, y)
        y = drawBillTotal(canvas, bill, y)
        if (shopInfo.upiId.isNotBlank()) {
            y = drawPaymentInfo(canvas, shopInfo, y)
        }
        drawFooter(canvas)

        document.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val file = File(dir, "Invoice_${bill.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawBillMeta(canvas: Canvas, bill: Bill, startY: Float): Float {
        var y = startY
        val body = bodyPaint()
        val bold = boldPaint()

        if (bill.sellerName.isNotBlank()) {
            canvas.drawText("Seller: ${bill.sellerName}", MARGIN, y + 12f, bold)
            y += 18f
        }
        if (bill.billNumber.isNotBlank()) {
            canvas.drawText("Bill No: ${bill.billNumber}", MARGIN, y + 12f, body)
            y += 16f
        }
        canvas.drawText("Date: ${dateTimeFormat.format(Date(bill.timestamp))}", MARGIN, y + 12f, body)
        y += 24f
        return y
    }

    private fun drawBillItemsTable(canvas: Canvas, bill: Bill, startY: Float): Float {
        var y = startY

        // Column positions
        val colX = floatArrayOf(MARGIN, MARGIN + 240f, MARGIN + 320f, MARGIN + 400f)
        val headers = listOf("Item", "Qty", "Unit", "Amount")

        // Header row
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, accentBgPaint())
        val hPaint = headerPaint()
        headers.forEachIndexed { i, header ->
            canvas.drawText(header, colX[i] + 6f, y + 15f, hPaint)
        }
        y += 26f

        // Item rows
        val body = bodyPaint()
        val bold = boldPaint()
        bill.items.forEachIndexed { index, item ->
            if (index % 2 == 0) {
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f, stripePaint())
            }
            canvas.drawText(item.name.take(35), colX[0] + 6f, y + 14f, body)
            canvas.drawText("${item.quantity}", colX[1] + 6f, y + 14f, body)
            canvas.drawText(item.unit, colX[2] + 6f, y + 14f, body)
            canvas.drawText(currencyFormat.format(item.total), colX[3] + 6f, y + 14f, bold)
            y += 20f
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 8f
        return y
    }

    private fun drawBillTotal(canvas: Canvas, bill: Bill, startY: Float): Float {
        var y = startY
        val large = largeBoldPaint()
        val totalText = "Total: ${currencyFormat.format(bill.totalAmount)}"
        val textWidth = large.measureText(totalText)
        canvas.drawText(totalText, PAGE_WIDTH - MARGIN - textWidth, y + 16f, large)
        y += 30f
        return y
    }

    private fun drawPaymentInfo(canvas: Canvas, shopInfo: ShopInfo, startY: Float): Float {
        var y = startY
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 12f
        val bold = boldPaint()
        val body = bodyPaint()
        canvas.drawText("Payment Info", MARGIN, y + 12f, bold)
        y += 18f
        canvas.drawText("UPI: ${shopInfo.upiId}", MARGIN, y + 12f, body)
        y += 20f
        return y
    }

    // ===================== STATEMENT PDF =====================

    fun generateStatementPdf(context: Context, shopInfo: ShopInfo, data: StatementShareData): File {
        val document = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        y = drawShopHeader(canvas, shopInfo, y)
        y = drawDocTitle(canvas, "KHATA STATEMENT", y)
        y = drawStatementCustomerInfo(canvas, data, y)
        y = drawStatementSummary(canvas, data, y)

        // Transaction table header
        y = drawTransactionTableHeader(canvas, y)

        // Transaction rows (with multi-page support)
        val body = bodyPaint()
        val green = greenPaint()
        val red = redPaint()

        data.transactions.forEachIndexed { index, txn ->
            if (y > PAGE_HEIGHT - 80f) {
                drawFooter(canvas)
                document.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
                y = drawTransactionTableHeader(canvas, y)
            }

            if (index % 2 == 0) {
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f, stripePaint())
            }

            val isJama = txn.type == TransactionType.JAMA
            val txColX = floatArrayOf(MARGIN, MARGIN + 100f, MARGIN + 200f, MARGIN + 320f)
            canvas.drawText(dateFormat.format(Date(txn.date)), txColX[0] + 6f, y + 14f, body)
            canvas.drawText(if (isJama) "Payment" else "Credit", txColX[1] + 6f, y + 14f, if (isJama) green else red)
            canvas.drawText(currencyFormat.format(txn.amount), txColX[2] + 6f, y + 14f, if (isJama) green else red)
            canvas.drawText(txn.notes?.take(30) ?: "", txColX[3] + 6f, y + 14f, body)
            y += 20f
        }

        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        drawFooter(canvas)
        document.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val file = File(dir, "Statement_${data.customerName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawStatementCustomerInfo(canvas: Canvas, data: StatementShareData, startY: Float): Float {
        var y = startY
        val bold = boldPaint()
        val body = bodyPaint()
        canvas.drawText("Customer: ${data.customerName}", MARGIN, y + 12f, bold)
        y += 16f
        canvas.drawText("Phone: ${data.customerPhone}", MARGIN, y + 12f, body)
        y += 16f
        canvas.drawText("Period: ${data.period}", MARGIN, y + 12f, body)
        y += 24f
        return y
    }

    private fun drawStatementSummary(canvas: Canvas, data: StatementShareData, startY: Float): Float {
        var y = startY
        val bold = boldPaint()
        val green = greenPaint()
        val red = redPaint()
        val body = bodyPaint()

        // Summary box
        val boxPaint = Paint().apply { color = Color.parseColor("#F3F4F6"); style = Paint.Style.FILL }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 70f, boxPaint)

        y += 4f
        canvas.drawText("Total Credit (Baki):", MARGIN + 10f, y + 16f, body)
        canvas.drawText(currencyFormat.format(data.totalCredit), MARGIN + 200f, y + 16f, red)
        y += 20f
        canvas.drawText("Total Payments (Jama):", MARGIN + 10f, y + 16f, body)
        canvas.drawText(currencyFormat.format(data.totalPayment), MARGIN + 200f, y + 16f, green)
        y += 20f
        canvas.drawLine(MARGIN + 10f, y, PAGE_WIDTH - MARGIN - 10f, y, linePaint())
        y += 4f
        canvas.drawText("Net Balance:", MARGIN + 10f, y + 16f, bold)
        val netColor = if (data.netBalance > 0) red else green
        canvas.drawText(currencyFormat.format(Math.abs(data.netBalance)), MARGIN + 200f, y + 16f, netColor)
        val label = if (data.netBalance > 0) "(to collect)" else if (data.netBalance < 0) "(to pay)" else "(settled)"
        canvas.drawText(label, MARGIN + 310f, y + 16f, body)
        y += 30f
        return y
    }

    private fun drawTransactionTableHeader(canvas: Canvas, startY: Float): Float {
        var y = startY
        val txColX = floatArrayOf(MARGIN, MARGIN + 100f, MARGIN + 200f, MARGIN + 320f)
        val headers = listOf("Date", "Type", "Amount", "Notes")
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, accentBgPaint())
        val hPaint = headerPaint()
        headers.forEachIndexed { i, header ->
            canvas.drawText(header, txColX[i] + 6f, y + 15f, hPaint)
        }
        y += 26f
        return y
    }

    // ===================== SHARED HELPERS =====================

    private fun drawShopHeader(canvas: Canvas, shop: ShopInfo, startY: Float): Float {
        var y = startY
        val title = titlePaint()
        val sub = subtitlePaint()

        if (shop.shopName.isNotBlank()) {
            canvas.drawText(shop.shopName, MARGIN, y + 20f, title)
            y += 28f
        } else {
            canvas.drawText("Dukaan AI", MARGIN, y + 20f, title)
            y += 28f
        }
        if (shop.address.isNotBlank()) {
            canvas.drawText(shop.address, MARGIN, y + 10f, sub)
            y += 16f
        }
        val contactParts = listOfNotNull(
            shop.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
            shop.email.takeIf { it.isNotBlank() }
        ).joinToString("  |  ")
        if (contactParts.isNotBlank()) {
            canvas.drawText(contactParts, MARGIN, y + 10f, sub)
            y += 16f
        }
        if (shop.gstNumber.isNotBlank()) {
            canvas.drawText("GSTIN: ${shop.gstNumber}", MARGIN, y + 10f, sub)
            y += 16f
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 12f
        return y
    }

    private fun drawDocTitle(canvas: Canvas, title: String, startY: Float): Float {
        var y = startY
        val paint = docTitlePaint()
        val textWidth = paint.measureText(title)
        canvas.drawText(title, (PAGE_WIDTH - textWidth) / 2f, y + 16f, paint)
        y += 30f
        return y
    }

    private fun drawFooter(canvas: Canvas) {
        val footer = footerPaint()
        val text = "Generated by Dukaan AI"
        val textWidth = footer.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - textWidth) / 2f, PAGE_HEIGHT - 20f, footer)
    }
}
