package com.dukaan.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _finalResult = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult

    private val _error = MutableStateFlow<Int?>(null)
    val error: StateFlow<Int?> = _error

    fun startListening() {
        mainHandler.post {
            // Destroy previous instance to ensure fresh session each time
            speechRecognizer?.destroy()
            speechRecognizer = null

            _speechText.value = ""
            _finalResult.value = ""
            _error.value = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    _error.value = error
                    // If we have partial text, treat it as final on error
                    if (_speechText.value.isNotBlank()) {
                        _finalResult.value = _speechText.value
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _speechText.value = matches[0]
                        _finalResult.value = matches[0]
                    } else if (_speechText.value.isNotBlank()) {
                        _finalResult.value = _speechText.value
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
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            speechRecognizer?.stopListening()
        }
    }

    fun clearFinalResult() {
        _finalResult.value = ""
    }

    fun destroy() {
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
