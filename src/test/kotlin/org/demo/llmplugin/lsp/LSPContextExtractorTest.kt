package org.demo.llmplugin.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiFileFactory
import com.intellij.openapi.project.Project

/**
 * LSPContextExtractor 测试类
 * 测试LSP上下文提取功能
 */
class LSPContextExtractorTest : BasePlatformTestCase() {

    private lateinit var lspContextExtractor: LSPContextExtractor

    override fun setUp() {
        super.setUp()
        lspContextExtractor = LSPContextExtractor(myFixture.project)
    }

    fun testExtractStructureContext() {
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
        
        // 创建PSI文件
        val psiFile = PsiFileFactory.getInstance(testProject)
            .createFileFromText("TestClass.java", testCode)
        
        // 提取结构化上下文
        val context = lspContextExtractor.extractStructureContext(psiFile)
        
        // 验证提取的上下文包含关键信息
        assertTrue(context.contains("package: com.example"))
        assertTrue(context.contains("imports:"))
        assertTrue(context.contains("class: public TestClass"))
        assertTrue(context.contains("field: private String name"))
        assertTrue(context.contains("field: private int value"))
        assertTrue(context.contains("method: public String getName()"))
        assertTrue(context.contains("method: public void setValue(int value)"))
        
        println("Extracted context:\n$context")
    }

    fun testExtractChunkedStructureContext() {
        val largeCode = buildString {
            append("package com.example;\n\n")
            append("public class LargeClass {\n")
            
            // 添加一些字段和方法
            for (i in 1..50) {
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
        
        // 创建PSI文件
        val psiFile = PsiFileFactory.getInstance(testProject)
            .createFileFromText("LargeClass.java", largeCode)
        
        // 提取分片结构化上下文
        val chunks = lspContextExtractor.extractChunkedStructureContext(psiFile, 50) // 每片50行
        
        // 验证分片数量
        assertTrue(chunks.isNotEmpty())
        println("Number of chunks: ${chunks.size}")
        for ((index, chunk) in chunks.withIndex()) {
            println("Chunk ${index + 1}:\n$chunk\n")
        }
    }

    private val testProject: Project
        get() = myFixture.project
}