package com.alphaorder.jarvisai.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val text: String) : GeminiResult()
    data class Error(val type: ErrorType, val message: String) : GeminiResult()
}

enum class ErrorType {
    NO_INTERNET,
    INVALID_API_KEY,
    RATE_LIMIT,
    EMPTY_KEY,
    UNKNOWN
}

class GeminiRepository {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(GeminiApiService.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeminiApiService::class.java)

    /**
     * Free-tier models to try, in order, as of July 2026.
     * gemini-1.5-* and gemini-2.0-* were retired by Google (they now return 404).
     * If a model hits a 404 (retired/renamed) or 429 (rate limit), we automatically
     * fall back to the next one in this list so the app keeps working.
     */
    private val fallbackModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-3-flash",
        "gemini-3.1-flash-lite"
    )

    /**
     * Sends the full conversation history to Gemini so context is preserved.
     * Tries [model] first, then automatically walks through [fallbackModels]
     * if that model is unavailable (404) or rate-limited (429), so the user always
     * gets the best model that is currently working on their free API key.
     * @param history list of prior turns (role = "user"/"model")
     * @param newMessage the latest user utterance
     */
    suspend fun sendMessage(
        apiKey: String,
        model: String,
        history: List<GeminiContent>,
        newMessage: String
    ): GeminiResult {
        if (apiKey.isBlank()) {
            return GeminiResult.Error(ErrorType.EMPTY_KEY, "API key नहीं मिली। Settings में जाकर डालें।")
        }

        val contents = history + GeminiContent(role = "user", parts = listOf(GeminiPart(newMessage)))

        // Try preferred model first, then fall back to the rest (no duplicates).
        val modelsToTry = (listOf(model) + fallbackModels).distinct()

        var lastError: GeminiResult.Error? = null

        for (candidateModel in modelsToTry) {
            when (val result = tryModel(apiKey, candidateModel, contents)) {
                is GeminiResult.Success -> return result
                is GeminiResult.Error -> {
                    lastError = result
                    // These errors won't be fixed by switching models, so stop immediately.
                    if (result.type == ErrorType.INVALID_API_KEY || result.type == ErrorType.NO_INTERNET) {
                        return result
                    }
                    // Otherwise (model not found / rate limited / unknown) -> try next model.
                }
            }
        }

        return lastError ?: GeminiResult.Error(ErrorType.UNKNOWN, "कोई भी मॉडल उपलब्ध नहीं है, दोबारा कोशिश करें।")
    }

    private suspend fun tryModel(
        apiKey: String,
        model: String,
        contents: List<GeminiContent>
    ): GeminiResult {
        return try {
            val response = api.generateContent(
                model = model,
                apiKey = apiKey,
                request = GeminiRequest(contents = contents)
            )

            if (response.isSuccessful) {
                val text = response.body()
                    ?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.joinToString(" ") { it.text }
                    ?.trim()

                if (text.isNullOrEmpty()) {
                    GeminiResult.Error(ErrorType.UNKNOWN, "कोई जवाब नहीं मिला, दोबारा कोशिश करें।")
                } else {
                    GeminiResult.Success(text)
                }
            } else {
                when (response.code()) {
                    400, 401, 403 -> GeminiResult.Error(
                        ErrorType.INVALID_API_KEY,
                        "API key invalid लग रही है। Settings में जाँच लें।"
                    )
                    404 -> GeminiResult.Error(
                        ErrorType.UNKNOWN,
                        "मॉडल '$model' उपलब्ध नहीं है।"
                    )
                    429 -> GeminiResult.Error(
                        ErrorType.RATE_LIMIT,
                        "थोड़ी देर बाद फिर कोशिश करें (rate limit)।"
                    )
                    else -> GeminiResult.Error(
                        ErrorType.UNKNOWN,
                        "Server error (${response.code()})। दोबारा कोशिश करें।"
                    )
                }
            }
        } catch (e: IOException) {
            GeminiResult.Error(ErrorType.NO_INTERNET, "Internet connection नहीं है।")
        } catch (e: Exception) {
            GeminiResult.Error(ErrorType.UNKNOWN, "कुछ गलत हो गया: ${e.localizedMessage}")
        }
    }

    /** Lightweight ping used by "Test Connection" in Settings. */
    suspend fun testConnection(apiKey: String, model: String): GeminiResult {
        return sendMessage(apiKey, model, emptyList(), "Hello")
    }
}
