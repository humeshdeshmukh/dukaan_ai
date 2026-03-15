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

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e("SpeechManager", "Speech recognition not available on this device")
                _error.tryEmit(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            // Destroy previous instance
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error destroying previous recognizer", e)
            }
            speechRecognizer = null
            _speechText.value = ""

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("SpeechManager", "Ready for speech")
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d("SpeechManager", "Speech began")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d("SpeechManager", "Speech ended")
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        Log.e("SpeechManager", "Error: $error")
                        _isListening.value = false
                        _error.tryEmit(error)
                        // If we have partial text, treat it as final on error
                        if (_speechText.value.isNotBlank()) {
                            _finalResult.tryEmit(_speechText.value)
                        }
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
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(
                        "en-IN", "en-US", "hi-IN", "mr-IN", "ta-IN", "te-IN", "bn-IN",
                        "gu-IN", "kn-IN", "ml-IN", "pa-IN", "ur-IN"
                    ))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                // Set listening true immediately so UI responds
                _isListening.value = true
                speechRecognizer?.startListening(intent)
                Log.d("SpeechManager", "startListening called")
            } catch (e: Exception) {
                Log.e("SpeechManager", "Failed to start speech recognizer", e)
                _isListening.value = false
                _error.tryEmit(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error stopping", e)
            }
            _isListening.value = false
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.e("SpeechManager", "Error destroying", e)
            }
            speechRecognizer = null
        }
    }
}
