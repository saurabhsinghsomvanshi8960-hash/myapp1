package com.alphaorder.jarvisai.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// ---- Request models ----

data class GeminiContent(
    val role: String, // "user" or "model"
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.9,
    val maxOutputTokens: Int = 1024
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

// ---- Response models ----

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?
)

interface GeminiApiService {

    // model e.g. "gemini-2.0-flash" or "gemini-2.5-pro" (user-selectable in Settings)
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @retrofit2.http.Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}
