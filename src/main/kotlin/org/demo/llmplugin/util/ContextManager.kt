package org.demo.llmplugin.util

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.mcp.MCPContextManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 上下文管理工具类
 * 负责管理AI生成代码时所需的上下文信息
 * 现在支持MCP协议来管理上下文资源
 */
class ContextManager(private val project: Project? = null) {
    private val contextFiles = mutableSetOf<VirtualFile>()
    private val mcpContextManager: MCPContextManager? = project?.let { MCPContextManager(it) }
    private val lspContextExtractor: LSPContextExtractor? = project?.let { LSPContextExtractor(it) }
    
    /**　
     * 添加文件到上下文
     * @param file 要添加的文件
     * @return 如果文件是新添加的则返回true，如果已存在则返回false
     */
    fun addFileToContext(file: VirtualFile): Boolean {
        val added = contextFiles.add(file)
        // 同时添加到MCP上下文管理器
        mcpContextManager?.addVirtualFileToContext(file)
        return added
    }
    
    /**
     * 批量添加文件到上下文
     * @param files 要添加的文件数组
     * @return 实际添加的文件数量（去重后）
     */
    fun addFilesToContext(files: Array<VirtualFile>): Int {
        val initialSize = contextFiles.size
        contextFiles.addAll(files)
        val addedCount = contextFiles.size - initialSize
        
        // 同时添加到MCP上下文管理器
        files.forEach { file ->
            mcpContextManager?.addVirtualFileToContext(file)
        }
        
        return addedCount
    }
    
    /**
     * 添加结构化上下文（类、方法、字段信息）
     */
    fun addStructureContextFromPsiFile(psiFile: PsiFile): Int {
        val initialSize = contextFiles.size
        val virtualFile = psiFile.virtualFile
        if (virtualFile != null) {
            contextFiles.add(virtualFile)
        }
        
        // 使用LSP提取器获取结构化上下文并添加到MCP上下文管理器
        lspContextExtractor?.let { extractor ->
            val structureContextResources = extractor.extractStructureContextFromPsiFile(psiFile)
            structureContextResources.forEach { resource ->
                mcpContextManager?.addResource(resource)
            }
            return structureContextResources.size
        }
        
        return 0
    }
    
    /**
     * 添加语法上下文
     */
    fun addSyntaxContext(psiFile: PsiFile, offset: Int): Int {
        // 使用LSP提取器获取语法上下文并添加到MCP上下文管理器
        lspContextExtractor?.let { extractor ->
            val syntaxContextResources = extractor.extractSyntaxContext(psiFile, offset)
            syntaxContextResources.forEach { resource ->
                mcpContextManager?.addResource(resource)
            }
            return syntaxContextResources.size
        }
        
        return 0
    }
    
    /**
     * 添加虚拟文件的上下文（使用LSP方式）
     */
    fun addVirtualFileContext(virtualFile: VirtualFile): Boolean {
        // 使用LSP提取器从虚拟文件创建资源
        lspContextExtractor?.let { extractor ->
            val resource = extractor.createResourceFromVirtualFile(virtualFile)
            mcpContextManager?.addResource(resource)
            return true
        }
        
        // 回退到原始方法
        val added = contextFiles.add(virtualFile)
        mcpContextManager?.addVirtualFileToContext(virtualFile)
        return added
    }
    
    /**
     * 从上下文中移除文件
     */
    fun removeFileFromContext(file: VirtualFile) {
        contextFiles.remove(file)
        // 同时从MCP上下文管理器移除
        mcpContextManager?.removeResource("file://${file.path}")
    }
    
    /**
     * 清空上下文
     */
    fun clearContext() {
        contextFiles.clear()
        // 同时清空MCP上下文管理器
        mcpContextManager?.clearAllResources()
    }
    
    /**
     * 获取上下文中的所有文件
     */
    fun getContextFiles(): Set<VirtualFile> {
        return contextFiles.toSet()
    }
    
    /**
     * 检查文件是否已在上下文中
     */
    fun isFileInContext(file: VirtualFile): Boolean {
        return contextFiles.contains(file)
    }
    
    /**
     * 获取上下文中文件的数量
     */
    fun getContextFilesCount(): Int {
        return contextFiles.size
    }
    
    /**
     * 获取MCP上下文管理器
     */
    fun getMCPContextManager(): MCPContextManager? {
        return mcpContextManager
    }
    
    /**
     * 构建上下文代码字符串（压缩版）
     * 只保留必要的信息，减少冗余内容
     */
    fun buildCompressedContextCode(): String {
        val contextBuilder = StringBuilder()
        
        for (file in contextFiles) {
            if (file.isDirectory) {
                // 如果是目录，递归处理目录中的文件
                appendCompressedDirectoryContents(contextBuilder, file, "")
            } else {
                // 如果是文件，直接添加文件内容
                appendCompressedFileContent(contextBuilder, file, "")
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 显示文件选择对话框，让用户选择要添加到上下文的文件
     */
    fun showFileChooser(project: Project): Array<VirtualFile> {
        // 创建文件选择描述符，允许选择文件和目录
        val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
            .withTitle("选择上下文文件或目录")
            .withDescription("选择要作为上下文包含的文件或目录")
        
        // 显示文件选择对话框
        return FileChooser.chooseFiles(descriptor, project, null)
    }
    
    /**
     * 递归添加目录内容（压缩版）
     */
    private fun appendCompressedDirectoryContents(builder: StringBuilder, directory: VirtualFile, indent: String) {
        builder.append("${indent}[目录: ${directory.name}]\n")
        
        val children = directory.children
        for (child in children) {
            if (child.isDirectory) {
                appendCompressedDirectoryContents(builder, child, "$indent  ")
            } else {
                appendCompressedFileContent(builder, child, "$indent  ")
            }
        }
    }
    
    /**
     * 添加单个文件的内容（压缩版）
     * 只保留文件名和关键内容，去除不必要的空行和注释
     */
    private fun appendCompressedFileContent(builder: StringBuilder, file: VirtualFile, indent: String) {
        try {
            builder.append("${indent}[文件: ${file.name}]\n")
            
            val inputStream = file.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            var line: String?
            var lineCount = 0
            val maxLines = 800 // 限制每个文件的最大行数
            
            while (reader.readLine().also { line = it } != null && lineCount < maxLines) {
                // 过滤掉空行和纯注释行（简单过滤）
                if (!line.isNullOrBlank()) {
                    val trimmedLine = line!!.trim()
                    // 简单过滤一些常见的无意义注释行
                    if (!trimmedLine.startsWith("//") || 
                        (trimmedLine.startsWith("//") && trimmedLine.length > 5)) {
                        builder.append("$indent$line\n")
                        lineCount++
                    }
                }
            }
            
            // 如果文件内容超过限制，添加提示
            if (lineCount >= maxLines) {
                builder.append("${indent}// ... 文件内容已截断 ...\n")
            }
            
            builder.append("\n")
            reader.close()
        } catch (e: Exception) {
            builder.append("${indent}// 读取文件失败: ${file.name} - ${e.message}\n\n")
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ContextManager? = null
        
        fun getInstance(): ContextManager {
            throw UnsupportedOperationException("ContextManager requires a Project instance. Use getInstance(project) instead.")
        }
        
        fun getInstance(project: Project): ContextManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContextManager(project).also { INSTANCE = it }
            }
        }
        
        /**
         * 创建一个新的独立上下文管理器实例
         */
        fun createInstance(project: Project): ContextManager {
            return ContextManager(project)
        }
    }
}