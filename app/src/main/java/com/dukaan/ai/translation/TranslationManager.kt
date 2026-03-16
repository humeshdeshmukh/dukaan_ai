package com.dukaan.ai.translation

import android.util.Log
import com.dukaan.core.db.dao.TranslationDao
import com.dukaan.core.db.entity.TranslationCacheEntity
import com.dukaan.core.network.ai.GeminiTranslationService
import com.dukaan.core.ui.translation.AppStrings
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TranslationManager"

@Singleton
class TranslationManager @Inject constructor(
    private val translationDao: TranslationDao,
    private val translationService: GeminiTranslationService,
    private val gson: Gson
) {
    private val _currentStrings = MutableStateFlow(AppStrings())
    val currentStrings: StateFlow<AppStrings> = _currentStrings.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    // Version fingerprint: changes whenever AppStrings fields are added/renamed/changed.
    // Cached translations with a different fingerprint are stale and must be re-translated.
    private val englishJson: String by lazy { gson.toJson(AppStrings()) }
    private val stringsVersion: Int by lazy { englishJson.hashCode() }

    /**
     * Called on app startup. Loads cached translation immediately for fast UI,
     * then re-translates in background if cache is stale or missing.
     */
    suspend fun loadOrTranslate(languageCode: String) {
        Log.d(TAG, "loadOrTranslate: code=$languageCode")
        if (languageCode == "en") {
            _currentStrings.value = AppStrings()
            return
        }

        val cached = translationDao.getTranslation(languageCode)
        Log.d(TAG, "loadOrTranslate: cached=${cached != null}, cachedTimestamp=${cached?.timestamp}, stringsVersion=$stringsVersion")

        if (cached != null && cached.timestamp == stringsVersion.toLong()) {
            // Cache is fresh — use it directly
            try {
                val loaded = gson.fromJson(cached.translationsJson, AppStrings::class.java)
                _currentStrings.value = fillNullsWithDefaults(loaded)
                Log.d(TAG, "loadOrTranslate: loaded from fresh cache")
                return
            } catch (e: Exception) {
                Log.e(TAG, "loadOrTranslate: cache corrupted", e)
                // Cache corrupted, fall through to re-translate
            }
        } else if (cached != null) {
            // Cache is stale — load it as best-effort (partially translated > all English),
            // then re-translate below
            try {
                val loaded = gson.fromJson(cached.translationsJson, AppStrings::class.java)
                _currentStrings.value = fillNullsWithDefaults(loaded)
                Log.d(TAG, "loadOrTranslate: loaded stale cache as best-effort, will re-translate")
            } catch (e: Exception) {
                Log.e(TAG, "loadOrTranslate: stale cache also corrupted", e)
            }
        }

        // Cache missing or stale — translate via Gemini
        val langName = com.dukaan.core.db.SupportedLanguages.getByCode(languageCode).englishName
        Log.d(TAG, "loadOrTranslate: calling doTranslate for $langName")
        doTranslate(languageCode, langName)
    }

    /**
     * Called when user explicitly applies a language from Settings.
     */
    suspend fun translateAndApply(languageCode: String, languageName: String) {
        Log.d(TAG, "translateAndApply: code=$languageCode, name=$languageName")
        if (languageCode == "en") {
            _currentStrings.value = AppStrings()
            return
        }

        // Always force a fresh translation when user explicitly applies language.
        // Delete any cached translation first — a previous failed attempt may have
        // cached all-English values with the current version timestamp.
        try {
            translationDao.deleteTranslation(languageCode)
            Log.d(TAG, "translateAndApply: cleared old cache for $languageCode")
        } catch (_: Exception) {}

        doTranslate(languageCode, languageName)
    }

    private suspend fun doTranslate(languageCode: String, languageName: String) {
        Log.d(TAG, "doTranslate: starting for $languageName ($languageCode)")
        _isTranslating.value = true
        try {
            val translatedJson = translationService.translateStrings(
                englishStringsJson = englishJson,
                targetLanguageName = languageName,
                targetLanguageCode = languageCode
            )
            Log.d(TAG, "doTranslate: got translated JSON (length=${translatedJson.length})")

            val translated = gson.fromJson(translatedJson, AppStrings::class.java)
            if (translated == null) {
                Log.e(TAG, "doTranslate: gson.fromJson returned null!")
                return
            }

            // Fill any null fields (Gson may leave new fields null if not in response)
            val safeTranslated = fillNullsWithDefaults(translated)

            // Quick sanity check — if a known field is still English, log a warning
            Log.d(TAG, "doTranslate: sample field 'save' = '${safeTranslated.save}', 'settings' = '${safeTranslated.settings}'")

            _currentStrings.value = safeTranslated
            Log.d(TAG, "doTranslate: _currentStrings updated")

            // Cache it with current strings version as timestamp
            translationDao.upsertTranslation(
                TranslationCacheEntity(
                    languageCode = languageCode,
                    translationsJson = translatedJson,
                    timestamp = stringsVersion.toLong()
                )
            )
            Log.d(TAG, "doTranslate: cached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "doTranslate FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            // Translation failed — delete any stale cache so next attempt retries
            try {
                translationDao.deleteTranslation(languageCode)
            } catch (_: Exception) {}
        } finally {
            _isTranslating.value = false
        }
    }

    /**
     * Gson bypasses Kotlin default constructors, so fields missing from cached/translated JSON
     * become null instead of their English defaults. This fills any null String fields with
     * the English default from a fresh AppStrings() instance.
     */
    private fun fillNullsWithDefaults(loaded: AppStrings): AppStrings {
        val defaults = AppStrings()
        for (field in AppStrings::class.java.declaredFields) {
            if (field.type != String::class.java) continue
            field.isAccessible = true
            if (field.get(loaded) == null) {
                field.set(loaded, field.get(defaults))
            }
        }
        return loaded
    }
}
