package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 *
 * todo 后续考虑支持MCP服务协议 减少token的传输 让大模型自己决定是否要获取增量上下文
 *
 *
 * 上下文管理工具类
 * 负责管理AI生成代码时所需的上下文信息
 */
class ContextManager {
    private val contextFiles = mutableSetOf<VirtualFile>()
    
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
    }
}