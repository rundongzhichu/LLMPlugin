package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.demo.llmplugin.lsp.LSPContextExtractor

/**
 * 上下文处理器
 * 根据文件类型处理内容：代码文件提取结构化信息，非代码文件过滤非必要信息
 */
class ContextProcessor(private val project: Project) {
    private val lspContextExtractor = LSPContextExtractor(project)
    
    /**
     * 过滤非代码文件内容，去除非必要信息
     */
    fun filterNonCodeFileContent(file: VirtualFile, content: String): String {
        return when {
            isBinaryFile(file) -> {
                "// Binary file: ${file.name} (${file.length} bytes)\n// Content not shown for binary file"
            }
            isLargeFile(file) -> {
                val lines = content.lineSequence().toList()
                val header = lines.take(50).joinToString("\n") // 取前50行
                val footer = lines.takeLast(20).joinToString("\n") // 取后20行
                """
                // File: ${file.name} (truncated - showing first 50 and last 20 lines of ${lines.size} total)
                $header
                // ... [${lines.size - 70} lines omitted] ...
                $footer
                """.trimIndent()
            }
            else -> {
                // 对于普通非代码文件，移除过多的空白行
                removeExcessiveBlankLines(content)
            }
        }
    }
    
    /**
     * 检查是否为二进制文件
     */
    private fun isBinaryFile(file: VirtualFile): Boolean {
        // 根据扩展名判断
        val binaryExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "svg", "webp", // 图片
            "mp3", "wav", "flac", "aac", "ogg", "m4a", // 音频
            "mp4", "avi", "mov", "wmv", "flv", "webm", // 视频
            "zip", "rar", "7z", "tar", "gz", "jar", "war", "exe", "dll", "so", // 压缩/可执行文件
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", // 办公文档
            "class", "o", "obj", "bin" // 编译文件
        )
        
        return file.extension?.lowercase() in binaryExtensions
    }
    
    /**
     * 检查是否为大文件
     */
    private fun isLargeFile(file: VirtualFile): Boolean {
        // 文件大于100KB则视为大文件
        return file.length > 100 * 1024
    }
    
    /**
     * 移除过多的空白行
     */
    private fun removeExcessiveBlankLines(content: String): String {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var blankLineCount = 0
        
        for (line in lines) {
            if (line.isBlank()) {
                blankLineCount++
                // 只保留最多2个连续的空白行
                if (blankLineCount <= 2) {
                    result.add(line)
                }
            } else {
                blankLineCount = 0
                result.add(line)
            }
        }
        
        return result.joinToString("\n")
    }
    
    /**
     * 处理大文件（200行做分片）
     */
    fun processLargeFileContent(file: VirtualFile, content: String): String {
        val lines = content.lineSequence().toList()
        val maxLinesPerChunk = 200
        val totalLines = lines.size
        
        if (totalLines <= maxLinesPerChunk) {
            // 文件不大，直接处理
            return if (ContextResource.isCodeFile(file)) {
                // 代码文件使用LSP提取结构化信息
                extractStructuredContext(file, content)
            } else {
                // 非代码文件过滤内容
                filterNonCodeFileContent(file, content)
            }
        }
        
        // 大文件分片处理
        val chunks = mutableListOf<String>()
        var chunkIndex = 0
        
        for (i in lines.indices step maxLinesPerChunk) {
            val chunkLines = lines.subList(i, minOf(i + maxLinesPerChunk, totalLines))
            val chunkContent = chunkLines.joinToString("\n")
            
            if (ContextResource.isCodeFile(file)) {
                // 对于代码文件，每个分片单独提取结构化信息
                chunks.add("// Chunk ${++chunkIndex} of ${file.name}\n$chunkContent")
            } else {
                chunks.add("// Chunk ${++chunkIndex} of ${file.name}\n$chunkContent")
            }
        }
        
        return chunks.joinToString("\n// ... chunk separator ...\n")
    }
    
    /**
     * 提取代码文件的结构化上下文
     */
    private fun extractStructuredContext(file: VirtualFile, content: String): String {
        try {
            // 从VirtualFile获取PSI文件
            val psiFile = com.intellij.psi.PsiManager.getInstance(project)
                .findFile(file) ?: return content // 如果无法获取PSI文件，返回原始内容
            
            // 使用LSP提取器获取结构化上下文
            return lspContextExtractor.extractStructureContext(psiFile)
        } catch (e: Exception) {
            // 如果LSP提取失败，返回原始内容
            return content
        }
    }
    
    /**
     * 创建虚拟文件的上下文资源（根据文件类型处理）
     */
    fun createResourceFromVirtualFile(virtualFile: VirtualFile): ContextResource {
        val content = if (ContextResource.isCodeFile(virtualFile)) {
            // 代码文件：使用LSP提取结构化信息
            try {
                val psiFile = com.intellij.psi.PsiManager.getInstance(project)
                    .findFile(virtualFile)
                
                if (psiFile != null) {
                    // 如果文件较大，先获取结构化信息，再根据需要获取具体内容
                    lspContextExtractor.extractStructureContext(psiFile)
                } else {
                    // 如果无法获取PSI文件，读取原始内容
                    val fullContent = virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
                    if (fullContent.lines().size > 200) {
                        processLargeFileContent(virtualFile, fullContent)
                    } else {
                        fullContent
                    }
                }
            } catch (e: Exception) {
                "// Error extracting structured context: ${e.message}"
            }
        } else {
            try {
                val fullContent = virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
                if (fullContent.lines().size > 200) {
                    processLargeFileContent(virtualFile, fullContent)
                } else {
                    filterNonCodeFileContent(virtualFile, fullContent)
                }
            } catch (e: Exception) {
                "// Error reading file content: ${e.message}"
            }
        }
        
        val metadata = mutableMapOf(
            "path" to virtualFile.path,
            "size" to virtualFile.length,
            "extension" to (virtualFile.extension ?: ""),
            "isDirectory" to virtualFile.isDirectory,
            "type" to if (ContextResource.isCodeFile(virtualFile)) "code" else "non-code"
        )
        
        if (!virtualFile.isDirectory) {
            metadata["lineCount"] = getLineCount(virtualFile)
        }
        
        return ContextResource(
            uri = "file://${virtualFile.path}",
            name = virtualFile.name,
            kind = if (virtualFile.isDirectory) "directory" else "file",
            description = if (ContextResource.isCodeFile(virtualFile)) {
                "Code file in project: ${virtualFile.path}"
            } else {
                "Non-code file in project: ${virtualFile.path}"
            },
            content = content,
            metadata = metadata
        )
    }
    
    /**
     * 获取文件行数
     */
    private fun getLineCount(virtualFile: VirtualFile): Int {
        return try {
            val content = virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
            content.count { it == '\n' } + 1
        } catch (e: Exception) {
            0
        }
    }
}