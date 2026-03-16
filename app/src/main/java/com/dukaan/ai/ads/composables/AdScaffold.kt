package com.dukaan.ai.ads.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A scaffold that wraps screen content with a banner ad at the bottom.
 * Use this to easily add ads to any screen.
 *
 * @param adUnitId The AdMob ad unit ID for the banner
 * @param showAd Whether to show the ad (can be controlled by premium status, etc.)
 * @param modifier Modifier for the container
 * @param content The screen content
 */
@Composable
fun AdScaffold(
    adUnitId: String,
    showAd: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Main content takes remaining space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            content()
        }

        // Banner ad at bottom
        if (showAd) {
            BannerAdView(
                adUnitId = adUnitId,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A scaffold with adaptive banner ad at the bottom.
 * Adaptive banners adjust their size based on screen width.
 */
@Composable
fun AdaptiveAdScaffold(
    adUnitId: String,
    showAd: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Main content takes remaining space
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            content()
        }

        // Adaptive banner ad at bottom
        if (showAd) {
            AdaptiveBannerAdView(
                adUnitId = adUnitId,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
