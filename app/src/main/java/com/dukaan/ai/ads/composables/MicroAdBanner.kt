package com.dukaan.ai.ads.composables

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay

/**
 * A micro ad banner that appears during processing states.
 * Shows smoothly during the loading/processing time.
 *
 * @param adUnitId The AdMob ad unit ID
 * @param isProcessing Whether the process is currently running
 * @param modifier Modifier for the container
 * @param minDisplayTimeMs Minimum time to show the ad (for visibility)
 * @param onAdShown Callback when ad is displayed
 */
@Composable
fun MicroAdBanner(
    adUnitId: String,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    minDisplayTimeMs: Long = 1500L,
    onAdShown: () -> Unit = {}
) {
    var isAdLoaded by remember { mutableStateOf(false) }
    var shouldShow by remember { mutableStateOf(false) }
    var showStartTime by remember { mutableStateOf(0L) }

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

    // Load ad when processing starts
    LaunchedEffect(isProcessing) {
        if (isProcessing && !isAdLoaded) {
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    // Handle minimum display time
    LaunchedEffect(isProcessing, isAdLoaded) {
        if (isProcessing && isAdLoaded) {
            shouldShow = true
            showStartTime = System.currentTimeMillis()
            onAdShown()
        } else if (!isProcessing && shouldShow) {
            // Wait for minimum display time
            val displayedTime = System.currentTimeMillis() - showStartTime
            if (displayedTime < minDisplayTimeMs) {
                delay(minDisplayTimeMs - displayedTime)
            }
            shouldShow = false
        }
    }

    DisposableEffect(adUnitId) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                isAdLoaded = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isAdLoaded = false
            }
        }

        onDispose {
            adView.destroy()
        }
    }

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
                .height(50.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Processing overlay with integrated micro ad.
 * Shows a loading indicator along with a micro ad below it.
 *
 * @param isProcessing Whether the process is running
 * @param adUnitId The AdMob ad unit ID
 * @param processingText Text to show during processing
 * @param modifier Modifier for the container
 */
@Composable
fun ProcessingWithMicroAd(
    isProcessing: Boolean,
    adUnitId: String,
    processingText: String = "Processing...",
    modifier: Modifier = Modifier,
    onAdShown: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main content (e.g., progress indicator)
        content()

        // Processing text
        if (isProcessing) {
            Text(
                text = processingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Micro ad during processing
        MicroAdBanner(
            adUnitId = adUnitId,
            isProcessing = isProcessing,
            onAdShown = onAdShown
        )
    }
}

/**
 * A compact micro ad for tight spaces.
 * Uses SMART_BANNER which adapts to screen width.
 */
@Composable
fun CompactMicroAd(
    adUnitId: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var isAdLoaded by remember { mutableStateOf(false) }

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

    LaunchedEffect(isVisible) {
        if (isVisible && !isAdLoaded) {
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adUnitId) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                isAdLoaded = true
            }
        }

        onDispose {
            adView.destroy()
        }
    }

    AnimatedVisibility(
        visible = isVisible && isAdLoaded,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
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
}
