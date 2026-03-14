package com.dukaan.core.network.ai

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
    suspend fun parseOrderSpeech(speechText: String): List<OrderItem>
}

@Singleton
class GeminiBillingServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
) : GeminiBillingService {

    override suspend fun parseBillingSpeech(speechText: String): List<BillItem> = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for a shop management app called Dukaan AI.
            Extract items from this shopkeeper's speech: "$speechText"
            Return ONLY a JSON array of objects with: name (string), quantity (number), unit (string), price (number - total price for that item).
            Example input: "Sugar 2 kilo 80, Soap 1 piece 30"
            Example output: [{"name": "Sugar", "quantity": 2, "unit": "kg", "price": 80}, {"name": "Soap", "quantity": 1, "unit": "pc", "price": 30}]
            If no price is mentioned, use 0.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "[]"
            // Simple manual parsing or use Gson
            com.google.gson.Gson().fromJson(jsonString, Array<BillItem>::class.java).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun parseOcrText(rawText: String): Bill = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI. Extract bill details from this OCR text: "$rawText"
            Return ONLY a JSON object with:
            "items": array of {name, quantity, unit, price},
            "totalAmount": number
            Focus on items, quantities, and their total prices.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "{}"
            com.google.gson.Gson().fromJson(jsonString, Bill::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            Bill(items = emptyList(), totalAmount = 0.0)
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
