package com.dukaan.ai.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
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
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // 6-column item table layout (MARGIN=40, right edge=555, total content=515)
    // S.No(32) | Item(168) | Qty(50) | Unit(50) | Rate(80) | Amount(135)
    private val ITEM_COL_X = floatArrayOf(MARGIN, MARGIN + 32f, MARGIN + 200f, MARGIN + 250f, MARGIN + 300f, MARGIN + 380f)
    private val ITEM_COL_R = floatArrayOf(MARGIN + 32f, MARGIN + 200f, MARGIN + 250f, MARGIN + 300f, MARGIN + 380f, PAGE_WIDTH - MARGIN.toFloat())

    // ===================== PAINTS =====================

    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun subtitlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; color = Color.parseColor("#666666")
    }
    private fun headerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE
    }
    private fun bodyPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; color = Color.parseColor("#333333")
    }
    private fun boldPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun largeBoldPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun docTitlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#065F46")
    }
    private fun sectionLabelPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#065F46")
    }
    private fun linePaint() = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f }
    private fun accentBgPaint() = Paint().apply { color = Color.parseColor("#065F46") }
    private fun stripePaint() = Paint().apply { color = Color.parseColor("#F9FAFB") }
    private fun greenPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#00B37E")
    }
    private fun redPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#EF4444")
    }
    private fun footerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f; color = Color.parseColor("#9CA3AF")
    }
    private fun totalLabelPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.parseColor("#555555")
    }
    private fun totalValuePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1a1a1a")
    }
    private fun discountValuePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.parseColor("#059669")
    }
    private fun gstValuePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.parseColor("#1D4ED8")
    }
    private fun grandTotalTextPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.WHITE
    }
    private fun amountWordsPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); color = Color.parseColor("#374151")
    }

    // ===================== BILL / INVOICE PDF =====================

    fun generateBillPdf(context: Context, shopInfo: ShopInfo, bill: Bill): File {
        val document = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val docTitle = if (shopInfo.gstNumber.isNotBlank()) "TAX INVOICE" else "INVOICE"

        y = drawInvoiceHeader(canvas, shopInfo, bill, y, docTitle)
        y = drawBillToSection(canvas, bill, y)
        y = drawItemsTableHeader(canvas, y)

        bill.items.forEachIndexed { index, item ->
            if (y > PAGE_HEIGHT - 200f) {
                drawPageFooter(canvas, pageNum)
                document.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
                y = drawItemsTableHeader(canvas, y)
            }
            y = drawItemRow(canvas, index, item, y)
        }

        // Divider after items
        canvas.drawLine(MARGIN, y + 2f, PAGE_WIDTH - MARGIN, y + 2f, linePaint())
        y += 12f

        // Ensure enough space for totals section (~180px)
        if (y > PAGE_HEIGHT - 200f) {
            drawPageFooter(canvas, pageNum)
            document.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        y = drawTotalsSection(canvas, bill, y)
        y = drawPaymentAndNotes(canvas, shopInfo, bill, y)

        // Ensure enough space for footer section (~90px)
        if (y > PAGE_HEIGHT - 110f) {
            drawPageFooter(canvas, pageNum)
            document.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        drawTermsAndFooter(canvas, bill, y)
        document.finishPage(page)

        val dir = File(context.cacheDir, "pdfs").also { it.mkdirs() }
        val file = File(dir, "Invoice_${bill.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /**
     * Two-column header: shop info (left) | invoice meta (right)
     */
    private fun drawInvoiceHeader(
        canvas: Canvas, shop: ShopInfo, bill: Bill, startY: Float, docTitle: String
    ): Float {
        var y = startY
        val rightEdge = PAGE_WIDTH - MARGIN

        // Row 1: Shop name (left) + document title (right)
        canvas.drawText(shop.shopName.ifBlank { "My Shop" }, MARGIN, y + 20f, titlePaint())
        val dtPaint = docTitlePaint()
        val dtW = dtPaint.measureText(docTitle)
        canvas.drawText(docTitle, rightEdge - dtW, y + 20f, dtPaint)
        y += 28f

        // Left: address, contact, GSTIN, proprietor
        var leftY = y
        var rightY = y
        val sub = subtitlePaint()
        val metaBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.parseColor("#666666") }
        val metaBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#1a1a1a")
        }

        if (shop.address.isNotBlank()) {
            canvas.drawText(shop.address, MARGIN, leftY + 9f, sub)
            leftY += 13f
        }
        val contact = listOfNotNull(
            shop.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
            shop.email.takeIf { it.isNotBlank() }
        ).joinToString("  |  ")
        if (contact.isNotBlank()) {
            canvas.drawText(contact, MARGIN, leftY + 9f, sub)
            leftY += 13f
        }
        if (shop.gstNumber.isNotBlank()) {
            canvas.drawText("GSTIN: ${shop.gstNumber}", MARGIN, leftY + 9f, sub)
            leftY += 13f
        }
        if (shop.ownerName.isNotBlank()) {
            canvas.drawText("Proprietor: ${shop.ownerName}", MARGIN, leftY + 9f, sub)
            leftY += 13f
        }

        // Right: Invoice No, Date, Time, Payment (all right-aligned)
        fun drawMetaRow(label: String, value: String, useMetaBold: Boolean = false) {
            val lW = metaBody.measureText(label)
            val vPaint = if (useMetaBold) metaBold else metaBody
            val vW = vPaint.measureText(value)
            canvas.drawText(label, rightEdge - lW - vW - 2f, rightY + 9f, metaBody)
            canvas.drawText(value, rightEdge - vW, rightY + 9f, vPaint)
            rightY += 13f
        }

        if (bill.billNumber.isNotBlank()) drawMetaRow("Invoice No:  ", bill.billNumber, useMetaBold = true)
        drawMetaRow("Date:  ", dateFormat.format(Date(bill.timestamp)), useMetaBold = true)
        drawMetaRow("Time:  ", timeFormat.format(Date(bill.timestamp)))
        drawMetaRow("Payment:  ", bill.paymentMode.ifBlank { "CASH" }.uppercase())

        y = maxOf(leftY, rightY) + 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 14f
        return y
    }

    /**
     * "Bill To" box showing customer / seller details
     */
    private fun drawBillToSection(canvas: Canvas, bill: Bill, startY: Float): Float {
        val hasCustomer = bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank()
        if (!hasCustomer && bill.sellerName.isBlank()) return startY

        var y = startY
        var contentH = 14f // label row
        if (bill.customerName.isNotBlank()) contentH += 16f
        if (bill.customerPhone.isNotBlank()) contentH += 13f
        if (bill.sellerName.isNotBlank()) contentH += 13f
        val boxH = contentH + 14f

        val boxRight = PAGE_WIDTH / 2f - 10f
        val bgPaint = Paint().apply { color = Color.parseColor("#F0FDF4"); style = Paint.Style.FILL }
        val borderPaint = Paint().apply { color = Color.parseColor("#6EE7B7"); style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(MARGIN, y, boxRight, y + boxH, bgPaint)
        canvas.drawRect(MARGIN, y, boxRight, y + boxH, borderPaint)

        y += 6f
        canvas.drawText("BILL TO", MARGIN + 8f, y + 8f, sectionLabelPaint())
        y += 14f

        if (bill.customerName.isNotBlank()) {
            canvas.drawText(bill.customerName, MARGIN + 8f, y + 8f, boldPaint())
            y += 16f
        }
        if (bill.customerPhone.isNotBlank()) {
            canvas.drawText("Ph: ${bill.customerPhone}", MARGIN + 8f, y + 8f, bodyPaint())
            y += 13f
        }
        if (bill.sellerName.isNotBlank()) {
            canvas.drawText("Seller: ${bill.sellerName}", MARGIN + 8f, y + 8f, bodyPaint())
            y += 13f
        }

        y += 16f
        return y
    }

    /**
     * 6-column items table header: S.No | Item | Qty | Unit | Rate (₹) | Amount (₹)
     */
    private fun drawItemsTableHeader(canvas: Canvas, startY: Float): Float {
        val y = startY
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, accentBgPaint())
        val hp = headerPaint()
        val headers = listOf("S.No", "Item / Description", "Qty", "Unit", "Rate (₹)", "Amount (₹)")
        headers.forEachIndexed { i, h ->
            if (i >= 4) {
                val w = hp.measureText(h)
                canvas.drawText(h, ITEM_COL_R[i] - w - 4f, y + 15f, hp)
            } else {
                canvas.drawText(h, ITEM_COL_X[i] + 4f, y + 15f, hp)
            }
        }
        return y + 26f
    }

    /**
     * Single item row with per-unit rate calculation
     */
    private fun drawItemRow(canvas: Canvas, index: Int, item: BillItem, startY: Float): Float {
        val y = startY
        if (index % 2 == 0) canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f, stripePaint())
        val body = bodyPaint()
        val bold = boldPaint()

        canvas.drawText("${index + 1}", ITEM_COL_X[0] + 4f, y + 14f, body)
        canvas.drawText(item.name.take(28), ITEM_COL_X[1] + 4f, y + 14f, body)

        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString()
        else "%.2f".format(item.quantity)
        canvas.drawText(qtyStr, ITEM_COL_X[2] + 4f, y + 14f, body)
        canvas.drawText(item.unit.take(8), ITEM_COL_X[3] + 4f, y + 14f, body)

        // Per-unit rate (right-aligned within col 4)
        val rate = if (item.quantity > 0) item.price / item.quantity else item.price
        val rateStr = "%.2f".format(rate)
        val rateW = body.measureText(rateStr)
        canvas.drawText(rateStr, ITEM_COL_R[4] - rateW - 4f, y + 14f, body)

        // Line total (right-aligned within col 5)
        val amtStr = "%.2f".format(item.total)
        val amtW = bold.measureText(amtStr)
        canvas.drawText(amtStr, ITEM_COL_R[5] - amtW - 4f, y + 14f, bold)

        return y + 20f
    }

    /**
     * Totals section: Subtotal → Discount → Taxable Amount → CGST → SGST → GRAND TOTAL
     */
    private fun drawTotalsSection(canvas: Canvas, bill: Bill, startY: Float): Float {
        var y = startY
        val subtotal = if (bill.subtotal > 0.0) bill.subtotal else bill.items.sumOf { it.total }
        val discountAmt = when {
            bill.discountAmount > 0.0 -> bill.discountAmount
            bill.discountPercent > 0.0 -> subtotal * bill.discountPercent / 100.0
            else -> 0.0
        }
        val taxable = subtotal - discountAmt
        val taxAmt = when {
            bill.taxAmount > 0.0 -> bill.taxAmount
            bill.taxPercent > 0.0 -> taxable * bill.taxPercent / 100.0
            else -> 0.0
        }
        val grandTotal = bill.totalAmount

        val hasDiscount = discountAmt > 0.001
        val hasTax = bill.taxPercent > 0.001

        val labelX = PAGE_WIDTH / 2f + 20f
        val valueR = PAGE_WIDTH - MARGIN
        val lp = linePaint()

        fun row(label: String, value: String, lPaint: Paint = totalLabelPaint(), vPaint: Paint = totalValuePaint()) {
            canvas.drawText(label, labelX, y + 12f, lPaint)
            val vW = vPaint.measureText(value)
            canvas.drawText(value, valueR - vW, y + 12f, vPaint)
            y += 18f
        }

        row("Subtotal", "%.2f".format(subtotal))

        if (hasDiscount) {
            val discLabel = if (bill.discountPercent > 0.001)
                "Discount (${formatRate(bill.discountPercent)}%)" else "Discount"
            row(discLabel, "- %.2f".format(discountAmt), vPaint = discountValuePaint())
        }

        if (hasDiscount && hasTax) {
            canvas.drawLine(labelX, y - 2f, valueR, y - 2f, lp)
            row("Taxable Amount", "%.2f".format(taxable))
        }

        if (hasTax) {
            val halfRate = bill.taxPercent / 2.0
            val cgst = taxAmt / 2.0
            row("CGST @ ${formatRate(halfRate)}%", "+ %.2f".format(cgst), vPaint = gstValuePaint())
            row("SGST @ ${formatRate(halfRate)}%", "+ %.2f".format(cgst), vPaint = gstValuePaint())
        }

        // Grand total banner
        y += 6f
        val gtBg = Paint().apply { color = Color.parseColor("#065F46"); style = Paint.Style.FILL }
        canvas.drawRect(labelX - 10f, y, valueR, y + 28f, gtBg)
        val gtPaint = grandTotalTextPaint()
        canvas.drawText("GRAND TOTAL", labelX, y + 19f, gtPaint)
        val gtStr = currencyFormat.format(grandTotal)
        val gtW = gtPaint.measureText(gtStr)
        canvas.drawText(gtStr, valueR - gtW - 4f, y + 19f, gtPaint)
        y += 36f
        return y
    }

    /**
     * Payment mode, UPI ID, and bill notes
     */
    private fun drawPaymentAndNotes(canvas: Canvas, shopInfo: ShopInfo, bill: Bill, startY: Float): Float {
        val hasExtra = shopInfo.upiId.isNotBlank() || bill.notes.isNotBlank()
        if (!hasExtra) return startY

        var y = startY
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 10f

        val bold = boldPaint()
        val body = bodyPaint()

        if (shopInfo.upiId.isNotBlank()) {
            canvas.drawText("Payment", MARGIN, y + 10f, bold)
            y += 14f
            canvas.drawText("UPI ID: ${shopInfo.upiId}", MARGIN + 8f, y + 10f, body)
            y += 14f
        }
        if (bill.notes.isNotBlank()) {
            canvas.drawText("Notes:", MARGIN, y + 10f, bold)
            y += 14f
            canvas.drawText(bill.notes.take(80), MARGIN + 8f, y + 10f, body)
            y += 16f
        }

        y += 8f
        return y
    }

    /**
     * Amount in words, terms, thank-you message, and generated-by line
     */
    private fun drawTermsAndFooter(canvas: Canvas, bill: Bill, startY: Float) {
        var y = startY
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += 12f

        // Amount in words
        val words = amountToWords(bill.totalAmount)
        canvas.drawText(words, MARGIN, y + 9f, amountWordsPaint())
        y += 14f

        // Terms
        val termsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.parseColor("#6B7280") }
        canvas.drawText(
            "Terms: Goods once sold will not be taken back. Subject to local jurisdiction.",
            MARGIN, y + 9f, termsPaint
        )
        y += 16f

        // Thank you (centered, green)
        val tyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#065F46")
        }
        val tyText = "Thank you for your business!"
        val tyW = tyPaint.measureText(tyText)
        canvas.drawText(tyText, (PAGE_WIDTH - tyW) / 2f, y + 10f, tyPaint)
        y += 16f

        // Computer-generated note
        val fp = footerPaint()
        val cgText = "This is a computer-generated invoice and does not require a signature."
        val cgW = fp.measureText(cgText)
        canvas.drawText(cgText, (PAGE_WIDTH - cgW) / 2f, y + 9f, fp)
        y += 12f

        // Generated by
        val genText = "Generated by Dukaan AI"
        val genW = fp.measureText(genText)
        canvas.drawText(genText, (PAGE_WIDTH - genW) / 2f, y + 9f, fp)
    }

    /**
     * Page number footer for multi-page documents
     */
    private fun drawPageFooter(canvas: Canvas, pageNum: Int) {
        val fp = footerPaint()
        val text = "Page $pageNum"
        val w = fp.measureText(text)
        canvas.drawText(text, (PAGE_WIDTH - w) / 2f, PAGE_HEIGHT - 20f, fp)
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
        y = drawTransactionTableHeader(canvas, y)

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
        headers.forEachIndexed { i, header -> canvas.drawText(header, txColX[i] + 6f, y + 15f, hPaint) }
        y += 26f
        return y
    }

    // ===================== SHARED HELPERS =====================

    private fun drawShopHeader(canvas: Canvas, shop: ShopInfo, startY: Float): Float {
        var y = startY
        val title = titlePaint()
        val sub = subtitlePaint()

        canvas.drawText(shop.shopName.ifBlank { "Dukaan AI" }, MARGIN, y + 20f, title)
        y += 28f
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

    // ===================== AMOUNT IN WORDS =====================

    private val ONES = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val TENS = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun numToWords(n: Int): String {
        if (n <= 0) return ""
        if (n < 20) return ONES[n] + " "
        if (n < 100) return TENS[n / 10] + " " + (if (n % 10 > 0) ONES[n % 10] + " " else "")
        return ONES[n / 100] + " Hundred " + numToWords(n % 100)
    }

    private fun amountToWords(amount: Double): String {
        if (amount <= 0.0) return "Rupees Zero Only"
        val rupees = amount.toLong().coerceAtMost(9_999_999_99L)
        val paise = ((amount - rupees) * 100).toLong()

        val sb = StringBuilder()
        var rem = rupees.toInt()

        if (rem >= 10_000_000) { sb.append(numToWords(rem / 10_000_000)).append("Crore "); rem %= 10_000_000 }
        if (rem >= 100_000)    { sb.append(numToWords(rem / 100_000)).append("Lakh "); rem %= 100_000 }
        if (rem >= 1_000)      { sb.append(numToWords(rem / 1_000)).append("Thousand "); rem %= 1_000 }
        if (rem > 0)           { sb.append(numToWords(rem)) }

        val rupeesStr = sb.toString().trim()
        return if (paise > 0) {
            "Rupees $rupeesStr and ${numToWords(paise.toInt()).trim()} Paise Only"
        } else {
            "Rupees $rupeesStr Only"
        }
    }

    private fun formatRate(d: Double): String =
        if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
}
