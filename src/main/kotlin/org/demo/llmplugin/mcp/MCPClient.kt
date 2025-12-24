package org.demo.llmplugin.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.demo.llmplugin.util.ContextResource

/**
 * MCP (Model Context Protocol) 客户端实现
 * 用于调用远程MCP服务
 */
class MCPClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * 向远程MCP服务器发送请求
     */
    suspend fun sendRequest(mcpUrl: String, request: MCPRequest): MCPResponse = withContext(Dispatchers.IO) {
        val requestBody = gson.toJson(request).toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(mcpUrl)
            .post(requestBody)
            .build()
        
        try {
            val response = httpClient.newCall(httpRequest).execute()
            
            if (!response.isSuccessful) {
                return@withContext MCPResponse(
                    error = MCPError(
                        code = MCPErrorCodes.INVALID_REQUEST,
                        message = "HTTP ${response.code}: ${response.message}"
                    ),
                    id = request.id
                )
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return@withContext MCPResponse(
                    error = MCPError(
                        code = MCPErrorCodes.INTERNAL_ERROR,
                        message = "Empty response body"
                    ),
                    id = request.id
                )
            }
            
            // 解析响应
            parseMCPResponse(responseBody, request.id)
        } catch (e: IOException) {
            MCPResponse(
                error = MCPError(
                    code = MCPErrorCodes.INTERNAL_ERROR,
                    message = "Network error: ${e.message}"
                ),
                id = request.id
            )
        } catch (e: Exception) {
            MCPResponse(
                error = MCPError(
                    code = MCPErrorCodes.INTERNAL_ERROR,
                    message = "Error processing request: ${e.message}"
                ),
                id = request.id
            )
        }
    }
    
    /**
     * 解析MCP响应
     */
    private fun parseMCPResponse(responseBody: String, requestId: String?): MCPResponse {
        return try {
            val jsonObject = JsonParser.parseString(responseBody).asJsonObject
            
            // 检查是否有错误字段
            if (jsonObject.has("error")) {
                val errorObj = jsonObject.getAsJsonObject("error")
                val code = errorObj.get("code").asInt
                val message = errorObj.get("message").asString
                MCPResponse(
                    error = MCPError(code, message),
                    id = jsonObject.get("id")?.asString ?: requestId
                )
            } else {
                // 有结果字段
                val result = if (jsonObject.has("result")) {
                    jsonObject.get("result")
                } else {
                    null
                }
                
                MCPResponse(
                    result = result,
                    id = jsonObject.get("id")?.asString ?: requestId
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                error = MCPError(
                    code = MCPErrorCodes.INTERNAL_ERROR,
                    message = "Failed to parse response: ${e.message}"
                ),
                id = requestId
            )
        }
    }
    
    /**
     * 列出远程服务器的资源
     */
    suspend fun listResources(mcpUrl: String): ListResourcesResponse? = withContext(Dispatchers.IO) {
        val request = MCPRequest(
            method = "resources/list",
            id = generateRequestId()
        )
        
        val response = sendRequest(mcpUrl, request)
        
        if (response.error != null) {
            null
        } else {
            try {
                val result = response.result
                if (result is JsonObject) {
                    val resourcesArray = result.getAsJsonArray("resources")
                    val resources = resourcesArray?.map { resourceElement ->
                        gson.fromJson(resourceElement, ContextResource::class.java)
                    } ?: emptyList()
                    
                    val hasMore = if (result.has("has_more")) result.get("has_more").asBoolean else false
                    val nextCursor = if (result.has("next_cursor")) result.get("next_cursor").asString else null
                    
                    ListResourcesResponse(resources, hasMore, nextCursor)
                } else {
                    ListResourcesResponse(emptyList())
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 获取远程服务器的资源内容
     */
    suspend fun getResourceContent(mcpUrl: String, uri: String): GetResourceContentResponse? = withContext(Dispatchers.IO) {
        val request = MCPRequest(
            method = "resources/read",
            params = mapOf("uri" to uri),
            id = generateRequestId()
        )
        
        val response = sendRequest(mcpUrl, request)
        
        if (response.error != null) {
            null
        } else {
            try {
                gson.fromJson(gson.toJson(response.result), GetResourceContentResponse::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 添加资源到远程服务器
     */
    suspend fun addResource(mcpUrl: String, resource: ContextResource): Boolean = withContext(Dispatchers.IO) {
        val request = MCPRequest(
            method = "context/add",
            params = mapOf(
                "uri" to resource.uri,
                "name" to resource.name,
                "kind" to resource.kind,
                "description" to resource.description,
                "content" to resource.content,
                "metadata" to resource.metadata
            ).filterValues { it != null },
            id = generateRequestId()
        )
        
        val response = sendRequest(mcpUrl, request)
        
        if (response.error != null) {
            false
        } else {
            try {
                val result = response.result as? Map<*, *>
                result?.get("success") as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 从远程服务器移除资源
     */
    suspend fun removeResource(mcpUrl: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        val request = MCPRequest(
            method = "context/remove",
            params = mapOf("uri" to uri),
            id = generateRequestId()
        )
        
        val response = sendRequest(mcpUrl, request)
        
        if (response.error != null) {
            false
        } else {
            try {
                val result = response.result as? Map<*, *>
                result?.get("success") as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 生成请求ID
     */
    private fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
    
    /**
     * 关闭客户端并清理资源
     */
    fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}