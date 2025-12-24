package org.demo.llmplugin.mcp

import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.demo.llmplugin.util.ContextResource

/**
 * 远程MCP上下文服务
 * 用于从远程MCP服务器获取上下文资源
 */
class RemoteMCPContextService(private val project: Project) {
    private val mcpClient = MCPClient()
    
    /**
     * 从远程MCP服务器获取所有资源列表
     */
    suspend fun fetchRemoteResources(mcpUrl: String): List<ContextResource>? {
        return mcpClient.listResources(mcpUrl)?.resources
    }
    
    /**
     * 从远程MCP服务器获取特定资源内容
     */
    suspend fun fetchRemoteResourceContent(mcpUrl: String, uri: String): GetResourceContentResponse? {
        return mcpClient.getResourceContent(mcpUrl, uri)
    }
    
    /**
     * 批量获取远程资源内容
     */
    suspend fun fetchRemoteResourceContents(mcpUrl: String, uris: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        // 使用coroutineScope来管理并发任务
        coroutineScope {
            val deferredList = uris.map { uri ->
                async {
                    val contentResponse = mcpClient.getResourceContent(mcpUrl, uri)
                    if (contentResponse != null) {
                        result[uri] = contentResponse.text
                    }
                }
            }
            
            // 等待所有异步任务完成
            deferredList.awaitAll()
        }
        
        return result
    }
    
    /**
     * 将远程资源添加到本地MCP上下文管理器
     */
    suspend fun importRemoteResourcesToContext(mcpUrl: String, localContextManager: MCPContextManager): Boolean {
        val remoteResources = fetchRemoteResources(mcpUrl)
        
        if (remoteResources.isNullOrEmpty()) {
            return false
        }
        
        var successCount = 0
        
        for (resource in remoteResources) {
            // 如果资源没有内容，尝试获取内容后再添加
            val resourceWithContent = if (resource.content == null) {
                val contentResponse = fetchRemoteResourceContent(mcpUrl, resource.uri)
                if (contentResponse != null) {
                    resource.copy(content = contentResponse.text)
                } else {
                    resource
                }
            } else {
                resource
            }
            
            if (localContextManager.addResource(resourceWithContent)) {
                successCount++
            }
        }
        
        return successCount > 0
    }
    
    /**
     * 获取远程MCP服务器的上下文摘要
     */
    suspend fun getRemoteContextSummary(mcpUrl: String): String {
        return try {
            val resources = fetchRemoteResources(mcpUrl)
            if (resources.isNullOrEmpty()) {
                "远程MCP服务器上下文为空"
            } else {
                val summary = StringBuilder()
                summary.append("远程MCP服务器包含以下资源:\n")
                resources.forEachIndexed { index, resource ->
                    summary.append("${index + 1}. ${resource.name} (${resource.kind})\n")
                    summary.append("   URI: ${resource.uri}\n")
                    if (resource.description != null) {
                        summary.append("   描述: ${resource.description}\n")
                    }
                    summary.append("\n")
                }
                summary.toString()
            }
        } catch (e: Exception) {
            "获取远程上下文摘要失败: ${e.message}"
        }
    }
    
    /**
     * 关闭服务并清理资源
     */
    fun close() {
        mcpClient.close()
    }
}