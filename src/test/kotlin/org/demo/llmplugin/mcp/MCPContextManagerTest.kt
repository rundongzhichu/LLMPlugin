package org.demo.llmplugin.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.demo.llmplugin.util.ContextResource
import org.junit.Test
import org.junit.Assert.*

/**
 * MCPContextManager 测试类
 */
class MCPContextManagerTest : BasePlatformTestCase() {

    private lateinit var mcpContextManager: MCPContextManager

    override fun setUp() {
        super.setUp()
        mcpContextManager = MCPContextManager(myFixture.project)
    }

    fun testAddResource() {
        val resource = ContextResource(
            uri = "file://test/file.java",
            name = "TestFile.java",
            kind = "file",
            description = "Test file for context manager",
            content = "public class Test {}",
            metadata = mapOf("size" to 100L, "type" to "code")
        )
        
        // 添加资源
        val added = mcpContextManager.addResource(resource)
        assertTrue("Resource should be added successfully", added)
        
        // 验证资源数量
        assertEquals(1, mcpContextManager.getResourceCount())
        
        // 尝试添加相同URI的资源，应该返回false
        val duplicateAdded = mcpContextManager.addResource(resource)
        assertFalse("Duplicate resource should not be added", duplicateAdded)
        
        // 验证资源数量未变化
        assertEquals(1, mcpContextManager.getResourceCount())
    }

    fun testRemoveResource() {
        val resource = ContextResource(
            uri = "file://test/file.java",
            name = "TestFile.java",
            kind = "file",
            description = "Test file for context manager",
            content = "public class Test {}",
            metadata = mapOf("size" to 100L, "type" to "code")
        )
        
        // 添加资源
        mcpContextManager.addResource(resource)
        assertEquals(1, mcpContextManager.getResourceCount())
        
        // 移除资源
        val removed = mcpContextManager.removeResource(resource.uri)
        assertTrue("Resource should be removed successfully", removed)
        
        // 验证资源数量
        assertEquals(0, mcpContextManager.getResourceCount())
    }

    fun testContainsResource() {
        val resource = ContextResource(
            uri = "file://test/file.java",
            name = "TestFile.java",
            kind = "file",
            description = "Test file for context manager",
            content = "public class Test {}",
            metadata = mapOf("size" to 100L, "type" to "code")
        )
        
        // 初始时资源不存在
        assertFalse("Resource should not exist initially", mcpContextManager.containsResource(resource.uri))
        
        // 添加资源
        mcpContextManager.addResource(resource)
        
        // 验证资源存在
        assertTrue("Resource should exist after adding", mcpContextManager.containsResource(resource.uri))
        
        // 移除资源
        mcpContextManager.removeResource(resource.uri)
        
        // 验证资源不存在
        assertFalse("Resource should not exist after removal", mcpContextManager.containsResource(resource.uri))
    }

    fun testGetContextResources() {
        val resource1 = ContextResource(
            uri = "file://test/file1.java",
            name = "TestFile1.java",
            kind = "file",
            description = "Test file 1 for context manager",
            content = "public class Test1 {}",
            metadata = mapOf("size" to 100L, "type" to "code")
        )
        
        val resource2 = ContextResource(
            uri = "file://test/file2.java",
            name = "TestFile2.java",
            kind = "file",
            description = "Test file 2 for context manager",
            content = "public class Test2 {}",
            metadata = mapOf("size" to 200L, "type" to "code")
        )
        
        // 添加资源
        mcpContextManager.addResource(resource1)
        mcpContextManager.addResource(resource2)
        
        // 获取所有资源
        val resources = mcpContextManager.getContextResources()
        
        // 验证资源数量和内容
        assertEquals(2, resources.size)
        assertTrue(resources.any { it.uri == resource1.uri })
        assertTrue(resources.any { it.uri == resource2.uri })
    }

    fun testClear() {
        val resource = ContextResource(
            uri = "file://test/file.java",
            name = "TestFile.java",
            kind = "file",
            description = "Test file for context manager",
            content = "public class Test {}",
            metadata = mapOf("size" to 100L, "type" to "code")
        )
        
        // 添加资源
        mcpContextManager.addResource(resource)
        assertEquals(1, mcpContextManager.getResourceCount())
        
        // 清空所有资源
        mcpContextManager.clear()
        
        // 验证资源数量
        assertEquals(0, mcpContextManager.getResourceCount())
    }
}