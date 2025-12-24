package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.demo.llmplugin.lsp.LSPContextExtractor

/**
 * 上下文管理工具类
 * 负责管理AI生成代码时所需的上下文信息
 * 支持LSP功能，通过LSP获取代码文件结构化上下文
 * 其他文件去除不必要信息后原文填入上下文
 * 大文件200行做分片
 */
class ContextManager(private val project: Project? = null) {
    private val contextFiles = mutableSetOf<VirtualFile>()
    private val lspContextExtractor = if (project != null) LSPContextExtractor(project) else null
    
    /**　
     * 添加文件到上下文
     * @param file 要添加的文件
     * @return 如果文件是新添加的则返回true，如果已存在则返回false
     */
    fun addFileToContext(file: VirtualFile): Boolean {
        return contextFiles.add(file)
    }
    
    /**
     * 批量添加文件到上下文
     * @param files 要添加的文件数组
     * @return 实际添加的文件数量（去重后）
     */
    fun addFilesToContext(files: Array<VirtualFile>): Int {
        val initialSize = contextFiles.size
        contextFiles.addAll(files)
        return contextFiles.size - initialSize
    }
    
    /**
     * 从上下文中移除文件
     */
    fun removeFileFromContext(file: VirtualFile) {
        contextFiles.remove(file)
    }
    
    /**
     * 清空上下文
     */
    fun clearContext() {
        contextFiles.clear()
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
     * 构建上下文代码字符串（压缩版）
     * 只保留必要的信息，减少冗余内容
     * 支持LSP功能，通过LSP获取代码文件结构化上下文
     * 其他文件去除不必要信息后原文填入上下文
     * 大文件200行做分片
     */
    fun buildCompressedContextCode(): String {
        val contextBuilder = StringBuilder()
        
        for (file in contextFiles) {
            if (file.isDirectory) {
                // 如果是目录，递归处理目录中的文件
                appendCompressedDirectoryContents(contextBuilder, file, "")
            } else {
                // 如果是文件，根据文件类型处理
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
     * 对代码文件使用LSP提取结构化上下文
     * 大文件按200行分片处理
     */
    private fun appendCompressedFileContent(builder: StringBuilder, file: VirtualFile, indent: String) {
        try {
            // 判断是否为代码文件
            val isCodeFile = ContextResource.isCodeFile(file)
            
            builder.append("${indent}[文件: ${file.name}]\n")
            
            // 读取文件内容
            val content = file.contentsToByteArray().toString(StandardCharsets.UTF_8)
            val lines = content.lines()
            
            if (isCodeFile) {
                // 代码文件：使用LSP提取结构化上下文
                if (project != null) {
                    try {
                        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
                        if (psiFile != null) {
                            // 使用LSP提取器获取结构化上下文
                            val structuredContext = lspContextExtractor?.extractStructureContext(psiFile)
                            if (structuredContext != null) {
                                builder.append(structuredContext)
                                builder.append("\n")
                                return // 已处理，直接返回
                            }
                        }
                    } catch (e: Exception) {
                        // 如果LSP提取失败，回退到原始处理方式
                        builder.append("${indent}// LSP提取失败，使用原始内容\n")
                    }
                }
            }
            
            // 如果不是代码文件或LSP提取失败，按行数处理
            if (lines.size > 200) {
                // 大文件：按200行分片处理
                val maxLinesPerChunk = 200
                
                for (i in lines.indices step maxLinesPerChunk) {
                    val chunkLines = lines.subList(i, minOf(i + maxLinesPerChunk, lines.size))
                    val chunkContent = chunkLines.joinToString("\n")
                    
                    builder.append("${indent}// --- Chunk starting at line ${i + 1} ---\n")
                    for (line in chunkLines) {
                        // 过滤掉空行和纯注释行（简单过滤）
                        if (!line.isBlank()) {
                            val trimmedLine = line.trim()
                            // 简单过滤一些常见的无意义注释行
                            if (!trimmedLine.startsWith("//") || 
                                (trimmedLine.startsWith("//") && trimmedLine.length > 5)) {
                                builder.append("$indent$line\n")
                            }
                        }
                    }
                    builder.append("${indent}// --- End of chunk ---\n\n")
                }
            } else {
                // 小文件：直接处理
                var lineCount = 0
                val maxLines = 800 // 限制每个文件的最大行数
                
                for (line in lines) {
                    if (lineCount >= maxLines) {
                        break
                    }
                    
                    // 过滤掉空行和纯注释行（简单过滤）
                    if (!line.isBlank()) {
                        val trimmedLine = line.trim()
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
            }
            
            builder.append("\n")
        } catch (e: Exception) {
            builder.append("${indent}// 读取文件失败: ${file.name} - ${e.message}\n\n")
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ContextManager? = null
        
        fun getInstance(): ContextManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContextManager().also { INSTANCE = it }
            }
        }
        
        /**
         * 创建一个新的独立上下文管理器实例
         */
        fun createInstance(): ContextManager {
            return ContextManager()
        }
        
        /**
         * 创建一个新的独立上下文管理器实例，带项目参数
         */
        fun createInstance(project: Project): ContextManager {
            return ContextManager(project)
        }
    }
}