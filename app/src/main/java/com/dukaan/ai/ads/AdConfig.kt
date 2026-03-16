package com.dukaan.ai.ads

/**
 * Ad configuration containing all Ad Unit IDs and settings.
 * Replace test IDs with production IDs before release.
 */
object AdConfig {

    // ============ TEST AD UNIT IDs (Replace before production) ============
    // These are Google's official test ad unit IDs

    // Banner Ads
    const val BANNER_BILL_LIST = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_DASHBOARD = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_KHATA_LIST = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_SETTINGS = "ca-app-pub-3940256099942544/6300978111"
    const val BANNER_TRANSACTION_HISTORY = "ca-app-pub-3940256099942544/6300978111"

    // Micro Ads (During Processing)
    const val MICRO_AD_SCANNING = "ca-app-pub-3940256099942544/6300978111"
    const val MICRO_AD_PROCESSING = "ca-app-pub-3940256099942544/6300978111"
    const val MICRO_AD_PDF_GENERATING = "ca-app-pub-3940256099942544/6300978111"
    const val MICRO_AD_AI_PROCESSING = "ca-app-pub-3940256099942544/6300978111"

    // Native Ads
    const val NATIVE_DASHBOARD = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_PROCESSING = "ca-app-pub-3940256099942544/2247696110"

    // Interstitial Ads - HIGH VALUE PLACEMENTS
    const val INTERSTITIAL_BILL_SAVE = "ca-app-pub-3940256099942544/1033173712"
    const val INTERSTITIAL_PDF_DOWNLOAD = "ca-app-pub-3940256099942544/1033173712"
    const val INTERSTITIAL_APP_RESUME = "ca-app-pub-3940256099942544/1033173712"
    const val INTERSTITIAL_ORDER_SAVE = "ca-app-pub-3940256099942544/1033173712"      // After saving order
    const val INTERSTITIAL_SCAN_BILL_SAVE = "ca-app-pub-3940256099942544/1033173712"  // After scan bill saved
    const val INTERSTITIAL_ENTER_SCAN = "ca-app-pub-3940256099942544/1033173712"      // Before entering scan flow
    const val INTERSTITIAL_CUSTOMER_ADD = "ca-app-pub-3940256099942544/1033173712"    // After adding customer

    // ============ FREQUENCY SETTINGS ============

    object Frequency {
        // Interstitial limits
        const val MAX_INTERSTITIALS_PER_HOUR = 6  // Increased for more revenue
        const val MIN_INTERSTITIAL_GAP_MS = 120_000L // 2 minutes (reduced from 3)

        // Micro ad limits
        const val MAX_MICRO_ADS_PER_SESSION = 15
        const val MICRO_AD_MIN_DISPLAY_MS = 1500L // 1.5 seconds
        const val MICRO_AD_MAX_DISPLAY_MS = 8000L // 8 seconds

        // Banner refresh
        const val BANNER_REFRESH_INTERVAL_MS = 60_000L // 60 seconds

        // Interstitial triggers - OPTIMIZED FOR MAX REVENUE
        const val BILLS_BEFORE_INTERSTITIAL = 3       // Every 3rd bill (was 4)
        const val PDF_DOWNLOADS_BEFORE_INTERSTITIAL = 2  // Every 2nd PDF (was 3)
        const val CUSTOMERS_BEFORE_INTERSTITIAL = 3   // Every 3rd customer (was 5)
        const val ORDERS_BEFORE_INTERSTITIAL = 1      // EVERY order (used rarely)
        const val SCAN_BILLS_BEFORE_INTERSTITIAL = 1  // EVERY scan bill (used rarely)
        const val APP_BACKGROUND_THRESHOLD_MS = 180_000L // 3 minutes (was 5)
    }

    // ============ PROCESS TYPES ============

    enum class ProcessType(val minDurationMs: Long) {
        OCR_SCAN(1500L),
        ITEM_PROCESSING(2000L),
        PDF_GENERATE(1000L),
        AI_PROCESSING(2000L),
        IMAGE_UPLOAD(2000L),
        DATA_SYNC(1500L)
    }
}
