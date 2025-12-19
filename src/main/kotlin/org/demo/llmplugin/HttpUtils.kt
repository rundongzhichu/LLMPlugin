package org.demo.llmplugin

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object HttpUtils {
    private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 增加读取超时到120秒
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()

    suspend fun callLocalLlm(prompt: String, onChunkReceived: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val settings = LocalLLMSettings.instance
        val url = "${settings.baseUrl.trimEnd('/')}/v1/chat/completions"

        val bodyObj = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", gson.toJsonTree(listOf(mapOf("role" to "user", "content" to prompt))))
            addProperty("temperature", 0.2)
            // 如果提供了onChunkReceived回调，则启用流式传输
            if (onChunkReceived != null) {
                addProperty("stream", true)
            }
        }

        val requestBody = bodyObj.toString().toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder().url(url).post(requestBody)
        if (settings.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            
            // 如果提供了onChunkReceived回调，则使用流式处理
            if (onChunkReceived != null) {
                return@withContext parseStreamResponse(response, onChunkReceived)
            }
            
            // 否则使用原有的处理方式
            if (!response.isSuccessful) {
                return@withContext "Error: HTTP ${response.code} - ${response.message}"
            }
            val responseBody = response.body?.string() ?: return@withContext "Empty response"
            
            // 检查响应体是否为空
            if (responseBody.isBlank()) {
                return@withContext "Empty response body"
            }
            
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            
            // 检查是否有错误字段
            if (json.has("error")) {
                val error = json.getAsJsonObject("error")
                val errorMessage = error.get("message")?.asString ?: "Unknown error"
                return@withContext "API Error: $errorMessage"
            }
            
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                return@withContext "No choices in response"
            }
            
            val content = choices
                    .get(0)
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    
            content ?: "No content in response"
        } catch (e: Exception) {
            "Network error: ${e.message ?: e.javaClass.simpleName}"
        }
    }
    
    private fun parseStreamResponse(response: Response, onChunkReceived: (String) -> Unit): String {
        if (!response.isSuccessful) {
            return "Error: HTTP ${response.code} - ${response.message}"
        }
        
        val responseBody = response.body ?: return "Empty response body"
        // 使用UTF-8字符集确保中文正确解码
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))
        val stringBuffer = StringBuffer()
        
        try {
            print(
                "Calling LLM API with prompt: parseStreamResponse"
            )
            println("onChunkReceived")
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("data: ")) {
                    val jsonData = line!!.substring(6) // 移除 "data: " 前缀
                    if (jsonData == "[DONE]") {
                        break
                    }
                    
                    try {
                        val json = JsonParser.parseString(jsonData).asJsonObject
                        val choices = json.getAsJsonArray("choices")
                        
                        if (choices != null && choices.size() > 0) {
                            val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                            if (delta != null && delta.has("content")) {
                                val content = delta.get("content").asString
                                stringBuffer.append(content)
                                // 调用回调函数传递新的内容块
                                onChunkReceived(content)
                            }
                        }
                    } catch (e: JsonSyntaxException) {
                        // 忽略解析错误的数据行
                        continue
                    }
                }
            }
        } catch (e: IOException) {
            return "Error reading stream: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            try {
                reader.close()
            } catch (e: IOException) {
                // 忽略关闭错误
            }
        }
        
        return stringBuffer.toString()
    }
}