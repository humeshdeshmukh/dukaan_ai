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
}

@Singleton
class GeminiBillingServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
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

            val recalcTotal = if (totalAmount <= 0.0 && items.isNotEmpty()) {
                items.sumOf { it.total }
            } else {
                totalAmount
            }

            Bill(items = items, totalAmount = recalcTotal, sellerName = sellerName.trim(), billNumber = billNumber.trim())
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
        }
    }

    override suspend fun parseOcrText(rawText: String): Bill = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI, a shop management app for Indian shopkeepers.
            Extract bill/invoice details from this OCR text of a wholesale purchase bill.

            OCR Text: "$rawText"

            Return ONLY a valid JSON object with these fields:
            - "sellerName": string (the wholesaler/seller/company name)
            - "billNumber": string (invoice/bill number, empty string if not found)
            - "items": array of objects, each with:
                - "name": string (product name)
                - "quantity": number
                - "unit": string (kg, pc, box, packet, litre, dozen, etc.)
                - "price": number (TOTAL price for this line item, i.e. quantity × per-unit rate)
            - "totalAmount": number (grand total)
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val rawText = response.text ?: "{}"
            parseBillJson(rawText)
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
            val response = generativeModel.generateContent(inputContent)
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
            val response = generativeModel.generateContent(inputContent)
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
            Your job is to digitize this bill image with 100% accuracy.

            STEP 1 — READ THE BILL CAREFULLY:
            Look at every single line of text in this bill image. Indian wholesale bills typically have:
            • Header: Shop/company name, address, phone, GSTIN
            • Bill info: Bill No., Date, Customer name
            • Item table with columns: Sr.No, Item/Product, Qty, Rate, Amount
            • Footer: Total, Discount, Net Amount, Signature

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

            STEP 3 — EXTRACT EACH ITEM:
            For EVERY item line in the bill:
            • "name": exact product name as written (e.g. "Tata Salt 1kg", "Fortune Oil 1L", "Parle-G 50g")
            • "quantity": the quantity number
            • "unit": the unit (kg, g, L, ml, pc, pkt, box, dz, etc.)
            • "price": the TOTAL AMOUNT for this line item (quantity × per-unit rate)

            PRICE DISAMBIGUATION:
            • If column header says "Rate" — that is per-unit rate, MULTIPLY by quantity to get price
            • If column header says "Amount", "Total", or "Amt" — that IS the line total, use directly as price
            • If bill shows "Qty: 5, Rate: 40, Amount: 200" → price = 200
            • If bill shows "Dal 5kg 450" → price = 450
            • If numbers appear without clear columns, the rightmost number is usually the amount

            STEP 4 — EXTRACT TOTAL:
            • "totalAmount": the GRAND TOTAL / NET AMOUNT from the bottom of the bill
            • Look for: "Grand Total", "Net Amount", "Net Amt", "कुल", "Total", "G.Total"
            • If "Round Off" is present, use the rounded total
            • If multiple totals exist (subtotal, tax, grand total), use the final/net amount
            • If no total found, calculate: sum of all item prices
            $ocrSection

            RETURN ONLY this JSON (no markdown, no explanation):
            {
              "sellerName": "Shop Name Here",
              "billNumber": "Bill/Invoice number or empty string",
              "items": [
                {"name": "Product Name", "quantity": 2.0, "unit": "kg", "price": 90.0},
                {"name": "Another Item", "quantity": 1.0, "unit": "pc", "price": 120.0}
              ],
              "totalAmount": 210.0
            }

            CRITICAL RULES:
            - Read EVERY item. Do NOT skip any line item. You MUST return at least 1 item if there is any text on the bill.
            - Hindi/Devanagari text: transliterate to English (e.g. "चीनी" → "Cheeni/Sugar")
            - "price" MUST be the total amount for this line item, not per-unit rate
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
            Read ALL pages and combine them into ONE complete bill.

            STEP 1 — READ ALL PAGES:
            These are CONSECUTIVE PAGES of the same bill. Items may continue from one page to the next.
            The seller name and bill number typically appear on the first page.
            The grand total typically appears on the last page.

            STEP 2 — EXTRACT SELLER NAME:
            Find the SHOP/COMPANY name at the TOP of the FIRST page. Look for:
            • Large text or bold text at the top
            • Patterns: "M/s", "Shri", "& Sons", "Traders", "Enterprises", "Agency", "Store"

            STEP 3 — EXTRACT EACH ITEM FROM ALL PAGES:
            For EVERY item line across ALL pages:
            • "name": exact product name as written
            • "quantity": the quantity number
            • "unit": the unit (kg, g, L, ml, pc, pkt, box, dz, etc.)
            • "price": the TOTAL AMOUNT for this line item (quantity × per-unit rate)

            PRICE DISAMBIGUATION:
            • If column header says "Rate" — that is per-unit rate, MULTIPLY by quantity
            • If column header says "Amount" or "Total" — use directly as price

            STEP 4 — EXTRACT TOTAL:
            • "totalAmount": the GRAND TOTAL / NET AMOUNT (usually on the last page)
            • If no total found, calculate: sum of all item prices
            $ocrSection

            RETURN ONLY this JSON (no markdown, no explanation):
            {
              "sellerName": "Shop Name Here",
              "billNumber": "Bill/Invoice number or empty string",
              "items": [
                {"name": "Product Name", "quantity": 2.0, "unit": "kg", "price": 90.0}
              ],
              "totalAmount": 210.0
            }

            CRITICAL RULES:
            - Combine ALL items from ALL pages into one list — do NOT skip any page
            - Do NOT duplicate items that appear as carry-forward totals between pages
            - Hindi/Devanagari text: transliterate to English
            - "price" MUST be the total amount for this line item, not per-unit rate
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
            val response = generativeModel.generateContent(inputContent)
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
            You are an AI for Dukaan AI. Extract wholesale order list from this speech: "$speechText"
            $nameInstruction
            Brand names should always stay as-is.
            Return ONLY a JSON array of objects with: name (string), quantity (number), unit (string).
            Example input: "Sugar 50 kilo, Lux soap 2 carton"
            Example output: [{"name": "Sugar", "quantity": 50, "unit": "kg"}, {"name": "Lux soap", "quantity": 2, "unit": "carton"}]
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val rawText = response.text ?: "[]"
            val jsonString = extractJsonArray(rawText)
            com.google.gson.Gson().fromJson(jsonString, Array<OrderItem>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
