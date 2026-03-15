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
    suspend fun getCustomerInsight(customerName: String, balance: Double, transactionSummary: String, languageCode: String = "en"): String
    suspend fun generatePaymentReminder(customerName: String, amount: Double, shopName: String, languageCode: String = "en"): String
    suspend fun chatAboutKhata(khataContext: String, userMessage: String, languageCode: String = "en"): String
    suspend fun parseTransactionSpeech(speechText: String): TransactionParseResult
    suspend fun getOverallKhataInsight(khataOverview: String, languageCode: String = "en"): String
    suspend fun chatAboutOverallKhata(khataOverview: String, userMessage: String, languageCode: String = "en"): String
}

@Singleton
class GeminiKhataServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
) : GeminiKhataService {

    private fun languageInstruction(languageCode: String): String {
        return when (languageCode) {
            "en" -> "Respond in simple, easy-to-understand Hinglish (Hindi-English mix)."
            "hi" -> "Respond in simple Hindi. Use easy words."
            else -> {
                val langName = when (languageCode) {
                    "bn" -> "Bengali"; "te" -> "Telugu"; "mr" -> "Marathi"; "ta" -> "Tamil"
                    "ur" -> "Urdu"; "gu" -> "Gujarati"; "kn" -> "Kannada"; "ml" -> "Malayalam"
                    "or" -> "Odia"; "pa" -> "Punjabi"; "as" -> "Assamese"; "mai" -> "Maithili"
                    "ne" -> "Nepali"; "kok" -> "Konkani"; "doi" -> "Dogri"; "sa" -> "Sanskrit"
                    "ks" -> "Kashmiri"; "sd" -> "Sindhi"; "mni" -> "Manipuri"; "brx" -> "Bodo"
                    "sat" -> "Santali"
                    else -> "Hindi"
                }
                "Respond in simple $langName language. Use easy, everyday words."
            }
        }
    }

    override suspend fun getCustomerInsight(
        customerName: String,
        balance: Double,
        transactionSummary: String,
        languageCode: String
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

            Keep it very concise and practical. Use ₹ for currency.
            Do not use any markdown formatting like ** or * or #. Use plain text only.
            ${languageInstruction(languageCode)}
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
        shopName: String,
        languageCode: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI for Dukaan AI, a shop management app for Indian shopkeepers.
            Generate a polite WhatsApp payment reminder message.

            Customer Name: $customerName
            Outstanding Amount: ₹$amount
            Shop Name: $shopName

            Generate a short, polite, and professional reminder message.
            The message should:
            - Be friendly and respectful
            - Mention the amount clearly
            - Request payment politely
            - Be suitable for WhatsApp (use emojis sparingly)
            - Be 3-4 lines max

            Return ONLY the message text, nothing else.
            Do not use any markdown formatting like ** or * or #. Plain text only.
            ${languageInstruction(languageCode)}
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
        userMessage: String,
        languageCode: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            The user is viewing a customer's khata (ledger) and wants to ask about it.

            Khata Data:
            $khataContext

            User's question: "$userMessage"

            Respond helpfully and concisely in simple, easy-to-understand language.
            Keep responses short and practical — this is for a busy shopkeeper.
            Do not use any markdown formatting like ** or * or #. Plain text only.
            Use ₹ for currency amounts.
            ${languageInstruction(languageCode)}
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

    override suspend fun getOverallKhataInsight(
        khataOverview: String,
        languageCode: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a smart AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            Analyze the overall khata (ledger) data and provide a brief business insight.

            Overall Khata Data:
            $khataOverview

            Provide a 3-4 line insight covering:
            - Overall business health (receivables vs payables)
            - Identify risky customers (high balance, long delays)
            - Actionable suggestion to improve cash flow
            - Any pattern you notice

            Keep it very concise and practical. Use ₹ for currency.
            Do not use any markdown formatting like ** or * or #. Use plain text only.
            ${languageInstruction(languageCode)}
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Unable to generate insight at the moment."
        } catch (e: Exception) {
            e.printStackTrace()
            "Unable to generate insight. Please try again."
        }
    }

    override suspend fun chatAboutOverallKhata(
        khataOverview: String,
        userMessage: String,
        languageCode: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are an AI assistant for Dukaan AI, a shop management app for Indian shopkeepers.
            The user wants to ask about their overall khata (ledger) business data.

            Overall Khata Data:
            $khataOverview

            User's question: "$userMessage"

            Respond helpfully and concisely in simple, easy-to-understand language.
            Keep responses short and practical — this is for a busy shopkeeper.
            Do not use any markdown formatting like ** or * or #. Plain text only.
            Use ₹ for currency amounts.
            ${languageInstruction(languageCode)}
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Sorry, I couldn't process that. Please try again."
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message ?: "Failed to get response"}"
        }
    }
}
