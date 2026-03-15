package com.dukaan.ai.translation

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

    suspend fun loadCachedTranslation(languageCode: String) {
        if (languageCode == "en") {
            _currentStrings.value = AppStrings()
            return
        }
        val cached = translationDao.getTranslation(languageCode)
        if (cached != null) {
            try {
                _currentStrings.value = gson.fromJson(cached.translationsJson, AppStrings::class.java)
            } catch (_: Exception) {
                _currentStrings.value = AppStrings()
            }
        }
    }

    suspend fun translateAndApply(languageCode: String, languageName: String) {
        if (languageCode == "en") {
            _currentStrings.value = AppStrings()
            return
        }

        // Check cache first
        val cached = translationDao.getTranslation(languageCode)
        if (cached != null) {
            try {
                _currentStrings.value = gson.fromJson(cached.translationsJson, AppStrings::class.java)
                return
            } catch (_: Exception) {
                // Cache corrupted, re-translate
            }
        }

        // Translate via Gemini
        _isTranslating.value = true
        try {
            val englishJson = gson.toJson(AppStrings())
            val translatedJson = translationService.translateStrings(
                englishStringsJson = englishJson,
                targetLanguageName = languageName,
                targetLanguageCode = languageCode
            )
            val translated = gson.fromJson(translatedJson, AppStrings::class.java)
            _currentStrings.value = translated

            // Cache it
            translationDao.upsertTranslation(
                TranslationCacheEntity(
                    languageCode = languageCode,
                    translationsJson = translatedJson,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            // Translation failed, keep current strings
        } finally {
            _isTranslating.value = false
        }
    }
}
