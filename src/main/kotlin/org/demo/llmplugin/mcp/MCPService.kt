package org.demo.llmplugin.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * MCP服务，管理MCP服务器的生命周期
 */
@Service
class MCPManagerService(private val project: Project) {
    private val mcpServer: MCPServer = MCPServer(project)
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun getMCPServer(): MCPServer {
        return mcpServer
    }

    companion object {
        fun getInstance(project: Project): MCPManagerService {
            return project.service<MCPManagerService>()
        }
    }
}