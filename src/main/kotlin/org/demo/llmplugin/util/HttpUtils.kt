package org.demo.llmplugin.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.demo.llmplugin.LocalLLMSettings
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

// 定义 ChatMessage 数据类
data class ChatMessage(val role: String, val content: String)

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

        // 创建消息列表
        val messages = listOf(
            ChatMessage("user", prompt)
        )

        val bodyObj = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", gson.toJsonTree(messages))
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
            
            val firstChoice = choices.get(0).asJsonObject
            if (!firstChoice.has("message")) {
                return@withContext "No message in choice"
            }
            
            val message = firstChoice.getAsJsonObject("message")
            val content = message.get("content")?.asString
            
            if (content == null) {
                return@withContext "No content in message"
            }
            
            content
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: JsonSyntaxException) {
            "JSON parsing error: ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * 解析流式响应
     */
    private fun parseStreamResponse(response: Response, onChunkReceived: (String) -> Unit): String {
        val reader = BufferedReader(InputStreamReader(response.body?.byteStream(), StandardCharsets.UTF_8))
        val fullResponse = StringBuilder()
        
        reader.use { bufferedReader ->
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) continue
                if (line!!.startsWith("data: ")) {
                    val dataStr = line!!.substring("data: ".length)
                    if (dataStr == "[DONE]") break
                    
                    try {
                        val json = JsonParser.parseString(dataStr).asJsonObject
                        if (json.has("choices")) {
                            val choices = json.getAsJsonArray("choices")
                            if (choices.size() > 0) {
                                val choice = choices.get(0).asJsonObject
                                if (choice.has("delta")) {
                                    val delta = choice.getAsJsonObject("delta")
                                    if (delta.has("content")) {
                                        val content = delta.get("content").asString
                                        if (!content.isNullOrBlank()) {
                                            onChunkReceived(content)
                                            fullResponse.append(content)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
            }
        }
        
        return fullResponse.toString()
    }

    /**
     * 创建系统消息和用户消息对象
     * 系统消息包含上下文代码（如果有）
     * 用户消息包含要测试的代码和用户指令
     *
     * 系统消息用于提供持续的上下文指导
     * 用户消息包含具体问题
     * 助手消息记录AI的回答
     *
     * 系统角色：提供规则和上下文
     * 用户角色：提出问题和指令
     * 助手角色：提供回答
     *
     */
    // 新增支持多个 ChatMessage 的方法
    suspend fun callLocalLlm(messages: List<ChatMessage>, onChunkReceived: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val settings = LocalLLMSettings.instance
        val url = "${settings.baseUrl.trimEnd('/')}/v1/chat/completions"

        val bodyObj = JsonObject().apply {
            addProperty("model", settings.model)
            add("messages", gson.toJsonTree(messages))
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
            
            val firstChoice = choices.get(0).asJsonObject
            if (!firstChoice.has("message")) {
                return@withContext "No message in choice"
            }
            
            val message = firstChoice.getAsJsonObject("message")
            val content = message.get("content")?.asString
            
            if (content == null) {
                return@withContext "No content in message"
            }
            
            content
        } catch (e: IOException) {
            "Network error: ${e.message}"
        } catch (e: JsonSyntaxException) {
            "JSON parsing error: ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}