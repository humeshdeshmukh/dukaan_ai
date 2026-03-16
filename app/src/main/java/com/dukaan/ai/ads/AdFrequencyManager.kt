package com.dukaan.ai.ads

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ad frequency to prevent user frustration.
 * Enforces limits on how often ads can be shown.
 */
@Singleton
class AdFrequencyManager @Inject constructor() {

    private var lastInterstitialTime = 0L
    private var interstitialCountThisHour = 0
    private var hourStartTime = System.currentTimeMillis()

    private var microAdCountThisSession = 0
    private var sessionStartTime = System.currentTimeMillis()

    private var billsSavedCount = 0
    private var pdfDownloadCount = 0
    private var customersAddedCount = 0
    private var ordersSavedCount = 0
    private var scanBillsSavedCount = 0

    private var lastBackgroundTime = 0L

    /**
     * Check if an interstitial ad can be shown.
     */
    fun canShowInterstitial(): Boolean {
        resetHourlyCounterIfNeeded()
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastInterstitialTime

        return timeSinceLast >= AdConfig.Frequency.MIN_INTERSTITIAL_GAP_MS &&
                interstitialCountThisHour < AdConfig.Frequency.MAX_INTERSTITIALS_PER_HOUR
    }

    /**
     * Record that an interstitial was shown.
     */
    fun onInterstitialShown() {
        resetHourlyCounterIfNeeded()
        lastInterstitialTime = System.currentTimeMillis()
        interstitialCountThisHour++
    }

    /**
     * Check if a micro ad can be shown.
     */
    fun canShowMicroAd(): Boolean {
        return microAdCountThisSession < AdConfig.Frequency.MAX_MICRO_ADS_PER_SESSION
    }

    /**
     * Record that a micro ad was shown.
     */
    fun onMicroAdShown() {
        microAdCountThisSession++
    }

    /**
     * Check if interstitial should show after bill save.
     */
    fun shouldShowInterstitialAfterBillSave(): Boolean {
        billsSavedCount++
        return billsSavedCount % AdConfig.Frequency.BILLS_BEFORE_INTERSTITIAL == 0 &&
                canShowInterstitial()
    }

    /**
     * Check if interstitial should show after PDF download.
     */
    fun shouldShowInterstitialAfterPdfDownload(): Boolean {
        pdfDownloadCount++
        return pdfDownloadCount % AdConfig.Frequency.PDF_DOWNLOADS_BEFORE_INTERSTITIAL == 0 &&
                canShowInterstitial()
    }

    /**
     * Check if interstitial should show after adding customer.
     */
    fun shouldShowInterstitialAfterCustomerAdd(): Boolean {
        customersAddedCount++
        return customersAddedCount % AdConfig.Frequency.CUSTOMERS_BEFORE_INTERSTITIAL == 0 &&
                canShowInterstitial()
    }

    /**
     * Check if interstitial should show after saving order.
     * Orders are infrequent, so show ad every time (if allowed by frequency).
     */
    fun shouldShowInterstitialAfterOrderSave(): Boolean {
        ordersSavedCount++
        return ordersSavedCount % AdConfig.Frequency.ORDERS_BEFORE_INTERSTITIAL == 0 &&
                canShowInterstitial()
    }

    /**
     * Check if interstitial should show after saving scanned bill.
     * Scan bills are infrequent, so show ad every time (if allowed by frequency).
     */
    fun shouldShowInterstitialAfterScanBillSave(): Boolean {
        scanBillsSavedCount++
        return scanBillsSavedCount % AdConfig.Frequency.SCAN_BILLS_BEFORE_INTERSTITIAL == 0 &&
                canShowInterstitial()
    }

    /**
     * Check if interstitial should show when entering scan flow.
     * Shows only if enough time has passed since last interstitial.
     */
    fun shouldShowInterstitialOnEnterScan(): Boolean {
        return canShowInterstitial()
    }

    /**
     * Record when app goes to background.
     */
    fun onAppBackground() {
        lastBackgroundTime = System.currentTimeMillis()
    }

    /**
     * Check if interstitial should show on app resume.
     */
    fun shouldShowInterstitialOnResume(): Boolean {
        val now = System.currentTimeMillis()
        val backgroundDuration = now - lastBackgroundTime

        return lastBackgroundTime > 0 &&
                backgroundDuration >= AdConfig.Frequency.APP_BACKGROUND_THRESHOLD_MS &&
                canShowInterstitial()
    }

    /**
     * Reset session counters (call when app starts fresh).
     */
    fun resetSession() {
        microAdCountThisSession = 0
        sessionStartTime = System.currentTimeMillis()
    }

    /**
     * Reset hourly counter if an hour has passed.
     */
    private fun resetHourlyCounterIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - hourStartTime >= 3600_000L) { // 1 hour
            interstitialCountThisHour = 0
            hourStartTime = now
        }
    }

    /**
     * Get remaining micro ads this session.
     */
    fun getRemainingMicroAds(): Int {
        return AdConfig.Frequency.MAX_MICRO_ADS_PER_SESSION - microAdCountThisSession
    }

    /**
     * Get time until next interstitial can be shown.
     */
    fun getTimeUntilNextInterstitial(): Long {
        if (!canShowInterstitial()) {
            val timeSinceLast = System.currentTimeMillis() - lastInterstitialTime
            return (AdConfig.Frequency.MIN_INTERSTITIAL_GAP_MS - timeSinceLast).coerceAtLeast(0)
        }
        return 0
    }
}
