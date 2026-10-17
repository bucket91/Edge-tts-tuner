package com.bucket91.ttstuner.logic

import com.bucket91.ttstuner.data.Rule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val rewrittenText: String) : GeminiResult()
    data class Skipped(val reason: String) : GeminiResult()
    data class Error(val message: String, val technicalDetail: String = "") : GeminiResult()
}

class GeminiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun testConnection(apiKey: String, model: String): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Gemini API key is missing. Please save your key in Settings."))
        }

        val testRule = Rule(text = "Test rule", enabled = true)
        val systemInstruction = PromptBuilder.buildSystemInstruction(listOf(testRule))
        val bodyJson = buildRequestBody(systemInstruction, "পরীক্ষা")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val responseBody = response.body?.string().orEmpty()

                when (code) {
                    200 -> Result.success("Connection test successful! Model: $model responded OK.")
                    400, 401, 403 -> Result.failure(
                        Exception("Check your Gemini key and model name in Settings (HTTP $code). $responseBody")
                    )
                    429 -> Result.failure(
                        Exception("Gemini rate limit, wait a minute (HTTP 429).")
                    )
                    else -> Result.failure(
                        Exception("Gemini returned HTTP $code: $responseBody")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Could not connect to Gemini: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun executeRespellingPass(
        textWithPlaceholders: String,
        replacements: List<PlaceholderReplacement>,
        rules: List<Rule>,
        apiKey: String,
        model: String
    ): GeminiResult {
        val enabledRules = rules.filter { it.enabled && it.text.isNotBlank() }
        if (enabledRules.isEmpty()) {
            return GeminiResult.Skipped("Rules pass skipped: No enabled rules")
        }

        if (apiKey.isBlank()) {
            return GeminiResult.Skipped("Rules pass skipped: Gemini API key not configured")
        }

        val systemInstruction = PromptBuilder.buildSystemInstruction(enabledRules)
        val requestBodyString = buildRequestBody(systemInstruction, textWithPlaceholders)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"

        // Execute attempt 1
        val firstAttempt = makeSingleRequest(url, apiKey, requestBodyString, textWithPlaceholders, replacements)
        if (firstAttempt is AttemptResult.Success) {
            return GeminiResult.Success(firstAttempt.text)
        } else if (firstAttempt is AttemptResult.HttpError) {
            return GeminiResult.Error(firstAttempt.plainMessage, firstAttempt.details)
        }

        // Retry once for placeholder or word count validation failures
        val secondAttempt = makeSingleRequest(url, apiKey, requestBodyString, textWithPlaceholders, replacements)
        return when (secondAttempt) {
            is AttemptResult.Success -> GeminiResult.Success(secondAttempt.text)
            is AttemptResult.HttpError -> GeminiResult.Error(secondAttempt.plainMessage, secondAttempt.details)
            is AttemptResult.ValidationFailure -> {
                GeminiResult.Skipped("Rules pass skipped: Gemini changed protected words")
            }
        }
    }

    private sealed class AttemptResult {
        data class Success(val text: String) : AttemptResult()
        data class ValidationFailure(val reason: String) : AttemptResult()
        data class HttpError(val plainMessage: String, val details: String) : AttemptResult()
    }

    private fun makeSingleRequest(
        url: String,
        apiKey: String,
        requestBodyString: String,
        inputText: String,
        replacements: List<PlaceholderReplacement>
    ): AttemptResult {
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()

                if (code == 400 || code == 401 || code == 403) {
                    return AttemptResult.HttpError(
                        "Check your Gemini key and model name in Settings",
                        "HTTP $code: $body"
                    )
                }
                if (code == 429) {
                    return AttemptResult.HttpError(
                        "Gemini rate limit, wait a minute",
                        "HTTP 429: $body"
                    )
                }
                if (!response.isSuccessful) {
                    return AttemptResult.HttpError(
                        "Gemini request failed (HTTP $code)",
                        body
                    )
                }

                // Parse response JSON
                val rootJson = try {
                    jsonParser.parseToJsonElement(body).jsonObject
                } catch (e: Exception) {
                    return AttemptResult.ValidationFailure("Invalid JSON response from server: ${e.message}")
                }

                val candidates = rootJson["candidates"]?.jsonArray
                val firstCandidate = candidates?.firstOrNull()?.jsonObject
                val content = firstCandidate?.get("content")?.jsonObject
                val parts = content?.get("parts")?.jsonArray
                val rawTextPart = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

                if (rawTextPart.isNullOrEmpty()) {
                    return AttemptResult.ValidationFailure("Empty candidate content")
                }

                // The output is structured JSON: {"text": "<the rewritten text>"}
                val parsedContent = try {
                    val innerJson = jsonParser.parseToJsonElement(rawTextPart.trim()).jsonObject
                    innerJson["text"]?.jsonPrimitive?.content ?: rawTextPart
                } catch (e: Exception) {
                    // In case Gemini returned the text directly or in markdown block
                    val cleaned = rawTextPart.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    try {
                        jsonParser.parseToJsonElement(cleaned).jsonObject["text"]?.jsonPrimitive?.content ?: cleaned
                    } catch (ex: Exception) {
                        cleaned
                    }
                }

                // Validate placeholders
                val placeholdersValid = WordCorrector.validatePlaceholders(parsedContent, replacements)
                if (!placeholdersValid) {
                    return AttemptResult.ValidationFailure("Placeholders missing or altered")
                }

                // Validate whitespace-separated word count equals input
                val inputWordsCount = inputText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                val outputWordsCount = parsedContent.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                if (inputWordsCount != outputWordsCount) {
                    return AttemptResult.ValidationFailure(
                        "Word count mismatch: expected $inputWordsCount words, got $outputWordsCount"
                    )
                }

                AttemptResult.Success(parsedContent)
            }
        } catch (e: IOException) {
            AttemptResult.HttpError("Network error while connecting to Gemini", e.localizedMessage ?: e.message.orEmpty())
        } catch (e: Exception) {
            AttemptResult.HttpError("Unexpected error during Gemini call", e.localizedMessage ?: e.message.orEmpty())
        }
    }

    private fun buildRequestBody(systemInstruction: String, userText: String): String {
        val root = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemInstruction) })
                }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", userText) })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("text") {
                            put("type", "STRING")
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("text"))
                    }
                }
            }
        }
        return root.toString()
    }
}
