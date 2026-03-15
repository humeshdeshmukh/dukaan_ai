package com.dukaan.core.db

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val speechCode: String
)

object SupportedLanguages {
    val languages = listOf(
        AppLanguage("en", "English", "English", "en-IN"),
        AppLanguage("hi-en", "Hinglish", "Hinglish", "hi-IN"),
        AppLanguage("hi", "हिन्दी", "Hindi", "hi-IN"),
        AppLanguage("bn", "বাংলা", "Bengali", "bn-IN"),
        AppLanguage("te", "తెలుగు", "Telugu", "te-IN"),
        AppLanguage("mr", "मराठी", "Marathi", "mr-IN"),
        AppLanguage("ta", "தமிழ்", "Tamil", "ta-IN"),
        AppLanguage("ur", "اردو", "Urdu", "ur-IN"),
        AppLanguage("gu", "ગુજરાતી", "Gujarati", "gu-IN"),
        AppLanguage("kn", "ಕನ್ನಡ", "Kannada", "kn-IN"),
        AppLanguage("ml", "മലയാളം", "Malayalam", "ml-IN"),
        AppLanguage("or", "ଓଡ଼ିଆ", "Odia", "or-IN"),
        AppLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi", "pa-IN"),
        AppLanguage("as", "অসমীয়া", "Assamese", "as-IN"),
        AppLanguage("mai", "मैथिली", "Maithili", "mai-IN"),
        AppLanguage("sat", "ᱥᱟᱱᱛᱟᱲᱤ", "Santali", "sat-IN"),
        AppLanguage("ks", "कॉशुर", "Kashmiri", "ks-IN"),
        AppLanguage("ne", "नेपाली", "Nepali", "ne-IN"),
        AppLanguage("sd", "سنڌي", "Sindhi", "sd-IN"),
        AppLanguage("kok", "कोंकणी", "Konkani", "kok-IN"),
        AppLanguage("doi", "डोगरी", "Dogri", "doi-IN"),
        AppLanguage("mni", "মণিপুরী", "Manipuri", "mni-IN"),
        AppLanguage("brx", "बड़ो", "Bodo", "brx-IN"),
        AppLanguage("sa", "संस्कृतम्", "Sanskrit", "sa-IN")
    )

    fun getByCode(code: String): AppLanguage =
        languages.find { it.code == code } ?: languages.first()

    fun getLanguageName(code: String): String {
        val lang = getByCode(code)
        return if (code == "en") "English" else "${lang.nativeName} (${lang.englishName})"
    }

    fun getSpeechCode(code: String): String = getByCode(code).speechCode
}
