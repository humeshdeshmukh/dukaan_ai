package com.dukaan.core.network.ai

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface GeminiTranslationService {
    suspend fun translateStrings(
        englishStringsJson: String,
        targetLanguageName: String,
        targetLanguageCode: String
    ): String
}

@Singleton
class GeminiTranslationServiceImpl @Inject constructor(
    private val generativeModel: GenerativeModel
) : GeminiTranslationService {

    override suspend fun translateStrings(
        englishStringsJson: String,
        targetLanguageName: String,
        targetLanguageCode: String
    ): String = withContext(Dispatchers.IO) {
        val hinglishNote = if (targetLanguageCode == "hi-en") {
            "\nIMPORTANT: Hinglish means Hindi-English mix written in Roman/Latin script (NOT Devanagari). " +
            "Use natural code-switching like Indian shopkeepers speak. " +
            "Example: 'Aaj ki Sales' not 'आज की बिक्री'. Keep common English words as-is."
        } else ""

        val prompt = """
You are a professional translator for a shop management mobile app called "Dukaan AI" used by Indian shopkeepers.

Translate ALL the JSON values below from English to $targetLanguageName.
$hinglishNote

RULES:
1. Translate ONLY the values, keep all JSON keys exactly the same
2. Keep translations SHORT - these are mobile UI labels (buttons, headers, placeholders)
3. Use simple, everyday $targetLanguageName words a shopkeeper would understand
4. Do NOT translate: proper nouns (GSTIN, UPI, IFSC, PDF), currency symbols (₹), technical terms commonly used in English (AI, PDF, OCR)
5. Keep app name "Dukaan AI" as-is
6. Keep format specifiers like %s exactly as-is
7. Return ONLY valid JSON with no markdown formatting, no code fences, no explanation

English strings JSON:
$englishStringsJson
        """.trimIndent()

        val response = generativeModel.generateContent(prompt)
        response.text?.let { raw ->
            raw.replace("```json", "")
                .replace("```", "")
                .trim()
        } ?: englishStringsJson
    }
}
