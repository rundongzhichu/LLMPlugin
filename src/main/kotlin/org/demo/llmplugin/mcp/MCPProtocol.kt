package org.demo.llmplugin.mcp

import org.demo.llmplugin.util.ContextResource

// MCP 协议相关的数据类
data class MCPRequest(
    val method: String,
    val params: Map<String, Any?>? = null,
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

data class GetResourceContentResponse(
    val text: String,
    val uri: String? = null,
    val mimeType: String? = null,
    val metadata: Map<String, Any>? = null
)

data class ListResourcesResponse(
    val resources: List<ContextResource>,
    val hasMore: Boolean = false,
    val nextCursor: String? = null
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
