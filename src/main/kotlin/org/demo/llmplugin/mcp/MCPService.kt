package org.demo.llmplugin.mcp

import org.demo.llmplugin.util.ContextResource

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