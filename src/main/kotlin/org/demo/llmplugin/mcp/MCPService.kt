package org.demo.llmplugin.mcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * MCP服务，现在主要用于项目级的协程管理
 */
@Service
class MCPManagerService(private val project: Project) {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        fun getInstance(project: Project): MCPManagerService {
            return project.service<MCPManagerService>()
        }
    }
}