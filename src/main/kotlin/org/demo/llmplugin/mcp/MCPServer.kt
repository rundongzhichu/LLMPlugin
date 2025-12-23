package org.demo.llmplugin.mcp

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.demo.llmplugin.util.ChatMessage
import org.demo.llmplugin.util.HttpUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP (Model Context Protocol) 服务器实现
 * 负责处理MCP协议请求并管理上下文
 */
class MCPServer(private val project: Project) {
    private val gson = Gson()
    private val mcpContextManager = MCPContextManager(project)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 处理MCP请求
     */
    suspend fun handleRequest(request: MCPRequest): MCPResponse {
        return try {
            when (request.method) {
                "resources/list" -> {
                    val resources = mcpContextManager.listResources()
                    MCPResponse(result = resources, id = request.id)
                }
                
                "resources/read" -> {
                    val uri = request.params?.get("uri") as? String
                    if (uri != null) {
                        val content = mcpContextManager.getResourceContent(uri)
                        MCPResponse(result = content, id = request.id)
                    } else {
                        MCPResponse(
                            error = MCPError(
                                code = MCPErrorCodes.INVALID_PARAMS,
                                message = "Missing uri parameter"
                            ),
                            id = request.id
                        )
                    }
                }
                
                "context/add" -> {
                    val resource = parseResourceFromParams(request.params)
                    if (resource != null) {
                        val success = mcpContextManager.addResource(resource)
                        MCPResponse(result = mapOf("success" to success), id = request.id)
                    } else {
                        MCPResponse(
                            error = MCPError(
                                code = MCPErrorCodes.INVALID_PARAMS,
                                message = "Invalid resource parameter"
                            ),
                            id = request.id
                        )
                    }
                }
                
                "context/remove" -> {
                    val uri = request.params?.get("uri") as? String
                    if (uri != null) {
                        val success = mcpContextManager.removeResource(uri)
                        MCPResponse(result = mapOf("success" to success), id = request.id)
                    } else {
                        MCPResponse(
                            error = MCPError(
                                code = MCPErrorCodes.INVALID_PARAMS,
                                message = "Missing uri parameter"
                            ),
                            id = request.id
                        )
                    }
                }
                
                "context/clear" -> {
                    val success = mcpContextManager.clearAllResources()
                    MCPResponse(result = mapOf("success" to success), id = request.id)
                }
                
                else -> {
                    MCPResponse(
                        error = MCPError(
                            code = MCPErrorCodes.METHOD_NOT_FOUND,
                            message = "Method not supported: ${request.method}"
                        ),
                        id = request.id
                    )
                }
            }
        } catch (e: Exception) {
            MCPResponse(
                error = MCPError(
                    code = MCPErrorCodes.INTERNAL_ERROR,
                    message = e.message ?: "Internal error"
                ),
                id = request.id
            )
        }
    }
    
    /**
     * 解析资源参数
     */
    private fun parseResourceFromParams(params: Map<String, Any>?): ContextResource? {
        if (params == null) return null
        
        val uri = params["uri"] as? String ?: return null
        val name = params["name"] as? String ?: uri.substringAfterLast("/")
        val kind = params["kind"] as? String ?: "file"
        
        return ContextResource(
            uri = uri,
            name = name,
            kind = kind,
            description = params["description"] as? String,
            content = params["content"] as? String,
            metadata = params["metadata"] as? Map<String, Any>
        )
    }
    
    /**
     * 获取MCP上下文管理器
     */
    fun getMCPContextManager(): MCPContextManager {
        return mcpContextManager
    }
    
    /**
     * 与LLM通信时使用MCP协议优化上下文传输
     */
    suspend fun callLLMWithMCPContext(
        userMessage: String,
        onChunkReceived: ((String) -> Unit)? = null
    ): String {
        // 构建包含上下文引用的消息，而不是完整的上下文内容
        val contextResources = mcpContextManager.listResources().resources
        
        // 创建系统消息，只包含上下文引用
        val systemMessageContent = buildContextReferenceMessage(contextResources)
        
        val messages = listOf(
            ChatMessage("system", systemMessageContent),
            ChatMessage("user", userMessage)
        )
        
        // 使用流式响应调用LLM
        return HttpUtils.callLocalLlm(messages, onChunkReceived)
    }
    
    /**
     * 构建上下文引用消息
     */
    private fun buildContextReferenceMessage(resources: List<ContextResource>): String {
        if (resources.isEmpty()) {
            return "你是一个专业的编程助手，帮助用户解答编程相关问题。"
        }
        
        val contextInfo = StringBuilder()
        contextInfo.append("当前上下文包含以下资源，AI可以根据需要请求特定资源的内容：\n\n")
        
        resources.forEachIndexed { index, resource ->
            contextInfo.append("${index + 1}. ${resource.name} (${resource.kind})\n")
            contextInfo.append("   URI: ${resource.uri}\n")
            if (resource.description != null) {
                contextInfo.append("   描述: ${resource.description}\n")
            }
            contextInfo.append("\n")
        }
        
        contextInfo.append("\n如果AI需要查看特定资源的详细内容，可以请求该资源。")
        
        return contextInfo.toString()
    }
    
    /**
     * 关闭服务器并清理资源
     */
    fun shutdown() {
        coroutineScope.cancel()
    }
}