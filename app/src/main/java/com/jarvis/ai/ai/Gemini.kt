package com.jarvis.ai.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiException(msg: String) : Exception(msg)

object Gemini {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val URL =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    fun generate(
        apiKey: String,
        model: String,
        system: String,
        prompt: String,
        imageJpegBase64: String? = null
    ): String {
        if (apiKey.isBlank()) {
            throw GeminiException("Gemini API key missing. Open Settings and paste your key.")
        }

        val parts = org.json.JSONArray()
        parts.put(JSONObject().put("text", prompt))

        if (imageJpegBase64 != null) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", imageJpegBase64)
                )
            )
        }

        val json = JSONObject()
            .put("system_instruction", JSONObject().put(
                "parts", org.json.JSONArray().put(JSONObject().put("text", system))
            ))
            .put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", parts)
            ))
            .toString()

        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(URL.format(model) + "?key=" + apiKey)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            when {
                resp.code == 429 -> {
                    val wait = resp.header("Retry-After")?.toIntOrNull()?.plus(1) ?: 30
                    throw GeminiException("API rate limit reached. Wait about ${wait}s and try again.")
                }
                resp.code == 400 && raw.contains("API_KEY_INVALID") ->
                    throw GeminiException("Invalid Gemini API key. Check Settings.")
                resp.code == 400 && raw.contains("API key not valid") ->
                    throw GeminiException("Invalid Gemini API key. Check Settings.")
                !resp.isSuccessful ->
                    throw GeminiException("Gemini error ${resp.code}: ${raw.take(160)}")
            }

            val root = JSONObject(raw)
            return root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }
}
