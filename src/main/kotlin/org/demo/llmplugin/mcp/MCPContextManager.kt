package org.demo.llmplugin.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.demo.llmplugin.util.ContextResource
import java.nio.file.Paths
import kotlin.text.Charsets

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
                val content = virtualFile.contentsToByteArray().toString(kotlin.text.Charsets.UTF_8)
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
            return VfsUtil.findFile(
                Paths.get(filePath),
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
            virtualFile.contentsToByteArray().toString(kotlin.text.Charsets.UTF_8)
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

    /**
     * 获取资源数量
     */
    fun getResourceCount(): Int {
        return resources.size
    }

    /**
     * 检查是否包含特定URI的资源
     */
    fun containsResource(uri: String): Boolean {
        return resources.containsKey(uri)
    }

    /**
     * 清空所有资源
     */
    fun clear() {
        resources.clear()
    }
}