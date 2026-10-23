package com.jarvis.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceInput(
    private val ctx: Context,
    private val onResult: (String) -> Unit,
    var onError: (String) -> Unit = {}
) {
    private var rec: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("Speech recognition is not available on this device (Google app missing?). Please type instead.")
            return
        }
        rec = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onError("Voice input error (code $error). Try again or type instead.")
                }
                override fun onResults(results: Bundle?) {
                    val m = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (m != null) onResult(m) else onError("Did not catch that. Please try again.")
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        }
    }

    fun stop() { rec?.stopListening() }
    fun destroy() { rec?.destroy(); rec = null }
}

class VoiceOutput(ctx: Context, private val female: () -> Boolean) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                applyVoice()
            }
        }
    }

    private fun applyVoice() {
        val t = tts ?: return
        t.setPitch(if (female()) 1.3f else 1.0f)
        if (female()) {
            val v = t.voices?.firstOrNull {
                !it.isNetworkConnectionRequired &&
                    (it.name.contains("female", true) || it.name.contains("heera", true) || it.name.contains("zira", true))
            }
            v?.let { t.voice = it }
        }
    }

    fun speak(text: String) { applyVoice(); tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis") }
    fun stop() { tts?.stop() }
    fun shutdown() { tts?.shutdown() }
}
