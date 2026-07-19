package com.alphaorder.jarvisai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alphaorder.jarvisai.data.ErrorType
import com.alphaorder.jarvisai.data.GeminiContent
import com.alphaorder.jarvisai.data.GeminiPart
import com.alphaorder.jarvisai.data.GeminiRepository
import com.alphaorder.jarvisai.data.GeminiResult
import com.alphaorder.jarvisai.data.SettingsDataStore
import com.alphaorder.jarvisai.voice.SpeechToTextManager
import com.alphaorder.jarvisai.voice.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class JarvisState { IDLE, LISTENING, THINKING, SPEAKING, OFFLINE, ERROR }

data class ChatTurn(val isUser: Boolean, val text: String)

data class JarvisUiState(
    val state: JarvisState = JarvisState.IDLE,
    val partialText: String = "",
    val responseText: String = "",
    val micLevel: Float = 0f,
    val errorMessage: String? = null,
    val chatHistory: List<ChatTurn> = emptyList()
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val geminiRepository = GeminiRepository()
    private val speechToText = SpeechToTextManager(application)
    private val textToSpeech = TextToSpeechManager(application)

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    // conversation history sent to Gemini for context
    private val conversationHistory = mutableListOf<GeminiContent>()

    private var currentLanguage = "en-IN"
    private var currentModel = "gemini-2.5-flash"

    init {
        viewModelScope.launch {
            settingsDataStore.language.collect { lang ->
                currentLanguage = lang
                textToSpeech.initialize(lang)
            }
        }
        viewModelScope.launch {
            settingsDataStore.geminiModel.collect { model ->
                currentModel = model
            }
        }
        viewModelScope.launch {
            settingsDataStore.voiceGender.collect { gender ->
                textToSpeech.setVoiceGender(preferFemale = gender == "female")
            }
        }
    }

    fun startListening() {
        _uiState.value = _uiState.value.copy(state = JarvisState.LISTENING, partialText = "", errorMessage = null)
        speechToText.startListening(
            languageTag = currentLanguage,
            onReadyForSpeech = {
                _uiState.value = _uiState.value.copy(state = JarvisState.LISTENING)
            },
            onRmsChanged = { rms ->
                // normalize rmsdB (~0-10) to 0f..1f for waveform animation
                val normalized = (rms / 10f).coerceIn(0f, 1f)
                _uiState.value = _uiState.value.copy(micLevel = normalized)
            },
            onPartialResult = { partial ->
                _uiState.value = _uiState.value.copy(partialText = partial)
            },
            onResult = { finalText ->
                _uiState.value = _uiState.value.copy(partialText = "", micLevel = 0f)
                sendToGemini(finalText)
            },
            onError = { message ->
                _uiState.value = _uiState.value.copy(
                    state = JarvisState.IDLE,
                    micLevel = 0f,
                    errorMessage = message
                )
            }
        )
    }

    fun stopListening() {
        speechToText.stopListening()
        _uiState.value = _uiState.value.copy(state = JarvisState.IDLE, micLevel = 0f)
    }

    /** Text input fallback (typed message instead of voice). */
    fun sendTypedMessage(text: String) {
        if (text.isBlank()) return
        sendToGemini(text)
    }

    private fun sendToGemini(userText: String) {
        val updatedHistory = _uiState.value.chatHistory + ChatTurn(isUser = true, text = userText)
        _uiState.value = _uiState.value.copy(
            state = JarvisState.THINKING,
            chatHistory = updatedHistory,
            errorMessage = null
        )

        viewModelScope.launch {
            val apiKey = settingsDataStore.getApiKey()

            when (val result = geminiRepository.sendMessage(
                apiKey = apiKey,
                model = currentModel,
                history = conversationHistory,
                newMessage = userText
            )) {
                is GeminiResult.Success -> {
                    conversationHistory.add(GeminiContent(role = "user", parts = listOf(GeminiPart(userText))))
                    conversationHistory.add(GeminiContent(role = "model", parts = listOf(GeminiPart(result.text))))

                    val newHistory = _uiState.value.chatHistory + ChatTurn(isUser = false, text = result.text)
                    _uiState.value = _uiState.value.copy(
                        state = JarvisState.SPEAKING,
                        responseText = result.text,
                        chatHistory = newHistory
                    )

                    textToSpeech.speak(
                        text = result.text,
                        onDone = {
                            _uiState.value = _uiState.value.copy(state = JarvisState.IDLE)
                        },
                        onError = {
                            _uiState.value = _uiState.value.copy(state = JarvisState.IDLE)
                        }
                    )
                }

                is GeminiResult.Error -> {
                    val stateAfterError = if (result.type == ErrorType.NO_INTERNET) {
                        JarvisState.OFFLINE
                    } else {
                        JarvisState.ERROR
                    }
                    _uiState.value = _uiState.value.copy(
                        state = stateAfterError,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, state = JarvisState.IDLE)
    }

    override fun onCleared() {
        super.onCleared()
        speechToText.stopListening()
        textToSpeech.shutdown()
    }
}
