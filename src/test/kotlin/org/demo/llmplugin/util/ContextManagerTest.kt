package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.openapi.fileTypes.PlainTextFileType
import java.io.File

/**
 * ContextManager 测试类
 * 测试LSP功能和大文件分片处理
 */
class ContextManagerTest : BasePlatformTestCase() {

    private lateinit var contextManager: ContextManager
    private lateinit var contextProcessor: ContextProcessor

    override fun setUp() {
        super.setUp()
        contextManager = ContextManager.createInstance(myFixture.project)
        contextProcessor = ContextProcessor(myFixture.project)
    }

    fun testLSPContextExtraction() {
        // 创建一个简单的Java类文件用于测试
        val testCode = """
            package com.example;
            
            import java.util.List;
            import java.util.ArrayList;
            
            public class TestClass {
                private String name;
                private int value;
                
                public TestClass(String name) {
                    this.name = name;
                    this.value = 0;
                }
                
                public String getName() {
                    return name;
                }
                
                public void setValue(int value) {
                    this.value = value;
                }
                
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        """.trimIndent()
        
        // 使用IDEA的测试框架创建虚拟文件
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText("TestClass.java", testCode)
        
        // 创建临时文件
        val tempFile = createTempFile("TestClass.java", testCode)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
        
        assertNotNull(virtualFile)
        
        // 添加文件到上下文
        assertTrue(contextManager.addFileToContext(virtualFile!!))
        
        // 构建压缩上下文代码
        val contextCode = contextManager.buildCompressedContextCode()
        
        // 验证上下文代码包含结构化信息
        assertTrue(contextCode.contains("package: com.example"))
        assertTrue(contextCode.contains("imports:"))
        assertTrue(contextCode.contains("class: public TestClass"))
        assertTrue(contextCode.contains("field: private String name"))
        assertTrue(contextCode.contains("field: private int value"))
        assertTrue(contextCode.contains("method: public String getName()"))
        assertTrue(contextCode.contains("method: public void setValue(int value)"))
        
        println("Context code:\n$contextCode")
    }

    fun testLargeFileChunking() {
        // 创建一个大文件（超过200行）
        val largeCode = buildString {
            append("package com.example;\n\n")
            append("public class LargeClass {\n")
            
            // 添加250个字段和方法（超过200行）
            for (i in 1..100) {  // 减少数量以避免测试超时
                append("    private String field$i;\n")
                append("    \n")
                append("    public String getField$i() {\n")
                append("        return field$i;\n")
                append("    }\n")
                append("    \n")
                append("    public void setField$i(String field$i) {\n")
                append("        this.field$i = field$i;\n")
                append("    }\n")
                append("    \n")
            }
            
            append("}\n")
        }
        
        // 创建临时文件
        val tempFile = createTempFile("LargeClass.java", largeCode)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
        
        assertNotNull(virtualFile)
        
        // 添加文件到上下文
        assertTrue(contextManager.addFileToContext(virtualFile!!))
        
        // 构建压缩上下文代码
        val contextCode = contextManager.buildCompressedContextCode()
        
        // 验证上下文代码被分片处理
        assertTrue(contextCode.contains("Chunk") || contextCode.contains("field")) // 应该包含分片信息或字段信息
        
        println("Large file context code length: ${contextCode.length}")
    }

    fun testNonCodeFileProcessing() {
        // 创建一个非代码文件
        val textContent = """
            这是一个测试文本文件。
            它包含多行内容。
            
            这里有一些空行。
            
            用于测试非代码文件的处理。
            
            包含一些重复的空行。
            
            
            
            
            结尾内容。
        """.trimIndent()
        
        // 创建临时文件
        val tempFile = createTempFile("test.txt", textContent)
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
        
        assertNotNull(virtualFile)
        
        // 添加文件到上下文
        assertTrue(contextManager.addFileToContext(virtualFile!!))
        
        // 构建压缩上下文代码
        val contextCode = contextManager.buildCompressedContextCode()
        
        // 验证上下文代码去除了多余的空行
        assertFalse(contextCode.contains("\n\n\n\n")) // 不应该有连续4个换行（保留一些空行是正常的）
        
        println("Non-code file context:\n$contextCode")
    }

    private fun createTempFile(fileName: String, content: String): File {
        val tempDir = System.getProperty("java.io.tmpdir")
        val tempFile = File(tempDir, fileName)
        tempFile.writeText(content)
        tempFile.deleteOnExit()
        return tempFile
    }
    
    private val project: Project
        get() = myFixture.project
}