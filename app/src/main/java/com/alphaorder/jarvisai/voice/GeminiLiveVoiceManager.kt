package com.alphaorder.jarvisai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Realistic, real-time voice conversations via Gemini's Live API (BidiGenerateContent).
 *
 * Unlike the old pipeline (SpeechRecognizer -> text Gemini call -> Android TextToSpeech),
 * this streams raw mic audio directly to Gemini over a WebSocket and gets back natively
 * generated speech audio in the same stream — no separate STT/TTS engines involved, which
 * is what makes it sound human instead of robotic. Same Gemini API key as the rest of the app.
 */
class GeminiLiveVoiceManager {

    enum class LiveVoiceState { CONNECTING, LISTENING, SPEAKING, DISCONNECTED }

    companion object {
        private const val TAG = "GeminiLiveVoice"
        private const val MODEL = "gemini-3.1-flash-live-preview"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val MIC_CHUNK_SIZE = 2048
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming connection, no read timeout
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    @Volatile private var sessionActive = false

    // Callback handlers, set fresh on each connect()
    private var onStateChange: (LiveVoiceState) -> Unit = {}
    private var onInputTranscript: (String) -> Unit = {}
    private var onOutputTranscript: (String) -> Unit = {}
    private var onTurnComplete: () -> Unit = {}
    private var onInterrupted: () -> Unit = {}
    private var onInputLevel: (Float) -> Unit = {}
    private var onOutputLevel: (Float) -> Unit = {}
    private var onError: (String) -> Unit = {}

    fun isActive(): Boolean = sessionActive

    // Whether this session should also capture mic audio, or is text-only
    // (used for the typed-message flow so it doesn't silently turn the mic on).
    @Volatile private var micStreamingEnabled = true

    fun connect(
        apiKey: String,
        voiceName: String,
        systemInstruction: String,
        startMic: Boolean = true,
        onStateChange: (LiveVoiceState) -> Unit,
        onInputTranscript: (String) -> Unit,
        onOutputTranscript: (String) -> Unit,
        onTurnComplete: () -> Unit,
        onInterrupted: () -> Unit,
        onInputLevel: (Float) -> Unit = {},
        onOutputLevel: (Float) -> Unit = {},
        onError: (String) -> Unit
    ) {
        if (sessionActive) return
        micStreamingEnabled = startMic

        if (apiKey.isBlank()) {
            onError("API key नहीं मिली। Settings में जाकर डालें।")
            return
        }

        this.onStateChange = onStateChange
        this.onInputTranscript = onInputTranscript
        this.onOutputTranscript = onOutputTranscript
        this.onTurnComplete = onTurnComplete
        this.onInterrupted = onInterrupted
        this.onInputLevel = onInputLevel
        this.onOutputLevel = onOutputLevel
        this.onError = onError

        sessionActive = true
        onStateChange(LiveVoiceState.CONNECTING)

        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        val request = Request.Builder().url(url).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val setupMessage = JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("model", "models/$MODEL")
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put(
                                    "prebuiltVoiceConfig",
                                    JSONObject().put("voiceName", voiceName)
                                )
                            })
                        })
                        if (systemInstruction.isNotBlank()) {
                            put("systemInstruction", JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("text", systemInstruction))
                                )
                            })
                        }
                        // Ask Gemini to transcribe both sides so we can still show chat history
                        put("inputAudioTranscription", JSONObject())
                        put("outputAudioTranscription", JSONObject())
                    })
                }
                webSocket.send(setupMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!sessionActive) return // already torn down intentionally, ignore late callback
                Log.e(TAG, "Live voice WebSocket failure", t)
                val message = when {
                    response?.code == 401 || response?.code == 403 ->
                        "API key invalid लग रही है। Settings में जाँच लें।"
                    response?.code == 429 ->
                        "थोड़ी देर बाद फिर कोशिश करें (rate limit)।"
                    t is java.net.UnknownHostException || t is java.net.ConnectException ->
                        "Internet connection नहीं है।"
                    else ->
                        "Voice connection में दिक्कत आई: ${t.localizedMessage ?: "अनजान गड़बड़ी"}"
                }
                this@GeminiLiveVoiceManager.onError(message)
                teardown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!sessionActive) return
                teardown()
            }
        })

        if (micStreamingEnabled) {
            startMicStreaming()
        }
    }

    /**
     * Sends a typed text message into the ongoing (or freshly opened) Live session and asks
     * Gemini to reply. Because it goes through the same BidiGenerateContent session as voice,
     * the reply comes back as native Jarvis audio + transcript — not the old separate
     * text-model + Android TextToSpeech path — so typed and spoken conversations sound like
     * the same character.
     */
    fun sendText(text: String) {
        val socket = webSocket ?: run {
            onError("Live session तैयार नहीं है, दोबारा कोशिश करें।")
            return
        }
        val message = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }))
                put("turnComplete", true)
            })
        }
        try {
            socket.send(message.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text turn", e)
            onError("मैसेज भेजने में दिक्कत आई।")
        }
    }

    private fun handleServerMessage(rawText: String) {
        try {
            val json = JSONObject(rawText)

            if (json.has("error")) {
                val errMsg = json.optJSONObject("error")?.optString("message")
                    ?: "अनजान गड़बड़ी"
                onError("Server error: $errMsg")
                return
            }

            if (json.has("setupComplete")) {
                onStateChange(LiveVoiceState.LISTENING)
                return
            }

            val serverContent = json.optJSONObject("serverContent") ?: return

            // Realistic model audio, streamed chunk by chunk (24kHz PCM)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val inlineData = parts.optJSONObject(i)?.optJSONObject("inlineData")
                    val data = inlineData?.optString("data")
                    if (!data.isNullOrEmpty()) {
                        onStateChange(LiveVoiceState.SPEAKING)
                        playAudioChunk(data)
                    }
                }
            }

            serverContent.optJSONObject("inputTranscription")?.optString("text")?.let { chunk ->
                if (chunk.isNotEmpty()) onInputTranscript(chunk)
            }
            serverContent.optJSONObject("outputTranscription")?.optString("text")?.let { chunk ->
                if (chunk.isNotEmpty()) onOutputTranscript(chunk)
            }

            if (serverContent.optBoolean("interrupted", false)) {
                clearPlayback()
                onInterrupted()
                onStateChange(LiveVoiceState.LISTENING)
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                onTurnComplete()
                onStateChange(LiveVoiceState.LISTENING)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Live API server message", e)
        }
    }

    // ---------- Mic capture & streaming ----------

    private fun startMicStreaming() {
        try {
            val minBuffer = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
                onError("इस डिवाइस पर Mic रिकॉर्डिंग सपोर्ट नहीं है।")
                teardown()
                return
            }

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                onError("Mic शुरू नहीं हो पाया। दोबारा कोशिश करें।")
                record.release()
                teardown()
                return
            }

            audioRecord = record
            record.startRecording()

            recordingJob = scope.launch {
                val buffer = ByteArray(MIC_CHUNK_SIZE)
                while (sessionActive) {
                    val read = try {
                        audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    } catch (e: Exception) {
                        -1
                    }
                    if (read > 0) {
                        val payload = if (read == buffer.size) buffer else buffer.copyOf(read)
                        onInputLevel(computeRms(payload))
                        sendAudioChunk(payload)
                    }
                }
            }
        } catch (se: SecurityException) {
            onError("Mic permission चाहिए।")
            teardown()
        } catch (e: Exception) {
            Log.e(TAG, "Mic init failed", e)
            onError("Mic शुरू नहीं हो पाया: ${e.localizedMessage ?: "अनजान गड़बड़ी"}")
            teardown()
        }
    }

    private fun sendAudioChunk(chunk: ByteArray) {
        val socket = webSocket ?: return
        val base64Audio = Base64.encodeToString(chunk, Base64.NO_WRAP)
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("audio", JSONObject().apply {
                    put("data", base64Audio)
                    put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                })
            })
        }
        try {
            socket.send(message.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    // ---------- Playback ----------

    private fun ensureAudioTrack(): AudioTrack {
        var track = audioTrack
        if (track == null) {
            val minBuffer = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, MIC_CHUNK_SIZE) * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
        }
        return track
    }

    private fun playAudioChunk(base64Audio: String) {
        try {
            val bytes = Base64.decode(base64Audio, Base64.NO_WRAP)
            onOutputLevel(computeRms(bytes))
            ensureAudioTrack().write(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
        }
    }

    /** Called when the user interrupts Myraa/Jarvis mid-sentence — stop audio immediately. */
    private fun clearPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear playback on interruption", e)
        }
        onOutputLevel(0f)
    }

    // Simple RMS amplitude (0f..1f) from 16-bit PCM, used to drive the orb's glow realistically
    // instead of a placeholder pulse.
    private fun computeRms(pcm16: ByteArray): Float {
        if (pcm16.size < 2) return 0f
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm16.size) {
            val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
            sum += (sample.toDouble() * sample.toDouble())
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f).let { abs(it) }
    }

    // ---------- Teardown ----------

    fun disconnect() {
        if (!sessionActive) return
        teardown()
    }

    private fun teardown() {
        sessionActive = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) { /* already stopped */ }
        try {
            audioRecord?.release()
        } catch (e: Exception) { /* ignore */ }
        audioRecord = null

        try {
            audioTrack?.stop()
        } catch (e: Exception) { /* already stopped */ }
        try {
            audioTrack?.release()
        } catch (e: Exception) { /* ignore */ }
        audioTrack = null

        try {
            webSocket?.close(1000, "client_disconnect")
        } catch (e: Exception) { /* ignore */ }
        webSocket = null

        onStateChange(LiveVoiceState.DISCONNECTED)
    }
}
