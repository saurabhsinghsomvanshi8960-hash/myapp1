package com.alphaorder.jarvisai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

/**
 * Wraps Android's TextToSpeech engine.
 * Call [initialize] once (e.g. from ViewModel's init) before using [speak].
 */
class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    fun initialize(languageTag: String = "en-IN", onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag(languageTag)
                isReady = true
                onReady()
            }
        }
    }

    fun setVoiceGender(preferFemale: Boolean) {
        val engine = tts ?: return
        val voices: Set<Voice> = engine.voices ?: return
        val match = voices.firstOrNull { voice ->
            val name = voice.name.lowercase()
            if (preferFemale) name.contains("female") else name.contains("male")
        }
        if (match != null) engine.voice = match
    }

    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val engine = tts ?: return
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onStart()
            override fun onDone(utteranceId: String?) = onDone()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onError()
        })
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
