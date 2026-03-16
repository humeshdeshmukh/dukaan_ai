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
            You are an AI for Dukaan AI, an Indian shop billing app used by kirana/grocery shopkeepers.
            Extract items from this shopkeeper's speech: "$speechText"

            Return ONLY a JSON array with: name, quantity, unit, price
            - "price" = TOTAL price for that item (the final amount the customer pays for that line)
            - The shopkeeper may say per-unit rate or total — always compute the TOTAL.

            ITEM NAMES: $nameInstruction
            Brand names should always stay as-is (Tata Salt, Parle-G, Surf Excel, Amul, etc.)

            PRICING EXAMPLES:
            - "500 gram cheeni 60 rupees per kg" → quantity=500, unit="g", price=30 (500g at 60/kg = 30)
            - "2 kg chawal 80 rupees per kg" → quantity=2, unit="kg", price=160 (2×80)
            - "Soap 3 piece 30 rupees each" → quantity=3, unit="pc", price=90 (3×30)
            - "Milk 500ml 30 rupees per litre" → quantity=500, unit="ml", price=15 (500/1000×30)
            - "1 dozen banana 60 rupees" → quantity=12, unit="pc", price=60 (already total)
            - "Oil 1 litre 180 rupees" → quantity=1, unit="L", price=180
            CRITICAL: "price" must always be the TOTAL amount for that item, NOT per-unit.

            RECOGNIZE THESE TERMS (but use the name according to the language instruction above):
            cheeni/chini, atta/aata, maida, chawal/chaawal, daal/dal, namak/nimak,
            tel/tail, doodh/dudh, ghee, sabun/saabun, chai patti, haldi, mirch/mirchi,
            jeera/zeera, dhaniya, hing, besan, suji/sooji/rawa, poha, gur/gud,
            saunf, ajwain, dalchini, elaichi, laung, rai/sarson, methi, kali mirch,
            tamatar, pyaaz, aloo, adrak, lehsun, palak, gobhi, bhindi, baigan, gajar, matar,
            anda/ande, bread/double roti, biscuit/biskut, maggi, chips/wafer

            BRAND NAMES (fix mispronunciations, keep brand name as-is):
            Tata namak/nimak = Tata Salt, Parle G/Parle ji = Parle-G, Surf/Surf Excel,
            Vim, Lifebuoy/Laibuoy, Colgate/Kolgate, Amul/Amool, Britannia/Britania,
            Fortune/Farchun, Aashirvaad/Ashirwad, Patanjali/Patanjli, Haldiram/Haldirams

            LOCAL QUANTITY TERMS:
            pav/paav = 250g, adha/aadha kilo = 500g, savaa/sawa kilo = 1.25kg,
            dedh/deedh kilo = 1.5kg, dhai/dhaai kilo = 2.5kg, paune/paunai = 0.75x,
            dozen/darjan = 12pc, dabba/dibba = box, peti = crate/case,
            packet/packit = packet, bottle/botal = bottle, tin/dabba = tin/can
            kilo=kg, gram/garam=g, piece/pees=pc, litre/liter=L, packet=pkt

            NOISY ENVIRONMENT HANDLING:
            The speech text may contain recognition errors from a noisy Indian shop environment.
            - Fix obvious misspellings from speech recognition
            - Recognize garbled brand names from context
            - Ignore filler words: "aur", "phir", "hmm", "ok", "haan", "toh", "woh", "ye", "yeh"
            - Ignore conversational fragments that aren't item entries
            - If speech contains "bas" or "done" or "ho gaya", ignore those (means "that's all")

            MULTIPLE ITEMS: The shopkeeper may dictate many items in one go. Extract ALL of them.
            If no valid items found, return an empty array [].
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
        return try {
            val gson = com.google.gson.Gson()
            val jsonElement = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
                ?: return Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")

            val sellerName = jsonElement.get("sellerName")?.asString ?: ""
            val billNumber = jsonElement.get("billNumber")?.asString ?: ""
            val totalAmount = jsonElement.get("totalAmount")?.asDouble ?: 0.0
            val subtotal = jsonElement.get("subtotal")?.asDouble ?: 0.0
            val discountAmount = jsonElement.get("discountAmount")?.asDouble ?: 0.0
            val discountPercent = jsonElement.get("discountPercent")?.asDouble ?: 0.0
            val taxAmount = jsonElement.get("taxAmount")?.asDouble ?: 0.0
            val taxPercent = jsonElement.get("taxPercent")?.asDouble ?: 0.0

            val itemsArray = jsonElement.getAsJsonArray("items")
            val items = if (itemsArray != null && itemsArray.size() > 0) {
                itemsArray.mapNotNull { element ->
                    try {
                        val obj = element.asJsonObject
                        val name = obj.get("name")?.asString ?: return@mapNotNull null
                        if (name.isBlank()) return@mapNotNull null
                        val quantity = obj.get("quantity")?.asDouble ?: 1.0
                        val unit = obj.get("unit")?.asString ?: "pc"
                        // Try "price" first, then "amount", then "total"
                        val price = obj.get("price")?.asDouble
                            ?: obj.get("amount")?.asDouble
                            ?: obj.get("total")?.asDouble
                            ?: 0.0
                        if (price < 0) return@mapNotNull null
                        BillItem(name = name.trim(), quantity = quantity, unit = unit.trim(), price = price)
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                emptyList()
            }

            // Use Gemini's totalAmount. Only fall back to sum if Gemini returned nothing.
            val finalTotal = when {
                totalAmount > 0.0 -> totalAmount
                items.isNotEmpty() -> items.sumOf { it.total } - discountAmount + taxAmount
                else -> 0.0
            }

            // If subtotal not returned by Gemini, derive it from items sum
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
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
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
                - "price": number (TOTAL price for this line item = quantity × per-unit rate)
            - "subtotal": number (sum of all item prices before discount/tax, 0 if not present)
            - "discountAmount": number (rupee discount on the bill, 0 if none)
            - "discountPercent": number (discount as percentage, 0 if none)
            - "taxAmount": number (total GST/tax amount = CGST + SGST or IGST, 0 if none)
            - "taxPercent": number (GST percentage rate, 0 if none)
            - "totalAmount": number (FINAL grand total / net amount the buyer has to pay)

            IMPORTANT: "totalAmount" must be the FINAL amount after discount and tax.
            If bill shows: subtotal=1000, discount=50, GST=90, then totalAmount=1040.
            If items are added by hand after the printed total, include them in items[] and add their amount to totalAmount.
        """.trimIndent()

        try {
            val response = flashModel.generateContent(prompt)
            val rawJson = response.text ?: "{}"
            parseBillJson(rawJson)
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
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
            val rawJson = response.text ?: "{}"
            parseBillJson(rawJson)
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
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
            val rawJson = response.text ?: "{}"
            val bill = parseBillJson(rawJson)

            // If combined approach returned empty, retry with text-only as fallback
            if (bill.items.isEmpty() && ocrText.isNotBlank()) {
                return@withContext parseOcrText(ocrText)
            }
            bill
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: try text-only parsing if image+text failed
            if (ocrText.isNotBlank()) {
                try { return@withContext parseOcrText(ocrText) } catch (_: Exception) {}
            }
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
        }
    }

    private fun buildImagePrompt(ocrText: String?): String {
        val ocrSection = if (!ocrText.isNullOrBlank()) {
            """

            ADDITIONAL CONTEXT — ML Kit OCR Text (pre-extracted from this image):
            The following text was extracted by on-device OCR. Use it as a REFERENCE to cross-check
            what you see in the image. The image is the primary source of truth, but this OCR text
            can help you identify items, numbers, and text that may be hard to read in the image.

            --- OCR TEXT START ---
            $ocrText
            --- OCR TEXT END ---

            IMPORTANT: Cross-reference the OCR text with what you see in the image.
            If the image is unclear but OCR text shows an item clearly, include it.
            If OCR text seems garbled but the image is clear, trust the image.
            Use BOTH sources together for maximum accuracy.
            """.trimIndent()
        } else ""

        return """
            You are an expert Indian wholesale bill reader for Dukaan AI app.
            Your job is to digitize this bill image with 100% accuracy — including ALL items,
            ALL charges, discount, GST, and any handwritten additions.

            STEP 1 — READ THE BILL CAREFULLY:
            Look at every single line of text in this bill image. Indian wholesale bills typically have:
            • Header: Shop/company name, address, phone, GSTIN
            • Bill info: Bill No., Date, Customer name
            • Item table with columns: Sr.No, Item/Product, Qty, Rate, Amount
            • Footer: Subtotal, Discount, GST (CGST+SGST or IGST), Net Amount, Signature
            • HANDWRITTEN additions: items or amounts written in pen AFTER the printed total line

            BILL FORMAT RECOGNITION:
            • Cash memo style: columns may be "Particulars | Qty | Rate | Amount"
            • Computer-generated bills: columns usually "Description | HSN | Qty | Rate | CGST | SGST | Total"
            • Handwritten bills: items may be in a simple list with amounts on the right side
            • Carbon copy bills: text may be faint or duplicated — read the clearest copy

            STEP 2 — EXTRACT SELLER NAME:
            Find the SHOP/COMPANY name at the TOP of the bill. Look for:
            • Large text or bold text at the top
            • Patterns: "M/s", "Shri", "& Sons", "Traders", "Enterprises", "Agency", "Store", "Bhandar", "भंडार", "ट्रेडर्स"
            • If GSTIN is present, the name is usually above it

            STEP 3 — EXTRACT EACH ITEM (INCLUDING HANDWRITTEN):
            For EVERY printed AND handwritten item line in the bill:
            • "name": exact product name as written (e.g. "Tata Salt 1kg", "Fortune Oil 1L", "Parle-G 50g")
            • "quantity": the quantity number
            • "unit": the unit (kg, g, L, ml, pc, pkt, box, dz, etc.)
            • "price": the TOTAL AMOUNT for this line item (quantity × per-unit rate)

            HANDWRITTEN ADDITIONS — CRITICAL:
            • Sellers in India often add extra items with a pen AFTER printing the bill
            • These may appear BELOW the printed total line, in empty space, or in the margin
            • They may be written as: "Dal 2kg - 180", "Soap 3pc 90" or just "180" beside a hand-written item
            • INCLUDE all such handwritten items in the items[] array
            • Adjust the totalAmount to include these additions

            PRICE DISAMBIGUATION:
            • If column header says "Rate" — that is per-unit rate, MULTIPLY by quantity to get price
            • If column header says "Amount", "Total", or "Amt" — that IS the line total, use directly as price
            • If bill shows "Qty: 5, Rate: 40, Amount: 200" → price = 200
            • If bill shows "Dal 5kg 450" → price = 450
            • If numbers appear without clear columns, the rightmost number is usually the amount

            STEP 4 — EXTRACT DISCOUNT:
            Look for discount fields near the bottom:
            • "Discount", "Disc", "छूट", "Less:"
            • It may be a flat rupee amount (e.g. "Discount: ₹50") or a percentage (e.g. "5%")
            • Fill "discountAmount" and "discountPercent" (use 0 if not present)

            STEP 5 — EXTRACT GST / TAX:
            Look for tax fields near the bottom:
            • CGST + SGST (add them together for taxAmount)
            • IGST (use directly as taxAmount)
            • "Tax", "GST", "VAT", "TDS"
            • Fill "taxAmount" = total tax rupees, "taxPercent" = GST rate % (use 0 if not present)

            STEP 6 — EXTRACT TOTALS:
            • "subtotal": sum of all item prices BEFORE discount/tax (look for "Subtotal", "Gross Total", "Sub Total")
            • "totalAmount": FINAL amount to be paid = subtotal - discount + tax
            • Look for: "Grand Total", "Net Amount", "Net Amt", "कुल", "Total", "G.Total", "Payable"
            • If "Round Off" adjusts the total by a few rupees, use the rounded final amount
            • If handwritten items were added after the printed total, add their amounts to totalAmount
            $ocrSection

            RETURN ONLY this JSON (no markdown, no explanation):
            {
              "sellerName": "Shop Name Here",
              "billNumber": "Bill/Invoice number or empty string",
              "items": [
                {"name": "Product Name", "quantity": 2.0, "unit": "kg", "price": 90.0},
                {"name": "Another Item", "quantity": 1.0, "unit": "pc", "price": 120.0}
              ],
              "subtotal": 210.0,
              "discountAmount": 10.0,
              "discountPercent": 0.0,
              "taxAmount": 18.0,
              "taxPercent": 9.0,
              "totalAmount": 218.0
            }

            CRITICAL RULES:
            - Read EVERY item (printed AND handwritten). Do NOT skip any line item.
            - Hindi/Devanagari text: transliterate to English (e.g. "चीनी" → "Cheeni/Sugar")
            - "price" MUST be the total amount for this line item, not per-unit rate
            - "totalAmount" MUST be the final net amount the buyer pays (after discount, after tax)
            - Abbreviations: "dz"=dozen, "pkt"=packet, "L"=litre, "pcs"=pieces
            - If handwritten, try your best to read accurately. Common confusions: 1 vs 7, 0 vs 6, 5 vs 8
            - Do NOT invent items that aren't in the image
            - If a line has ditto marks (") or "do", it means same item name as above
            - NEVER return an empty items array if the bill has any items at all
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
            You are an expert Indian wholesale bill reader for Dukaan AI app.
            You are given ${images.size} pages of the SAME bill.
            Read ALL pages and combine them into ONE complete bill, including discount and GST.

            STEP 1 — READ ALL PAGES:
            These are CONSECUTIVE PAGES of the same bill. Items may continue from one page to the next.
            The seller name and bill number typically appear on the first page.
            The grand total, discount, and GST typically appear on the last page.

            STEP 2 — EXTRACT SELLER NAME:
            Find the SHOP/COMPANY name at the TOP of the FIRST page.

            STEP 3 — EXTRACT EACH ITEM FROM ALL PAGES (INCLUDING HANDWRITTEN):
            For EVERY printed AND handwritten item line across ALL pages:
            • "name", "quantity", "unit", "price" (TOTAL price = quantity × rate)
            • Include any items written by hand after the printed total

            PRICE DISAMBIGUATION:
            • "Rate" column = per-unit rate, MULTIPLY by quantity
            • "Amount" or "Total" column = use directly as price

            STEP 4 — EXTRACT DISCOUNT (from last page footer):
            • "discountAmount": flat rupee discount (0 if none)
            • "discountPercent": discount percentage (0 if none)

            STEP 5 — EXTRACT GST/TAX (from last page footer):
            • "taxAmount": CGST + SGST or IGST total (0 if none)
            • "taxPercent": GST rate % (0 if none)

            STEP 6 — EXTRACT TOTALS:
            • "subtotal": item sum before discount/tax (0 if not shown)
            • "totalAmount": FINAL net amount payable (after discount, after tax)
            $ocrSection

            RETURN ONLY this JSON (no markdown, no explanation):
            {
              "sellerName": "Shop Name Here",
              "billNumber": "Bill/Invoice number or empty string",
              "items": [
                {"name": "Product Name", "quantity": 2.0, "unit": "kg", "price": 90.0}
              ],
              "subtotal": 0.0,
              "discountAmount": 0.0,
              "discountPercent": 0.0,
              "taxAmount": 0.0,
              "taxPercent": 0.0,
              "totalAmount": 210.0
            }

            CRITICAL RULES:
            - Combine ALL items from ALL pages into one list — do NOT skip any page
            - Do NOT duplicate items that appear as carry-forward totals between pages
            - Include handwritten items added after the printed total
            - Hindi/Devanagari text: transliterate to English
            - "price" MUST be the total amount for this line item, not per-unit rate
            - "totalAmount" MUST be the final net amount (after discount, after tax)
            - Do NOT invent items that aren't in the images
            - NEVER return an empty items array if the bill has any items at all
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
            val rawJson = response.text ?: "{}"
            val bill = parseBillJson(rawJson)

            // Fallback if empty items
            if (bill.items.isEmpty() && combinedOcr.isNotBlank()) {
                return@withContext parseOcrText(combinedOcr)
            }
            bill
        } catch (e: Exception) {
            e.printStackTrace()
            if (combinedOcr.isNotBlank()) {
                try { return@withContext parseOcrText(combinedOcr) } catch (_: Exception) {}
            }
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
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
        val prompt = """
            You are an AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            The user is looking at a scanned bill and wants to ask you about it.

            Here is the digitized bill data:
            $billJson

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
            You are an AI for Dukaan AI, an Indian shop billing app used by kirana/grocery shopkeepers.
            Extract wholesale order items from this shopkeeper's speech: "$speechText"

            Return ONLY a JSON array of objects with: name (string), quantity (number), unit (string).

            ITEM NAMES: $nameInstruction
            Brand names should always stay as-is (Parle-G, Amul, Tata Salt, etc.)

            LOCAL QUANTITY TERMS:
            pav/paav = 0.25 (e.g. 250g), adha/aadha kilo = 0.5 (e.g. 500g), savaa/sawa kilo = 1.25,
            dedh/deedh kilo = 1.5, dhai/dhaai kilo = 2.5, paune/paunai = 0.75x,
            dozen/darjan = 12, dabba/dibba = box, peti = crate/case,
            packet/packit = packet, bottle/botal = bottle, tin/dabba = tin/can
            kilo=kg, gram/garam=g, piece/pees=pc, litre/liter=L, packet=pkt
            If term implies a number like "dozen", use that number for "quantity" and "pc" or item name for unit.

            NOISY ENVIRONMENT HANDLING:
            The speech text may contain recognition errors from a noisy Indian shop environment.
            - Fix obvious misspellings from speech recognition
            - Recognize garbled brand names from context
            - Ignore filler words: "aur", "phir", "hmm", "ok", "haan", "toh", "woh", "ye", "yeh"
            - Ignore conversational fragments that aren't item entries
            - If speech contains "bas" or "done" or "ho gaya", ignore those (means "that's all")

            EXAMPLES:
            - "Sugar 50 kilo" → [{"name": "Sugar", "quantity": 50.0, "unit": "kg"}]
            - "Lux soap 2 carton" → [{"name": "Lux soap", "quantity": 2.0, "unit": "carton"}]
            - "1 dozen banana" → [{"name": "Banana", "quantity": 12.0, "unit": "pc"}]
            - "Adha kilo cheeni" → [{"name": "Sugar", "quantity": 0.5, "unit": "kg"}]

            MULTIPLE ITEMS: The shopkeeper may dictate many items in one go. Extract ALL of them.
            If no valid items found, return an empty array [].
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
            "\n\nON-DEVICE OCR TEXT (cross-reference with image):\n\"\"\"$ocrText\"\"\""
        else ""

        val prompt = """
            You are an AI for Dukaan AI, an Indian kirana shop billing app.
            This is a photo of a customer's HANDWRITTEN SHOPPING LIST with items and prices.$ocrSection

            Extract ALL items from this handwritten list. Return ONLY a JSON array.
            Each item: {"name": string, "quantity": number, "unit": string, "price": number}

            RULES:
            - Extract "price" if written on the list (as TOTAL price for that line item)
            - If price is not written, set "price" = 0
            - If price is per-unit (e.g. "₹50/kg"), calculate total: quantity × per-unit-rate
            - Keep item names as written — do NOT translate Hindi/Hinglish to English
            - Fix obvious spelling mistakes but keep the language
            - Default quantity = 1 if not written
            - Default unit = "pc" if not clear; use "kg", "g", "L", "ml", "pkt", "box", "dozen" where obvious

            PRICE EXAMPLES:
            - "Sugar 2kg ₹80" → price = 80 (total written)
            - "Rice 5kg @₹60/kg" → price = 300 (5 × 60)
            - "Soap 3pc 30rs each" → price = 90 (3 × 30)
            - "Milk 1L" (no price) → price = 0

            LOCAL TERMS:
            pav/paav = 250g, adha kilo = 500g, dedh = 1.5, darjan/dozen = 12 pc
            ek = 1, do/dono = 2, teen = 3, chaar = 4, paanch = 5

            Return [] if no items found.
        """.trimIndent()

        try {
            val response = flashModel.generateContent(
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
