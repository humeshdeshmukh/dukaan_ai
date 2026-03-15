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
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "{}"
            com.google.gson.Gson().fromJson(jsonString, Bill::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0, sellerName = "Unknown Seller")
        }
    }

    override suspend fun parseBillImage(image: Bitmap): Bill = withContext(Dispatchers.IO) {
        val prompt = """
            You are an expert Indian wholesale bill reader for Dukaan AI app.
            Your job is to digitize this bill image with 100% accuracy.

            STEP 1 — READ THE BILL CAREFULLY:
            Look at every single line of text in this bill image. Indian wholesale bills typically have:
            • Header: Shop/company name, address, phone, GSTIN
            • Bill info: Bill No., Date, Customer name
            • Item table with columns: Sr.No, Item/Product, Qty, Rate, Amount
            • Footer: Total, Discount, Net Amount, Signature

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
              - If bill shows "Qty: 5, Rate: 40, Amount: 200" → price = 200 (the amount/total column)
              - If bill shows "Dal 5kg 450" → price = 450 (the total for this line)
              - If bill shows only per-unit rate → multiply by quantity to get total

            STEP 4 — EXTRACT TOTAL:
            • "totalAmount": the GRAND TOTAL / NET AMOUNT from the bottom of the bill
            • If multiple totals exist (subtotal, tax, grand total), use the final/net amount
            • If no total found, calculate: sum of (quantity × price) for all items

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
            - Read EVERY item. Do NOT skip any line item.
            - Hindi/Devanagari text: transliterate to English (e.g. "चीनी" → "Cheeni/Sugar")
            - "price" MUST be the total amount for this line item, not per-unit rate
            - Abbreviations: "dz"=dozen, "pkt"=packet, "L"=litre, "pcs"=pieces
            - If handwritten, try your best to read accurately
            - Do NOT invent items that aren't in the image
        """.trimIndent()

        try {
            val inputContent = content {
                image(image)
                text(prompt)
            }
            val response = generativeModel.generateContent(inputContent)
            val rawJson = response.text ?: "{}"
            val jsonString = rawJson
                .replace("```json", "")
                .replace("```", "")
                .trim()
            com.google.gson.Gson().fromJson(jsonString, Bill::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
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
