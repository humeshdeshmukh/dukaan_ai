package com.dukaan.ai.ads.composables

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * A composable banner ad view that can be placed at the bottom of screens.
 *
 * @param adUnitId The AdMob ad unit ID for this banner
 * @param modifier Modifier for the container
 * @param onAdLoaded Callback when ad is loaded successfully
 * @param onAdFailedToLoad Callback when ad fails to load
 */
@Composable
fun BannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (LoadAdError) -> Unit = {}
) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    DisposableEffect(adUnitId) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                onAdLoaded()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onAdFailedToLoad(error)
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        onDispose {
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A large banner ad view (320x100 dp).
 */
@Composable
fun LargeBannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (LoadAdError) -> Unit = {}
) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.LARGE_BANNER)
            this.adUnitId = adUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    DisposableEffect(adUnitId) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                onAdLoaded()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onAdFailedToLoad(error)
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        onDispose {
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Adaptive banner that adjusts to screen width.
 */
@Composable
fun AdaptiveBannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (LoadAdError) -> Unit = {}
) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            // Get adaptive banner size based on screen width
            val displayMetrics = context.resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)

            setAdSize(adaptiveSize)
            this.adUnitId = adUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    DisposableEffect(adUnitId) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                onAdLoaded()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onAdFailedToLoad(error)
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        onDispose {
            adView.destroy()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
