package com.dukaan.core.network.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiTranslation"

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

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    override suspend fun translateStrings(
        englishStringsJson: String,
        targetLanguageName: String,
        targetLanguageCode: String
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "translateStrings called: lang=$targetLanguageName, code=$targetLanguageCode")

        // Parse all English strings into a map
        val allEntries: Map<String, String> = try {
            gson.fromJson(englishStringsJson, mapType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse English JSON", e)
            return@withContext englishStringsJson
        }

        Log.d(TAG, "Total fields to translate: ${allEntries.size}")

        // Split into chunks of ~30 fields to avoid output token truncation
        // (Devanagari/non-Latin scripts use 2-4x more tokens per character)
        val chunks = allEntries.entries.chunked(30)
        val mergedResult = mutableMapOf<String, String>()
        var successChunks = 0

        val hinglishNote = if (targetLanguageCode == "hi-en") {
            "\nIMPORTANT: Hinglish = Hindi-English mix in Roman/Latin script (NOT Devanagari). " +
            "Use natural code-switching like Indian shopkeepers speak. " +
            "Example: 'Aaj ki Sales' not 'आज की बिक्री'. Keep common English words as-is."
        } else ""

        for ((index, chunk) in chunks.withIndex()) {
            val chunkMap = chunk.associate { it.key to it.value }
            val chunkJson = gson.toJson(chunkMap)

            val prompt = """
You are a professional translator for a shop management mobile app called "Dukaan AI" used by Indian shopkeepers.
Translate ALL the JSON values below from English to $targetLanguageName.
$hinglishNote

RULES:
1. Translate ONLY the values, keep all JSON keys exactly the same
2. Keep translations SHORT - these are mobile UI labels
3. Use simple, everyday $targetLanguageName words a shopkeeper would understand
4. Do NOT translate: proper nouns (GSTIN, UPI, IFSC, PDF), currency symbols, technical terms commonly used in English (AI, PDF, OCR)
5. Keep app name "Dukaan AI" as-is
6. Return ONLY valid JSON — no markdown, no code fences, no explanation

JSON to translate:
$chunkJson
            """.trimIndent()

            try {
                Log.d(TAG, "Translating chunk ${index + 1}/${chunks.size} (${chunkMap.size} fields)")
                val response = generativeModel.generateContent(prompt)
                val rawText = response.text
                if (rawText == null) {
                    Log.w(TAG, "Chunk ${index + 1}: Gemini returned null text")
                    mergedResult.putAll(chunkMap)
                    continue
                }
                Log.d(TAG, "Chunk ${index + 1} raw response length: ${rawText.length}")
                val cleanJson = rawText
                    .replace("```json", "")
                    .replace("```JSON", "")
                    .replace("```", "")
                    .trim()
                val translated: Map<String, String> = gson.fromJson(cleanJson, mapType)
                Log.d(TAG, "Chunk ${index + 1}: translated ${translated.size} fields successfully")
                mergedResult.putAll(translated)
                successChunks++
            } catch (e: Exception) {
                Log.e(TAG, "Chunk ${index + 1} FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                // If a chunk fails, use the English values for those keys
                mergedResult.putAll(chunkMap)
            }
        }

        Log.d(TAG, "Translation complete: $successChunks/${chunks.size} chunks succeeded, merged ${mergedResult.size} fields")

        // Fill in any missing keys with English defaults
        for ((key, value) in allEntries) {
            if (key !in mergedResult) {
                mergedResult[key] = value
            }
        }

        // If no chunks succeeded, throw to signal failure (don't cache all-English as "translated")
        if (successChunks == 0) {
            throw RuntimeException("All ${chunks.size} translation chunks failed for $targetLanguageName")
        }

        gson.toJson(mergedResult)
    }
}
