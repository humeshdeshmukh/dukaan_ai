package com.dukaan.ai.ads.composables

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dukaan.ai.ads.AdManager

/**
 * Effect that shows an interstitial ad after certain events.
 * Use this as a side-effect in your composables.
 *
 * @param adManager The AdManager instance
 * @param triggerKey Key that changes when an interstitial should be considered
 * @param eventType Type of event that triggered this
 */
@Composable
fun InterstitialAdEffect(
    adManager: AdManager,
    triggerKey: Any?,
    eventType: InterstitialTrigger
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    LaunchedEffect(triggerKey) {
        if (triggerKey != null) {
            when (eventType) {
                InterstitialTrigger.BILL_SAVE -> {
                    adManager.showInterstitialAfterBillSave(activity)
                }
                InterstitialTrigger.PDF_DOWNLOAD -> {
                    adManager.showInterstitialAfterPdfDownload(activity)
                }
                InterstitialTrigger.ORDER_SAVE -> {
                    adManager.showInterstitialAfterOrderSave(activity)
                }
                InterstitialTrigger.SCAN_BILL_SAVE -> {
                    adManager.showInterstitialAfterScanBillSave(activity)
                }
                InterstitialTrigger.ENTER_SCAN -> {
                    adManager.showInterstitialOnEnterScan(activity)
                }
                InterstitialTrigger.CUSTOMER_ADD -> {
                    adManager.showInterstitialAfterCustomerAdd(activity)
                }
                InterstitialTrigger.APP_RESUME -> {
                    // This is typically handled in lifecycle observer
                }
            }
        }
    }
}

/**
 * Effect that shows interstitial ad when app resumes after being in background.
 *
 * @param adManager The AdManager instance
 */
@Composable
fun AppResumeInterstitialEffect(adManager: AdManager) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    var wasInBackground by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasInBackground = true
                    adManager.onAppBackground()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasInBackground) {
                        adManager.showInterstitialOnResume(activity)
                        wasInBackground = false
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Helper to show interstitial when entering a screen.
 * Use this when navigating to infrequently-used features.
 */
@Composable
fun ShowInterstitialOnEnter(
    adManager: AdManager,
    trigger: InterstitialTrigger
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    LaunchedEffect(Unit) {
        when (trigger) {
            InterstitialTrigger.ENTER_SCAN -> adManager.showInterstitialOnEnterScan(activity)
            InterstitialTrigger.ORDER_SAVE -> adManager.showInterstitialAfterOrderSave(activity)
            InterstitialTrigger.SCAN_BILL_SAVE -> adManager.showInterstitialAfterScanBillSave(activity)
            else -> {}
        }
    }
}

/**
 * Types of events that can trigger an interstitial ad.
 */
enum class InterstitialTrigger {
    BILL_SAVE,
    PDF_DOWNLOAD,
    ORDER_SAVE,        // After saving wholesale order
    SCAN_BILL_SAVE,    // After saving scanned purchase bill
    ENTER_SCAN,        // When entering OCR/scan flow
    CUSTOMER_ADD,      // After adding new customer
    APP_RESUME
}
