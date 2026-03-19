package com.dukaan.core.network.ai

import android.graphics.Bitmap
import com.dukaan.core.network.model.Bill
import com.dukaan.core.network.model.BillItem
import com.dukaan.core.network.model.OrderItem
import com.dukaan.core.network.model.Order
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dukaan.core.network.di.GeminiFlash
import com.dukaan.core.network.di.GeminiLite
import javax.inject.Inject
import javax.inject.Singleton

interface GeminiBillingService {
    suspend fun parseBillingSpeech(speechText: String, languageCode: String = "en"): List<BillItem>
    suspend fun parseOcrText(rawText: String): Bill
    suspend fun parseBillImage(image: Bitmap): Bill
    suspend fun parseBillImageWithOcr(image: Bitmap, ocrText: String): Bill
    suspend fun parseMultiPageBill(images: List<Bitmap>): Bill
    suspend fun parseMultiPageBillWithOcr(images: List<Bitmap>, ocrTexts: List<String>): Bill
    suspend fun chatAboutBill(billJson: String, userMessage: String, image: Bitmap? = null, languageCode: String = "en"): String
    suspend fun parseOrderSpeech(speechText: String, languageCode: String = "en"): List<OrderItem>
    suspend fun parseCustomerListImage(image: Bitmap, ocrText: String = ""): List<BillItem>
}

@Singleton
class GeminiBillingServiceImpl @Inject constructor(
    @GeminiLite private val generativeModel: GenerativeModel,
    @GeminiFlash private val flashModel: GenerativeModel
) : GeminiBillingService {

    override suspend fun parseBillingSpeech(speechText: String, languageCode: String): List<BillItem> = withContext(Dispatchers.IO) {
        val nameInstruction = when (languageCode) {
            "en" -> "Use English item names. For Hindi terms, use their common English name (cheeni→Sugar, atta→Wheat Flour)."
            "hi" -> "Use Hindi item names in Devanagari script (e.g. चीनी, आटा, चावल, दाल, नमक). Do NOT translate to English."
            "hi-en" -> "Use Hinglish item names in Roman script as the shopkeeper says them (e.g. Cheeni, Atta, Chawal, Daal, Namak). Do NOT translate to English."
            else -> "Keep item names exactly as spoken by the shopkeeper. Do NOT translate to English."
        }

        val prompt = """
            You are an AI for Dukaan AI, an Indian grocery billing app.
            Extract items from this shopkeeper's speech: "$speechText"

            Return ONLY a JSON array. Each item: name, quantity, unit, price.
            - "price" = TOTAL amount for the line (qty × rate if rate given, or stated total)
            $nameInstruction
            - Brand names stay as-is (Tata Salt, Parle-G, Amul, Surf Excel, Fortune, etc.)

            EXAMPLES:
            - "500 gram cheeni 60 rupees per kg" → qty=500, unit="g", price=30
            - "2 kg chawal 80 rupees per kg" → qty=2, unit="kg", price=160
            - "Soap 3 piece 30 rupees each" → qty=3, unit="pc", price=90
            - "Oil 1 litre 180 rupees" → qty=1, unit="L", price=180

            UNITS: pav=250g, adha kilo=500g, savaa kilo=1.25kg, dedh kilo=1.5kg, dhai=2.5kg, dozen/darjan=12pc
            All other standard Indian grocery units and terms are recognized.

            Ignore filler words: aur, phir, hmm, ok, haan, toh, bas, ho gaya.
            If no valid items, return [].
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val rawText = response.text ?: "[]"
            // Robust JSON extraction: find the JSON array in the response
            val jsonString = extractJsonArray(rawText)
            com.google.gson.Gson().fromJson(jsonString, Array<BillItem>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        // Try to find a JSON array in the response
        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val startIndex = cleaned.indexOf('[')
        val endIndex = cleaned.lastIndexOf(']')
        return if (startIndex >= 0 && endIndex > startIndex) {
            cleaned.substring(startIndex, endIndex + 1)
        } else {
            "[]"
        }
    }

    private fun extractJsonObject(text: String): String {
        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val startIndex = cleaned.indexOf('{')
        if (startIndex < 0) return "{}"
        var depth = 0
        for (i in startIndex until cleaned.length) {
            when (cleaned[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return cleaned.substring(startIndex, i + 1)
                }
            }
        }
        return "{}"
    }

    private fun parseBillJson(rawText: String): Bill {
        val jsonString = extractJsonObject(rawText)

        if (jsonString.isBlank() || jsonString == "{}") {
            throw IllegalStateException("AI service returned empty response. Please try with a clearer image.")
        }

        return try {
            val gson = com.google.gson.Gson()
            val jsonElement = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
                ?: throw IllegalStateException("Invalid response format from AI service.")

            val sellerName = jsonElement.get("sellerName")?.asString ?: ""
            val billNumber = jsonElement.get("billNumber")?.asString ?: ""

            // Validate numeric values and coerce to safe ranges
            val totalAmount = jsonElement.get("totalAmount")?.asDouble?.takeIf { it >= 0 } ?: 0.0
            val subtotal = jsonElement.get("subtotal")?.asDouble?.takeIf { it >= 0 } ?: 0.0
            val discountAmount = jsonElement.get("discountAmount")?.asDouble?.takeIf { it >= 0 } ?: 0.0
            val discountPercent = jsonElement.get("discountPercent")?.asDouble?.coerceIn(0.0, 100.0) ?: 0.0
            val taxAmount = jsonElement.get("taxAmount")?.asDouble?.takeIf { it >= 0 } ?: 0.0
            val taxPercent = jsonElement.get("taxPercent")?.asDouble?.coerceIn(0.0, 50.0) ?: 0.0

            val itemsArray = jsonElement.getAsJsonArray("items")
            var validItemCount = 0
            var skippedItemCount = 0

            val items = if (itemsArray != null && itemsArray.size() > 0) {
                itemsArray.mapNotNull { element ->
                    try {
                        val obj = element.asJsonObject
                        val name = obj.get("name")?.asString?.trim()

                        if (name.isNullOrBlank()) {
                            skippedItemCount++
                            return@mapNotNull null
                        }

                        val quantity = obj.get("quantity")?.asDouble?.takeIf { it > 0 } ?: 1.0
                        val unit = obj.get("unit")?.asString?.trim() ?: "pc"

                        // Extract unitPrice (per-unit rate) if available
                        val unitPrice = obj.get("unitPrice")?.asDouble?.takeIf { it >= 0 }
                            ?: obj.get("rate")?.asDouble?.takeIf { it >= 0 }
                            ?: 0.0

                        // Extract lineTotal/price (total for this item line)
                        val lineTotal = obj.get("lineTotal")?.asDouble?.takeIf { it >= 0 }
                            ?: obj.get("price")?.asDouble?.takeIf { it >= 0 }
                            ?: obj.get("amount")?.asDouble?.takeIf { it >= 0 }
                            ?: obj.get("total")?.asDouble?.takeIf { it >= 0 }
                            ?: 0.0

                        // Extract per-item discount if present
                        val itemDiscountAmount = obj.get("itemDiscount")?.asDouble?.takeIf { it >= 0 }
                            ?: obj.get("itemDiscountAmount")?.asDouble?.takeIf { it >= 0 }
                            ?: 0.0
                        val itemDiscountPercent = obj.get("itemDiscountPercent")?.asDouble?.coerceIn(0.0, 100.0) ?: 0.0

                        // Calculate price: if unitPrice given, compute; else use lineTotal
                        val price = if (unitPrice > 0 && lineTotal <= 0) {
                            val gross = unitPrice * quantity
                            val discount = if (itemDiscountAmount > 0) itemDiscountAmount
                                           else gross * itemDiscountPercent / 100.0
                            (gross - discount).coerceAtLeast(0.0)
                        } else {
                            lineTotal
                        }

                        if (price <= 0) {
                            skippedItemCount++
                            return@mapNotNull null
                        }

                        validItemCount++
                        BillItem(
                            name = name,
                            quantity = quantity,
                            unit = unit,
                            price = price,
                            unitPrice = unitPrice,
                            itemDiscountPercent = itemDiscountPercent,
                            itemDiscountAmount = itemDiscountAmount
                        )
                    } catch (e: Exception) {
                        skippedItemCount++
                        android.util.Log.w("BillingService", "Skipped malformed item: ${e.message}")
                        null
                    }
                }
            } else {
                emptyList()
            }

            // Log parsing statistics for debugging
            if (skippedItemCount > 0) {
                android.util.Log.w("BillingService", "Parsed $validItemCount items, skipped $skippedItemCount invalid items")
            }

            // Validate we extracted meaningful data
            if (items.isEmpty() && sellerName.isBlank()) {
                throw IllegalStateException("No valid bill data could be extracted. Please try with a clearer image of the bill.")
            }

            // Use AI service's totalAmount. Only fall back to sum if AI service returned nothing.
            val finalTotal = when {
                totalAmount > 0.0 -> totalAmount
                items.isNotEmpty() -> items.sumOf { it.total } - discountAmount + taxAmount
                else -> 0.0
            }

            // If subtotal not returned by AI service, derive it from items sum
            val finalSubtotal = if (subtotal > 0.0) subtotal else items.sumOf { it.total }

            Bill(
                items = items,
                totalAmount = finalTotal,
                sellerName = sellerName.trim(),
                billNumber = billNumber.trim(),
                subtotal = finalSubtotal,
                discountAmount = discountAmount,
                discountPercent = discountPercent,
                taxAmount = taxAmount,
                taxPercent = taxPercent
            )
        } catch (e: IllegalStateException) {
            throw e // Re-throw our meaningful exceptions
        } catch (e: com.google.gson.JsonSyntaxException) {
            android.util.Log.e("BillingService", "Invalid JSON from AI service: $jsonString", e)
            throw RuntimeException("AI service returned invalid data format. Please try again.")
        } catch (e: Exception) {
            android.util.Log.e("BillingService", "Bill parsing failed", e)
            throw RuntimeException("Unable to parse the bill data. Please try with a clearer image.")
        }
    }

    override suspend fun parseOcrText(rawText: String): Bill = withContext(Dispatchers.IO) {
        val prompt = """
            You are an expert Indian wholesale bill reader for Dukaan AI app.
            Extract full bill details from this OCR text of a wholesale purchase bill.

            OCR Text: "$rawText"

            Return ONLY a valid JSON object with these fields:
            - "sellerName": string (wholesaler/seller/company name at the top)
            - "billNumber": string (invoice/bill number, empty string if not found)
            - "items": array of objects, each with:
                - "name": string (product name exactly as written)
                - "quantity": number
                - "unit": string (kg, pc, box, packet, litre, dozen, etc.)
                - "unitPrice": number (per-unit rate/price from Rate column)
                - "lineTotal": number (total for this line = unitPrice × quantity)
                - "itemDiscount": number (rupee discount on THIS item, 0 if none)
                - "itemDiscountPercent": number (% discount on THIS item, 0 if none)
            - "subtotal": number (sum of all lineTotals before bill discount/tax, 0 if not present)
            - "discountAmount": number (overall bill discount in rupees, 0 if none)
            - "discountPercent": number (overall bill discount as percentage, 0 if none)
            - "taxAmount": number (total GST/tax amount = CGST + SGST or IGST, 0 if none)
            - "taxPercent": number (GST percentage rate, 0 if none)
            - "totalAmount": number (FINAL grand total / net amount the buyer has to pay)

            IMPORTANT:
            - "totalAmount" must be the FINAL amount after discount and tax
            - Extract BOTH unitPrice (rate) AND lineTotal (amount) for each item
            - If Rate (unitPrice) is not visible, use 0. Do NOT derive it from lineTotal.
        """.trimIndent()

        try {
            val response = flashModel.generateContent(prompt)
            val rawJson = response.text

            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("No response received from AI service")
            }

            parseBillJson(rawJson)
        } catch (e: IllegalStateException) {
            throw e // Re-throw our own exceptions
        } catch (e: Exception) {
            android.util.Log.e("BillingService", "AI OCR parsing failed", e)
            throw RuntimeException("Unable to parse the extracted text. Please try with a clearer image.")
        }
    }

    override suspend fun parseBillImage(image: Bitmap): Bill = withContext(Dispatchers.IO) {
        val prompt = buildImagePrompt(null)

        try {
            val inputContent = content {
                image(image)
                text(prompt)
            }
            val response = flashModel.generateContent(inputContent)
            val rawJson = response.text

            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("No response received from AI service")
            }

            parseBillJson(rawJson)
        } catch (e: IllegalStateException) {
            throw e // Re-throw our own exceptions
        } catch (e: Exception) {
            // Log for debugging but throw user-friendly exception
            android.util.Log.e("BillingService", "AI image parsing failed", e)
            throw RuntimeException("Unable to analyze the bill image. Please try again with a clearer photo.")
        }
    }

    override suspend fun parseBillImageWithOcr(image: Bitmap, ocrText: String): Bill = withContext(Dispatchers.IO) {
        val prompt = buildImagePrompt(ocrText)

        try {
            val inputContent = content {
                image(image)
                text(prompt)
            }
            val response = flashModel.generateContent(inputContent)
            val rawJson = response.text

            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("No response received from AI service")
            }

            parseBillJson(rawJson)
        } catch (e: IllegalStateException) {
            throw e // Re-throw our own exceptions
        } catch (e: Exception) {
            android.util.Log.e("BillingService", "AI image+OCR parsing failed", e)
            // Fallback: try text-only parsing if image+text failed
            if (ocrText.isNotBlank()) {
                try {
                    return@withContext parseOcrText(ocrText)
                } catch (fallbackException: Exception) {
                    android.util.Log.e("BillingService", "OCR fallback also failed", fallbackException)
                }
            }
            throw RuntimeException("Unable to analyze the bill image. Please try again with a clearer photo.")
        }
    }

    private fun buildImagePrompt(ocrText: String?): String {
        val ocrSection = if (!ocrText.isNullOrBlank()) {
            """

            OCR PRE-EXTRACTED TEXT (use as reference, image is primary source):
            --- OCR START ---
            $ocrText
            --- OCR END ---
            """.trimIndent()
        } else ""

        return """
            You are an Indian wholesale bill reader for Dukaan AI. Extract all data from this bill image.

            EXTRACT these fields:
            - "sellerName": shop/company name at top (bold text, look for M/s, Shri, Traders, Enterprises, Bhandar)
            - "billNumber": invoice/bill number ("" if not found)
            - "items": ALL printed AND handwritten items. Each item object must have:
              • "name": item name exactly as written
              • "quantity": number (e.g., 2, 0.5, 1.5)
              • "unit": string (kg, g, L, ml, pc, pkt, box, dozen, etc.)
              • "unitPrice": per-unit rate/price (the RATE column value, NOT the line total)
              • "lineTotal": total for this line = unitPrice × quantity (the AMOUNT/TOTAL column value)
              • "itemDiscount": rupee discount on THIS item only (0 if none)
              • "itemDiscountPercent": percentage discount on THIS item (0 if none)
              Example: If bill shows "Sugar 2kg Rate:45 Amount:90", then unitPrice=45, lineTotal=90
              Example: If bill shows "Rice 5kg @60 = 300 -10%", then unitPrice=60, lineTotal=270, itemDiscountPercent=10
            - "subtotal": sum before discount/tax (0 if not shown)
            - "discountAmount": overall bill discount rupees (0 if none); "discountPercent": % discount (0 if none)
            - "taxAmount": CGST+SGST or IGST total rupees (0 if none); "taxPercent": GST rate % (0 if none)
            - "totalAmount": FINAL net amount payable (after discount, after tax)
            $ocrSection

            Return ONLY this JSON (no markdown, no explanation):
            {"sellerName":"","billNumber":"","items":[{"name":"","quantity":1.0,"unit":"kg","unitPrice":0.0,"lineTotal":0.0,"itemDiscount":0.0,"itemDiscountPercent":0.0}],"subtotal":0.0,"discountAmount":0.0,"discountPercent":0.0,"taxAmount":0.0,"taxPercent":0.0,"totalAmount":0.0}

            CRITICAL RULES FOR HANDWRITTEN TEXT:
            - CAREFULLY examine the entire image for handwritten text in blue, black, or red ink
            - Check margins, empty rows, and spaces between printed items for handwritten additions
            - Look for circled, underlined, or starred items — these are often important
            - Handwritten numbers may be messy — try to interpret them (e.g., "7" vs "1", "5" vs "6")
            - Include items with ONLY handwritten quantities or prices — they are valid
            - NEVER skip an item just because parts are handwritten
            - ALWAYS extract unitPrice (per-unit rate) and lineTotal (line amount) separately
            - If Rate (unitPrice) is not visible, use 0. Do NOT derive it from lineTotal.
            - Include handwritten items added by pen. Include ALL items. NEVER return empty items[] if bill has items.
        """.trimIndent()
    }

    override suspend fun parseMultiPageBill(images: List<Bitmap>): Bill =
        parseMultiPageBillWithOcr(images, emptyList())

    override suspend fun parseMultiPageBillWithOcr(images: List<Bitmap>, ocrTexts: List<String>): Bill = withContext(Dispatchers.IO) {
        val combinedOcr = ocrTexts.filter { it.isNotBlank() }.mapIndexed { i, text ->
            "--- Page ${i + 1} OCR ---\n$text"
        }.joinToString("\n\n")

        val ocrSection = if (combinedOcr.isNotBlank()) {
            """

            ADDITIONAL CONTEXT — ML Kit OCR Text (pre-extracted from all pages):
            Use this as a REFERENCE to cross-check what you see in the images.
            The images are the primary source of truth, but this OCR text helps identify
            items, numbers, and text that may be hard to read.

            --- OCR TEXT START ---
            $combinedOcr
            --- OCR TEXT END ---
            """.trimIndent()
        } else ""

        val prompt = """
            You are an Indian wholesale bill reader for Dukaan AI. You have ${images.size} pages of the SAME bill.
            Read ALL pages and produce ONE combined bill output.

            EXTRACT:
            - "sellerName": shop/company name from the first page (bold/top text, M/s, Traders, Enterprises)
            - "billNumber": invoice/bill number ("" if not found)
            - "items": ALL items across ALL pages (printed and handwritten). Each item must have:
              • "name": item name exactly as written
              • "quantity": number
              • "unit": string (kg, g, L, pc, pkt, box, dozen, etc.)
              • "unitPrice": per-unit rate/price (Rate column value)
              • "lineTotal": total for this line = unitPrice × quantity (Amount column value)
              • "itemDiscount": rupee discount on THIS item (0 if none)
              • "itemDiscountPercent": % discount on THIS item (0 if none)
              Do NOT duplicate carry-forward totals between pages.
              Include handwritten items added by pen after the printed total.
            - "subtotal": sum before discount/tax (0 if not shown)
            - "discountAmount": overall bill discount rupees (0 if none); "discountPercent": % (0 if none)
            - "taxAmount": CGST+SGST or IGST total (0 if none); "taxPercent": GST rate % (0 if none)
            - "totalAmount": FINAL net amount payable (last page footer, after discount+tax)
            $ocrSection

            Return ONLY this JSON (no markdown, no explanation):
            {"sellerName":"","billNumber":"","items":[{"name":"","quantity":1.0,"unit":"kg","unitPrice":0.0,"lineTotal":0.0,"itemDiscount":0.0,"itemDiscountPercent":0.0}],"subtotal":0.0,"discountAmount":0.0,"discountPercent":0.0,"taxAmount":0.0,"taxPercent":0.0,"totalAmount":0.0}

            CRITICAL RULES FOR HANDWRITTEN TEXT:
            - CAREFULLY examine all pages for handwritten text in blue, black, or red ink
            - Check margins, empty rows, and spaces between printed items for handwritten additions
            - Look for circled, underlined, or starred items — these are often important
            - Handwritten numbers may be messy — try to interpret them (e.g., "7" vs "1", "5" vs "6")
            - Include items with ONLY handwritten quantities or prices — they are valid
            - NEVER skip an item just because parts are handwritten
            - Extract unitPrice AND lineTotal for each item. Combine ALL pages. NEVER return empty items[] if bill has items.
        """.trimIndent()

        try {
            val inputContent = content {
                images.forEachIndexed { index, bitmap ->
                    image(bitmap)
                    text("--- Page ${index + 1} of ${images.size} ---")
                }
                text(prompt)
            }
            val response = flashModel.generateContent(inputContent)
            val rawJson = response.text

            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("No response received from AI service")
            }

            parseBillJson(rawJson)
        } catch (e: IllegalStateException) {
            throw e // Re-throw our own exceptions
        } catch (e: Exception) {
            android.util.Log.e("BillingService", "AI multi-page parsing failed", e)
            // Fallback: try OCR-only parsing if available
            if (combinedOcr.isNotBlank()) {
                try {
                    return@withContext parseOcrText(combinedOcr)
                } catch (fallbackException: Exception) {
                    android.util.Log.e("BillingService", "Multi-page OCR fallback also failed", fallbackException)
                }
            }
            throw RuntimeException("Unable to analyze the multi-page bill. Please try with clearer images.")
        }
    }

    override suspend fun chatAboutBill(
        billJson: String,
        userMessage: String,
        image: Bitmap?,
        languageCode: String
    ): String = withContext(Dispatchers.IO) {
        val langInstruction = when (languageCode) {
            "en" -> "Respond in simple English."
            "hi" -> "Respond in simple Hindi."
            "hi-en" -> "Respond in simple Hinglish (Hindi-English mix)."
            else -> "Respond in simple, easy language the user understands."
        }
        val trimmedBill = billJson.take(1500)
        val prompt = """
            You are an AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            The user is looking at a scanned bill and wants to ask you about it.

            Here is the digitized bill data:
            $trimmedBill

            User's question: "$userMessage"

            Respond helpfully and concisely in simple, easy-to-understand language.
            If the user asks to correct an item, suggest the exact correction.
            If the user asks about totals, verify by calculating from items.
            Keep responses short and practical — this is for a busy shopkeeper.
            Do not use any markdown formatting like ** or * or #. Plain text only.
            $langInstruction
        """.trimIndent()

        try {
            val response = if (image != null) {
                val inputContent = content {
                    image(image)
                    text(prompt)
                }
                generativeModel.generateContent(inputContent)
            } else {
                generativeModel.generateContent(prompt)
            }
            response.text ?: "Sorry, I couldn't process that. Please try again."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message ?: "Failed to get response"}"
        }
    }

    override suspend fun parseOrderSpeech(speechText: String, languageCode: String): List<OrderItem> = withContext(Dispatchers.IO) {
        val nameInstruction = when (languageCode) {
            "en" -> "Use English item names."
            "hi" -> "Use Hindi item names in Devanagari script. Do NOT translate to English."
            "hi-en" -> "Use Hinglish item names in Roman script as spoken. Do NOT translate to English."
            else -> "Keep item names exactly as spoken. Do NOT translate to English."
        }
        val prompt = """
            You are an AI for Dukaan AI, an Indian grocery billing app.
            Extract wholesale order items from this shopkeeper's speech: "$speechText"

            Return ONLY a JSON array. Each item: name, quantity, unit. (No price needed for orders.)
            $nameInstruction
            - Brand names stay as-is (Parle-G, Amul, Tata Salt, etc.)

            UNITS: pav=250g, adha kilo=500g, savaa kilo=1.25kg, dedh kilo=1.5kg, dhai=2.5kg, dozen/darjan=12pc
            All other standard Indian grocery units and terms are recognized.

            Ignore filler words: aur, phir, hmm, ok, haan, toh, bas, ho gaya.
            If no valid items, return [].
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val rawText = response.text ?: "[]"
            val jsonString = extractJsonArray(rawText)
            com.google.gson.Gson().fromJson(jsonString, Array<OrderItem>::class.java)
                .map { item ->
                    // Gson bypasses Kotlin null-safety; sanitize all string fields
                    OrderItem(
                        name = item.name as? String ?: "",
                        quantity = item.quantity,
                        unit = item.unit as? String ?: "",
                        notes = item.notes as? String ?: ""
                    )
                }
                .filter { it.name.isNotBlank() }
                .toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun parseCustomerListImage(image: Bitmap, ocrText: String): List<BillItem> = withContext(Dispatchers.IO) {
        val ocrSection = if (ocrText.isNotBlank())
            "\n\nPRINTED TEXT FROM OCR (item names may be here):\n\"\"\"$ocrText\"\"\""
        else ""

        val prompt = """
            You are an expert at reading HANDWRITTEN text on documents.
            This image shows a PRINTED SHOPPING LIST or FORM with HANDWRITTEN annotations written by pen/pencil.$ocrSection

            YOUR TASK: Find ALL handwritten numbers (quantities, prices) written BY HAND on this document.

            DOCUMENT LAYOUT - The document likely has:
            - PRINTED item names (typed/computer text) in one column
            - HANDWRITTEN numbers written by pen in columns for:
              * Quantity (e.g., 1, 2, 5, 10) - often BEFORE the item name or in a QTY column
              * Price/Amount (e.g., 50, 100, 250) - often AFTER the item name or in a PRICE/AMOUNT column
              * Rate per unit (e.g., ₹60/kg) - sometimes written next to quantity

            HOW TO IDENTIFY HANDWRITTEN vs PRINTED TEXT:
            - Handwriting is UNEVEN, SLANTED, varying line thickness
            - Printed text is UNIFORM, perfectly aligned, consistent font
            - Handwriting may be in BLUE or BLACK ink (pen) or GREY (pencil)
            - Numbers written by hand look different from typed numbers
            - Look for ANY marks that are NOT part of the printed template

            SCAN EVERY PART OF THE IMAGE CAREFULLY:
            - Left margin (quantities often written here BEFORE item names)
            - Right side (prices/totals often written here AFTER item names)
            - Blank spaces next to each printed item name
            - In empty boxes or columns of the form
            - At the bottom (may have subtotals, totals)
            - Any circled, underlined, or ticked (✓) numbers

            OUTPUT FORMAT: Return ONLY a JSON array.
            Each item: {"name": string, "quantity": number, "unit": string, "price": number}

            EXTRACTION RULES:
            - "name" = the PRINTED item name from the form
            - "quantity" = HANDWRITTEN number for quantity (default 1 if not visible)
            - "price" = HANDWRITTEN number for total price (0 if not written)
            - "unit" = infer from context: kg, g, L, ml, pc, pkt, box, dozen (default "pc")
            - If rate like "₹60/kg" and qty "2", calculate: price = 2 × 60 = 120
            - INCLUDE items that have ANY handwritten annotation (qty OR price OR checkmark)

            HANDWRITTEN NUMBER PATTERNS:
            - "1" may look like vertical line "l" or "I"
            - "2" may have a loop at bottom or be angular "Z"
            - "5" may look like "S"
            - "7" may have horizontal cross stroke
            - "0" may be oval, round, or have a slash
            - Numbers may be written quickly/messily - use context to interpret

            EXAMPLE LAYOUTS:
            - "[handwritten: 2] [printed: Sugar] [handwritten: 80]" → qty=2, name=Sugar, price=80
            - "[printed: Rice 1kg] [handwritten: 60]" → name=Rice, qty=1, unit=kg, price=60
            - "[printed: Soap] [handwritten: ✓] [handwritten: 3] [handwritten: 90]" → qty=3, price=90

            Return [] if no handwritten annotations found.
        """.trimIndent()

        try {
            // Use generativeModel (more capable) for better handwriting recognition
            val response = generativeModel.generateContent(
                content {
                    image(image)
                    text(prompt)
                }
            )
            val rawText = response.text ?: "[]"
            val jsonString = extractJsonArray(rawText)
            com.google.gson.Gson().fromJson(jsonString, Array<BillItem>::class.java)
                .map { item ->
                    BillItem(
                        name = item.name as? String ?: "",
                        quantity = item.quantity,
                        unit = item.unit as? String ?: "pc",
                        price = item.price.coerceAtLeast(0.0)
                    )
                }
                .filter { it.name.isNotBlank() }
                .toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
