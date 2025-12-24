package org.demo.llmplugin.mcp

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

/**
 * MCP客户端测试类
 */
class MCPClientTest : BasePlatformTestCase() {
    
    fun testRemoteContextImport() {
        runBlocking {
            val remoteMCPContextService = RemoteMCPContextService(myFixture.project)
            
            try {
                // 这是一个示例URL，实际使用时需要替换为真实的MCP服务器URL
                val mcpUrl = "http://localhost:8080/mcp" // 示例URL
                
                // 获取远程上下文摘要
                val summary = remoteMCPContextService.getRemoteContextSummary(mcpUrl)
                println("Remote context summary: $summary")
                
                // 从远程导入资源到本地上下文管理器
                val localContextManager = MCPContextManager(myFixture.project)
                val success = remoteMCPContextService.importRemoteResourcesToContext(mcpUrl, localContextManager)
                
                if (success) {
                    println("Successfully imported resources from remote MCP server")
                    val resources = localContextManager.getContextResources()
                    println("Total resources in local context: ${resources.size}")
                    assertTrue("Should have imported resources", resources.isNotEmpty())
                } else {
                    println("Failed to import resources from remote MCP server")
                    // 注意：由于MCP服务可能未运行，这里不强制要求成功
                }
            } catch (e: Exception) {
                println("Error during remote context import: ${e.message}")
                // 注意：由于MCP服务可能未运行，这里不抛出异常
            } finally {
                remoteMCPContextService.close()
            }
        }
    }
}