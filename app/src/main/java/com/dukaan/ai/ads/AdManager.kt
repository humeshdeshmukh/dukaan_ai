package com.dukaan.ai.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for all ad operations.
 * Handles initialization, loading, caching, and displaying ads.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frequencyManager: AdFrequencyManager,
    private val microAdController: MicroAdController
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _interstitialReady = MutableStateFlow(false)
    val interstitialReady: StateFlow<Boolean> = _interstitialReady.asStateFlow()

    /**
     * Initialize the Mobile Ads SDK.
     * Call this in Application.onCreate()
     */
    fun initialize() {
        scope.launch {
            MobileAds.initialize(context) {
                _isInitialized.value = true
                preloadInterstitial()
            }
        }
    }

    /**
     * Preload an interstitial ad for later use.
     */
    fun preloadInterstitial(adUnitId: String = AdConfig.INTERSTITIAL_BILL_SAVE) {
        if (isInterstitialLoading || interstitialAd != null) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    _interstitialReady.value = true

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            _interstitialReady.value = false
                            preloadInterstitial() // Preload next one
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            _interstitialReady.value = false
                            preloadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isInterstitialLoading = false
                    _interstitialReady.value = false
                }
            }
        )
    }

    /**
     * Show interstitial ad after bill save.
     * Returns true if ad was shown.
     */
    fun showInterstitialAfterBillSave(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialAfterBillSave()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad after PDF download.
     * Returns true if ad was shown.
     */
    fun showInterstitialAfterPdfDownload(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialAfterPdfDownload()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad on app resume.
     * Returns true if ad was shown.
     */
    fun showInterstitialOnResume(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialOnResume()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad after saving an order.
     * Orders are infrequent - show ad every time (high value placement).
     * Returns true if ad was shown.
     */
    fun showInterstitialAfterOrderSave(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialAfterOrderSave()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad after saving a scanned bill.
     * Scan bills are infrequent - show ad every time (high value placement).
     * Returns true if ad was shown.
     */
    fun showInterstitialAfterScanBillSave(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialAfterScanBillSave()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad when entering the scan flow.
     * Shows before user starts scanning (natural break point).
     * Returns true if ad was shown.
     */
    fun showInterstitialOnEnterScan(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialOnEnterScan()) return false
        return showInterstitial(activity)
    }

    /**
     * Show interstitial ad after adding a customer.
     * Returns true if ad was shown.
     */
    fun showInterstitialAfterCustomerAdd(activity: Activity): Boolean {
        if (!frequencyManager.shouldShowInterstitialAfterCustomerAdd()) return false
        return showInterstitial(activity)
    }

    /**
     * Show an interstitial ad.
     * Returns true if ad was shown.
     */
    private fun showInterstitial(activity: Activity): Boolean {
        val ad = interstitialAd
        return if (ad != null && frequencyManager.canShowInterstitial()) {
            ad.show(activity)
            frequencyManager.onInterstitialShown()
            true
        } else {
            preloadInterstitial()
            false
        }
    }

    /**
     * Create an ad request for banner/micro ads.
     */
    fun createAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }

    /**
     * Notify that app went to background.
     */
    fun onAppBackground() {
        frequencyManager.onAppBackground()
    }

    /**
     * Reset session counters.
     */
    fun resetSession() {
        frequencyManager.resetSession()
    }

    /**
     * Get the MicroAdController instance.
     */
    fun getMicroAdController(): MicroAdController = microAdController

    /**
     * Get the FrequencyManager instance.
     */
    fun getFrequencyManager(): AdFrequencyManager = frequencyManager
}
