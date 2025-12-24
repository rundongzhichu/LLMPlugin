package org.demo.llmplugin.mcp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Model Context Protocol (MCP) 相关数据结构定义
 * MCP协议用于管理大模型对话的上下文信息
 */
data class MCPRequest(
    val method: String,
    val params: Map<String, Any>? = null,
    val id: String? = null
)

data class MCPResponse(
    val result: Any? = null,
    val error: MCPError? = null,
    val id: String? = null
)

data class MCPError(
    val code: Int,
    val message: String
)

/**
 * 上下文资源定义
 */
data class ContextResource(
    val uri: String,  // 资源URI，可以是文件路径或代码片段标识符
    val name: String,  // 资源名称
    val kind: String,  // 资源类型（如：file, directory, code_snippet等）
    val description: String? = null,  // 资源描述
    val content: String? = null,  // 资源内容（可选，按需获取）
    val metadata: Map<String, Any>? = null  // 额外元数据
)

/**
 * 上下文资源列表响应
 */
data class ListResourcesResponse(
    val resources: List<ContextResource>,
    @SerializedName("has_more") val hasMore: Boolean = false,
    @SerializedName("next_cursor") val nextCursor: String? = null
)

/**
 * 获取资源内容的请求参数
 */
data class GetResourceContentRequest(
    val uri: String
)

/**
 * 资源内容响应
 */
data class GetResourceContentResponse(
    val uri: String,
    val mimeType: String = "text/plain",
    val text: String,
    val metadata: Map<String, Any>? = null
)

/**
 * MCP协议定义的错误码
 */
object MCPErrorCodes {
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INTERNAL_ERROR = -32603
    const val RESOURCE_NOT_FOUND = 404
    const val INVALID_PARAMS = -32602
}

/**
 * MCP服务器接口
 */
interface MCPService {
    fun listResources(): ListResourcesResponse
    fun getResourceContent(uri: String): GetResourceContentResponse
    fun addResource(resource: ContextResource): Boolean
    fun removeResource(uri: String): Boolean
    fun clearAllResources(): Boolean
}

/**
 * MCP上下文管理器实现
 */
class MCPContextManager(private val project: Project) : MCPService {
    private val resources = mutableMapOf<String, ContextResource>()

    override fun listResources(): ListResourcesResponse {
        return ListResourcesResponse(resources.values.toList())
    }

    override fun getResourceContent(uri: String): GetResourceContentResponse {
        val resource = resources[uri] ?: throw RuntimeException("Resource not found: $uri")
        
        // 如果资源内容已存在，直接返回
        if (resource.content != null) {
            return GetResourceContentResponse(
                uri = uri,
                text = resource.content,
                metadata = resource.metadata
            )
        }
        
        // 否则尝试从虚拟文件读取内容
        try {
            val virtualFile = getVirtualFileFromUri(uri)
            if (virtualFile != null && virtualFile.isValid) {
                val content = virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
                return GetResourceContentResponse(
                    uri = uri,
                    text = content,
                    metadata = resource.metadata
                )
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read resource content: ${e.message}")
        }
        
        throw RuntimeException("Resource content not available: $uri")
    }

    override fun addResource(resource: ContextResource): Boolean {
        resources[resource.uri] = resource
        return true
    }

    override fun removeResource(uri: String): Boolean {
        return resources.remove(uri) != null
    }

    override fun clearAllResources(): Boolean {
        resources.clear()
        return true
    }

    /**
     * 从URI获取虚拟文件
     */
    private fun getVirtualFileFromUri(uri: String): VirtualFile? {
        // 简单实现：假设URI是文件路径
        // 在实际实现中，可能需要更复杂的URI解析逻辑
        if (uri.startsWith("file://")) {
            val filePath = uri.substring("file://".length)
            return com.intellij.openapi.vfs.VfsUtil.findFile(
                java.nio.file.Paths.get(filePath),
                false
            )
        }
        return null
    }

    /**
     * 从项目虚拟文件创建上下文资源
     */
    fun createResourceFromVirtualFile(virtualFile: VirtualFile): ContextResource {
        val uri = "file://${virtualFile.path}"
        val content = try {
            virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        
        return ContextResource(
            uri = uri,
            name = virtualFile.name,
            kind = if (virtualFile.isDirectory) "directory" else "file",
            description = "File in project: ${virtualFile.path}",
            content = content,
            metadata = mapOf<String, Any>(
                "path" to (virtualFile.path as Any),
                "size" to (virtualFile.length as Any),
                "extension" to (virtualFile.extension ?: "" as Any),
                "isDirectory" to (virtualFile.isDirectory as Any)
            )
        )
    }

    /**
     * 添加虚拟文件到上下文
     */
    fun addVirtualFileToContext(virtualFile: VirtualFile): Boolean {
        val resource = createResourceFromVirtualFile(virtualFile)
        return addResource(resource)
    }

    /**
     * 获取所有上下文资源的URI列表
     */
    fun getContextResourceUris(): List<String> {
        return resources.keys.toList()
    }

    /**
     * 获取所有上下文资源
     */
    fun getContextResources(): List<ContextResource> {
        return resources.values.toList()
    }
}