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
    suspend fun parseBillingSpeech(speechText: String): List<BillItem>
    suspend fun parseOcrText(rawText: String): Bill
    suspend fun parseBillImage(image: Bitmap): Bill
    suspend fun chatAboutBill(billJson: String, userMessage: String, image: Bitmap? = null, languageCode: String = "en"): String
    suspend fun parseOrderSpeech(speechText: String): List<OrderItem>
}

@Singleton
class GeminiBillingServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
) : GeminiBillingService {

    override suspend fun parseBillingSpeech(speechText: String): List<BillItem> = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for a shop management app called Dukaan AI.
            Extract items from this Indian shopkeeper's speech: "$speechText"

            Return ONLY a JSON array of objects with these fields:
            - name (string): item name
            - quantity (number): the quantity spoken
            - unit (string): the unit for quantity (g, kg, ml, L, pc, pcs, dozen, packet, box etc.)
            - price (number): PER-UNIT price (rate per priceUnit)
            - priceUnit (string): the unit the price is quoted for (e.g. "kg" if price is per kg)

            IMPORTANT RULES:
            - "price" must be PER-UNIT rate, NOT the total.
            - If shopkeeper says "500 gram sugar 60 rupees per kg" → quantity=500, unit="g", price=60, priceUnit="kg"
            - If shopkeeper says "2 kilo rice 40 rupees" → quantity=2, unit="kg", price=40, priceUnit="kg" (same unit)
            - If shopkeeper says "Soap 3 piece 30 rupees each" → quantity=3, unit="pc", price=30, priceUnit="pc"
            - If shopkeeper says "doodh 500ml 30 rupee per litre" → quantity=500, unit="ml", price=30, priceUnit="L"
            - If price seems like a TOTAL (e.g. "sugar 2kg 80 rupees total") → price=40, priceUnit="kg" (divide total by qty)
            - If unclear, assume price is per unit of the same unit as quantity, set priceUnit same as unit
            - Hindi/Hinglish words: kilo=kg, gram=g, piece/ek=pc, litre=L, dozen=dozen, packet=pkt

            Example input: "500 gram sugar 60 rupees per kg, 2 sabun 30 rupees each, doodh 1 litre 68 rupees"
            Example output: [{"name":"Sugar","quantity":500,"unit":"g","price":60,"priceUnit":"kg"},{"name":"Sabun","quantity":2,"unit":"pc","price":30,"priceUnit":"pc"},{"name":"Doodh","quantity":1,"unit":"L","price":68,"priceUnit":"L"}]
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "[]"
            com.google.gson.Gson().fromJson(jsonString, Array<BillItem>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
                - "price": number (PER-UNIT price)
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
            • "price": the PER-UNIT rate/price (NOT the line total)
              - If bill shows "Qty: 5, Rate: 40, Amount: 200" → price = 40 (the rate column)
              - If bill shows "Dal 5kg 450" with no rate column → price = 450/5 = 90
              - If bill shows only total amount per line → divide by quantity to get per-unit price

            STEP 4 — EXTRACT TOTAL:
            • "totalAmount": the GRAND TOTAL / NET AMOUNT from the bottom of the bill
            • If multiple totals exist (subtotal, tax, grand total), use the final/net amount
            • If no total found, calculate: sum of (quantity × price) for all items

            RETURN ONLY this JSON (no markdown, no explanation):
            {
              "sellerName": "Shop Name Here",
              "billNumber": "Bill/Invoice number or empty string",
              "items": [
                {"name": "Product Name", "quantity": 2.0, "unit": "kg", "price": 45.0},
                {"name": "Another Item", "quantity": 1.0, "unit": "pc", "price": 120.0}
              ],
              "totalAmount": 210.0
            }

            CRITICAL RULES:
            - Read EVERY item. Do NOT skip any line item.
            - Hindi/Devanagari text: transliterate to English (e.g. "चीनी" → "Cheeni/Sugar")
            - "price" MUST be per-unit rate, never the line total
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
            "en" -> "Respond in simple Hinglish (Hindi-English mix)."
            "hi" -> "Respond in simple Hindi."
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

    override suspend fun parseOrderSpeech(speechText: String): List<OrderItem> = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI. Extract wholesale order list from this speech: "$speechText"
            Return ONLY a JSON array of objects with: name (string), quantity (number), unit (string).
            Example input: "Sugar 50 kilo, Lux soap 2 carton"
            Example output: [{"name": "Sugar", "quantity": 50, "unit": "kg"}, {"name": "Lux soap", "quantity": 2, "unit": "carton"}]
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "[]"
            com.google.gson.Gson().fromJson(jsonString, Array<OrderItem>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
