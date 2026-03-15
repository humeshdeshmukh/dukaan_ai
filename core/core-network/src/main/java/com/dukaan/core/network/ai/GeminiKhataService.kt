package com.dukaan.core.network.ai

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionParseResult(
    val amount: Double = 0.0,
    val type: String = "BAKI",
    val notes: String? = null,
    val confidence: Double = 0.0
)

interface GeminiKhataService {
    suspend fun getCustomerInsight(customerName: String, balance: Double, transactionSummary: String): String
    suspend fun generatePaymentReminder(customerName: String, amount: Double, shopName: String): String
    suspend fun chatAboutKhata(khataContext: String, userMessage: String): String
    suspend fun parseTransactionSpeech(speechText: String): TransactionParseResult
}

@Singleton
class GeminiKhataServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
) : GeminiKhataService {

    override suspend fun getCustomerInsight(
        customerName: String,
        balance: Double,
        transactionSummary: String
    ): String = withContext(Dispatchers.IO) {
        val balanceType = if (balance < 0) "owes you ₹${Math.abs(balance)}" else "you owe them ₹${Math.abs(balance)}"
        val prompt = """
            You are a smart AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            Analyze this customer's transaction history and provide a brief, actionable insight.

            Customer: $customerName
            Balance: $balanceType
            Transaction History:
            $transactionSummary

            Provide a 2-3 line insight covering:
            - Payment behavior pattern (regular/irregular, on-time/late)
            - Credit risk assessment (low/medium/high)
            - Actionable suggestion for the shopkeeper

            Keep it very concise and practical. Respond in English. Use ₹ for currency.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Unable to generate insight at the moment."
        } catch (e: Exception) {
            e.printStackTrace()
            "Unable to generate insight. Please try again."
        }
    }

    override suspend fun generatePaymentReminder(
        customerName: String,
        amount: Double,
        shopName: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI, a shop management app for Indian shopkeepers.
            Generate a polite WhatsApp payment reminder message.

            Customer Name: $customerName
            Outstanding Amount: ₹$amount
            Shop Name: $shopName

            Generate a short, polite, and professional reminder message in Hindi-English mix (Hinglish).
            The message should:
            - Be friendly and respectful
            - Mention the amount clearly
            - Request payment politely
            - Be suitable for WhatsApp (use emojis sparingly)
            - Be 3-4 lines max

            Return ONLY the message text, nothing else.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Namaste $customerName ji, aapka ₹$amount baaki hai $shopName mein. Jab convenient ho payment kar dijiye. Dhanyavaad!"
        } catch (e: Exception) {
            e.printStackTrace()
            "Namaste $customerName ji, aapka ₹$amount baaki hai $shopName mein. Jab convenient ho payment kar dijiye. Dhanyavaad!"
        }
    }

    override suspend fun chatAboutKhata(
        khataContext: String,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            The user is viewing a customer's khata (ledger) and wants to ask about it.

            Khata Data:
            $khataContext

            User's question: "$userMessage"

            Respond helpfully and concisely in the same language the user asks (Hindi or English).
            Keep responses short and practical — this is for a busy shopkeeper.
            Use ₹ for currency amounts.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Sorry, I couldn't process that. Please try again."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message ?: "Failed to get response"}"
        }
    }

    override suspend fun parseTransactionSpeech(
        speechText: String
    ): TransactionParseResult = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI, a shop management app for Indian shopkeepers.
            Parse this speech into a transaction entry: "$speechText"

            The shopkeeper uses these terms:
            - JAMA / received / payment / diya / mila / bheja = Payment received (JAMA)
            - BAKI / credit / udhar / liya / kharcha = Credit given (BAKI)

            Return ONLY a JSON object with:
            - "amount": number (the transaction amount)
            - "type": "JAMA" or "BAKI"
            - "notes": string or null (any additional context like customer name, item, date)
            - "confidence": number 0-1 (how confident you are in the parsing)

            Examples:
            "500 rupees mila" → {"amount": 500, "type": "JAMA", "notes": null, "confidence": 0.9}
            "Raju ne 200 ka udhar liya chai ke liye" → {"amount": 200, "type": "BAKI", "notes": "chai ke liye", "confidence": 0.95}
            "1000 payment received" → {"amount": 1000, "type": "JAMA", "notes": null, "confidence": 0.95}

            If you can't determine the type, default to "BAKI" with low confidence.
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonString = response.text?.filterNot { it == '`' }?.removePrefix("json")?.trim() ?: "{}"
            com.google.gson.Gson().fromJson(jsonString, TransactionParseResult::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            val extracted = speechText.filter { it.isDigit() || it == '.' }
            TransactionParseResult(
                amount = extracted.toDoubleOrNull() ?: 0.0,
                type = "BAKI",
                notes = null,
                confidence = 0.3
            )
        }
    }
}
