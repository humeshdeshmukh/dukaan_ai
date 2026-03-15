package com.dukaan.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _finalResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val finalResult: SharedFlow<String> = _finalResult

    private val _error = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val error: SharedFlow<Int> = _error

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode

    // Flag to suppress errors during intentional stop
    @Volatile
    private var isStopping = false

    // The speech recognition locale (e.g. "hi-IN", "ta-IN", "en-IN")
    private var currentSpeechCode: String = "hi-IN"

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(continuous: Boolean = true, speechCode: String = "hi-IN") {
        isStopping = false
        _isContinuousMode.value = continuous
        currentSpeechCode = speechCode
        startRecognizer()
    }

    private fun startRecognizer() {
        mainHandler.post {
            // Don't start if we're in the process of stopping
            if (isStopping || (!_isContinuousMode.value && _isListening.value)) {
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e("SpeechManager", "Speech recognition not available on this device")
                _error.tryEmit(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            // Destroy previous instance — null listener first to prevent spurious onError(ERROR_CLIENT)
            try {
                speechRecognizer?.setRecognitionListener(null)
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error destroying previous recognizer", e)
            }
            speechRecognizer = null
            _speechText.value = ""
            _audioLevel.value = 0f

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechManager", "Ready for speech")
                        if (!isStopping) {
                            _isListening.value = true
                        }
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechManager", "Speech began")
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize RMS to 0-1 range (typically -2 to 10 dB)
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioLevel.value = normalized
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("SpeechManager", "Speech ended")
                        _audioLevel.value = 0f
                    }
                    override fun onError(error: Int) {
                        Log.e("SpeechManager", "Error: $error (isStopping=$isStopping)")
                        _isListening.value = false
                        _audioLevel.value = 0f

                        // If we're intentionally stopping, don't emit errors or restart
                        if (isStopping) {
                            return
                        }

                        // If we have partial text, emit it as final result
                        if (_speechText.value.isNotBlank()) {
                            _finalResult.tryEmit(_speechText.value)
                        }

                        // In continuous mode, auto-restart on transient errors
                        if (_isContinuousMode.value) {
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                SpeechRecognizer.ERROR_SERVER,
                                11, // ERROR_SERVER_DISCONNECTED (API 31+)
                                10  // ERROR_TOO_MANY_REQUESTS (API 31+)
                                -> {
                                    Log.d("SpeechManager", "Continuous mode: auto-restarting after transient error $error")
                                    mainHandler.postDelayed({
                                        if (_isContinuousMode.value && !isStopping) {
                                            startRecognizer()
                                        }
                                    }, 500)
                                    return
                                }
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                    Log.d("SpeechManager", "Continuous mode: recognizer busy, retrying")
                                    mainHandler.postDelayed({
                                        if (_isContinuousMode.value && !isStopping) {
                                            startRecognizer()
                                        }
                                    }, 1000)
                                    return
                                }
                            }
                        }
                        // For non-transient errors or non-continuous mode, emit error
                        _error.tryEmit(error)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        Log.d("SpeechManager", "Results: $matches")
                        if (!matches.isNullOrEmpty()) {
                            _speechText.value = matches[0]
                            _finalResult.tryEmit(matches[0])
                        } else if (_speechText.value.isNotBlank()) {
                            _finalResult.tryEmit(_speechText.value)
                        }
                        _isListening.value = false
                        _audioLevel.value = 0f

                        // In continuous mode, auto-restart after getting results
                        if (_isContinuousMode.value && !isStopping) {
                            Log.d("SpeechManager", "Continuous mode: auto-restarting after results")
                            mainHandler.postDelayed({
                                if (_isContinuousMode.value && !isStopping) {
                                    startRecognizer()
                                }
                            }, 500)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _speechText.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSpeechCode)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(
                        "en-IN", "en-US", "hi-IN", "mr-IN", "ta-IN", "te-IN", "bn-IN",
                        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN"
                    ))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                _isListening.value = true
                speechRecognizer?.startListening(intent)
                Log.d("SpeechManager", "startListening called (continuous=${_isContinuousMode.value})")
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to start speech recognizer", e)
                _isListening.value = false
                if (!isStopping) {
                    _error.tryEmit(SpeechRecognizer.ERROR_CLIENT)
                }
            }
        }
    }

    fun stopListening() {
        isStopping = true
        _isContinuousMode.value = false
        // Cancel ALL pending callbacks to prevent stale restarts
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.setRecognitionListener(null)
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error stopping", e)
            }
            speechRecognizer = null
            _isListening.value = false
            _audioLevel.value = 0f
            _speechText.value = ""
        }
    }

    fun destroy() {
        isStopping = true
        _isContinuousMode.value = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.setRecognitionListener(null)
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error destroying", e)
            }
            speechRecognizer = null
            _audioLevel.value = 0f
        }
    }
}
