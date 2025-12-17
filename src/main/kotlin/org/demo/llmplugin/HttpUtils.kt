package org.demo.llmplugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object HttpUtils {
    private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    suspend fun callLocalLlm(prompt: String): String = withContext(Dispatchers.IO) {
        val settings = LocalLLMSettings.instance
        val url = "${settings.baseUrl.trimEnd('/')}/v1/chat/completions"

        val bodyObj = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to prompt))))
            addProperty("temperature", 0.2)
        }

        val requestBody = bodyObj.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder().url(url).post(requestBody)
        if (settings.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext "Error: HTTP ${response.code}"
            }
            val responseBody = response.body?.string() ?: return@withContext "Empty response"
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val content = json.getAsJsonArray("choices")
                    ?.get(0)
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
            content ?: "No content in response"
        } catch (e: Exception) {
            "Network error: ${e.message}"
        }
    }
}