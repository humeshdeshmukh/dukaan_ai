package com.dukaan.ai.ads

/**
 * Ad configuration containing all Ad Unit IDs and settings.
 *
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  HOW TO ADD REAL ADMOB KEYS:                                 ║
 * ║                                                              ║
 * ║  1. Go to admob.google.com                                   ║
 * ║  2. Create app → get App ID → put in AndroidManifest.xml     ║
 * ║  3. Create ad units → copy IDs below                         ║
 * ║  4. Set IS_TEST_MODE = false before releasing                 ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
object AdConfig {

    // ─── SET TO FALSE BEFORE RELEASE ─────────────────────────────
    const val IS_TEST_MODE = false
    // ─────────────────────────────────────────────────────────────

    // ─── YOUR REAL AD UNIT IDs (replace these) ───────────────────
    // Get from: admob.google.com → Your App → Ad Units
    private const val REAL_BANNER          = "ca-app-pub-1860002386592592/2913149193"
    private const val REAL_INTERSTITIAL    = "ca-app-pub-1860002386592592/3023862231"
    private const val REAL_MICRO_BANNER    = "ca-app-pub-1860002386592592/9344528899"
    private const val REAL_NATIVE          = "ca-app-pub-1860002386592592/9655520084"
    // ─────────────────────────────────────────────────────────────

    // ─── GOOGLE'S OFFICIAL TEST IDs (do not change these) ────────
    private const val TEST_BANNER          = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL    = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_NATIVE          = "ca-app-pub-3940256099942544/2247696110"
    // ─────────────────────────────────────────────────────────────

    // ─── ACTIVE IDs (auto-switches based on IS_TEST_MODE) ────────
    private val BANNER       get() = if (IS_TEST_MODE) TEST_BANNER       else REAL_BANNER
    private val INTERSTITIAL get() = if (IS_TEST_MODE) TEST_INTERSTITIAL else REAL_INTERSTITIAL
    private val MICRO        get() = if (IS_TEST_MODE) TEST_BANNER       else REAL_MICRO_BANNER
    private val NATIVE       get() = if (IS_TEST_MODE) TEST_NATIVE       else REAL_NATIVE
    // ─────────────────────────────────────────────────────────────

    // Banner placements
    val BANNER_BILL_LIST          get() = BANNER
    val BANNER_DASHBOARD          get() = BANNER
    val BANNER_KHATA_LIST         get() = BANNER
    val BANNER_SETTINGS           get() = BANNER
    val BANNER_TRANSACTION_HISTORY get() = BANNER

    // Micro Ads (during processing/scanning)
    val MICRO_AD_SCANNING         get() = MICRO
    val MICRO_AD_PROCESSING       get() = MICRO
    val MICRO_AD_PDF_GENERATING   get() = MICRO
    val MICRO_AD_AI_PROCESSING    get() = MICRO

    // Native Ads
    val NATIVE_DASHBOARD          get() = NATIVE
    val NATIVE_PROCESSING         get() = NATIVE

    // Interstitial Ads
    val INTERSTITIAL_BILL_SAVE       get() = INTERSTITIAL
    val INTERSTITIAL_PDF_DOWNLOAD    get() = INTERSTITIAL
    val INTERSTITIAL_APP_RESUME      get() = INTERSTITIAL
    val INTERSTITIAL_ORDER_SAVE      get() = INTERSTITIAL
    val INTERSTITIAL_SCAN_BILL_SAVE  get() = INTERSTITIAL
    val INTERSTITIAL_ENTER_SCAN      get() = INTERSTITIAL
    val INTERSTITIAL_CUSTOMER_ADD    get() = INTERSTITIAL

    // ─── FREQUENCY SETTINGS ──────────────────────────────────────

    object Frequency {
        const val MAX_INTERSTITIALS_PER_HOUR     = 6
        const val MIN_INTERSTITIAL_GAP_MS        = 120_000L  // 2 minutes
        const val MAX_MICRO_ADS_PER_SESSION      = 15
        const val MICRO_AD_MIN_DISPLAY_MS        = 1500L
        const val MICRO_AD_MAX_DISPLAY_MS        = 8000L
        const val BANNER_REFRESH_INTERVAL_MS     = 60_000L
        const val BILLS_BEFORE_INTERSTITIAL      = 3
        const val PDF_DOWNLOADS_BEFORE_INTERSTITIAL = 2
        const val CUSTOMERS_BEFORE_INTERSTITIAL  = 3
        const val ORDERS_BEFORE_INTERSTITIAL     = 1
        const val SCAN_BILLS_BEFORE_INTERSTITIAL = 1
        const val APP_BACKGROUND_THRESHOLD_MS    = 180_000L  // 3 minutes
    }

    // ─── PROCESS TYPES ───────────────────────────────────────────

    enum class ProcessType(val minDurationMs: Long) {
        OCR_SCAN(1500L),
        ITEM_PROCESSING(2000L),
        PDF_GENERATE(1000L),
        AI_PROCESSING(2000L),
        IMAGE_UPLOAD(2000L),
        DATA_SYNC(1500L)
    }
}
