package com.dukaan.ai.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls micro ads that appear during processing states.
 * Ensures ads only show when the user is already waiting.
 */
@Singleton
class MicroAdController @Inject constructor(
    private val frequencyManager: AdFrequencyManager
) {

    private val _currentMicroAdState = MutableStateFlow<MicroAdState>(MicroAdState.Hidden)
    val currentMicroAdState: StateFlow<MicroAdState> = _currentMicroAdState.asStateFlow()

    private var processStartTime = 0L
    private var currentProcessType: AdConfig.ProcessType? = null

    /**
     * Called when a process starts (e.g., OCR scanning).
     * Returns true if micro ad should be shown.
     */
    fun onProcessStart(processType: AdConfig.ProcessType): Boolean {
        if (!frequencyManager.canShowMicroAd()) {
            return false
        }

        processStartTime = System.currentTimeMillis()
        currentProcessType = processType

        val adUnitId = getAdUnitForProcess(processType)
        _currentMicroAdState.value = MicroAdState.Loading(processType, adUnitId)

        return true
    }

    /**
     * Called when ad is loaded and ready to display.
     */
    fun onAdLoaded() {
        val state = _currentMicroAdState.value
        if (state is MicroAdState.Loading) {
            _currentMicroAdState.value = MicroAdState.Showing(
                processType = state.processType,
                adUnitId = state.adUnitId,
                startTime = System.currentTimeMillis()
            )
            frequencyManager.onMicroAdShown()
        }
    }

    /**
     * Called when the process completes (e.g., OCR done).
     * Hides the micro ad.
     */
    fun onProcessComplete() {
        val state = _currentMicroAdState.value

        if (state is MicroAdState.Showing) {
            val displayDuration = System.currentTimeMillis() - state.startTime

            // Ensure minimum display time for user to see the ad
            if (displayDuration >= AdConfig.Frequency.MICRO_AD_MIN_DISPLAY_MS) {
                hideAd()
            } else {
                // Will be hidden after minimum time
                _currentMicroAdState.value = MicroAdState.WaitingMinTime(
                    processType = state.processType,
                    remainingTime = AdConfig.Frequency.MICRO_AD_MIN_DISPLAY_MS - displayDuration
                )
            }
        } else {
            hideAd()
        }

        currentProcessType = null
        processStartTime = 0L
    }

    /**
     * Force hide the micro ad.
     */
    fun hideAd() {
        _currentMicroAdState.value = MicroAdState.Hidden
    }

    /**
     * Check if a process has been running long enough to show micro ad.
     */
    fun shouldShowMicroAd(processType: AdConfig.ProcessType, estimatedDurationMs: Long): Boolean {
        return estimatedDurationMs >= processType.minDurationMs &&
                frequencyManager.canShowMicroAd()
    }

    /**
     * Get the appropriate ad unit ID for a process type.
     */
    private fun getAdUnitForProcess(processType: AdConfig.ProcessType): String {
        return when (processType) {
            AdConfig.ProcessType.OCR_SCAN -> AdConfig.MICRO_AD_SCANNING
            AdConfig.ProcessType.ITEM_PROCESSING -> AdConfig.MICRO_AD_PROCESSING
            AdConfig.ProcessType.PDF_GENERATE -> AdConfig.MICRO_AD_PDF_GENERATING
            AdConfig.ProcessType.AI_PROCESSING -> AdConfig.MICRO_AD_AI_PROCESSING
            AdConfig.ProcessType.IMAGE_UPLOAD -> AdConfig.MICRO_AD_PROCESSING
            AdConfig.ProcessType.DATA_SYNC -> AdConfig.MICRO_AD_PROCESSING
        }
    }

    /**
     * Get current process duration in milliseconds.
     */
    fun getCurrentProcessDuration(): Long {
        return if (processStartTime > 0) {
            System.currentTimeMillis() - processStartTime
        } else {
            0L
        }
    }
}

/**
 * Represents the state of a micro ad.
 */
sealed class MicroAdState {
    data object Hidden : MicroAdState()

    data class Loading(
        val processType: AdConfig.ProcessType,
        val adUnitId: String
    ) : MicroAdState()

    data class Showing(
        val processType: AdConfig.ProcessType,
        val adUnitId: String,
        val startTime: Long
    ) : MicroAdState()

    data class WaitingMinTime(
        val processType: AdConfig.ProcessType,
        val remainingTime: Long
    ) : MicroAdState()
}
