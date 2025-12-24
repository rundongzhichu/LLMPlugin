package org.demo.llmplugin.util

import com.intellij.openapi.vfs.VirtualFile

/**
 * 上下文资源类，用于表示单个文件或资源的上下文信息
 * 包含文件的基本信息、内容以及元数据
 */
data class ContextResource(
    val uri: String,           // 资源的URI，通常是文件路径
    val name: String,          // 资源名称
    val kind: String,          // 资源类型 (file, directory, code等)
    val description: String,   // 资源描述
    val content: String?,      // 资源内容（可选，对于大文件或二进制文件可能为空）
    val metadata: Map<String, Any> = mapOf() // 附加元数据
) {
    companion object {
        /**
         * 从虚拟文件创建上下文资源
         */
        fun fromVirtualFile(virtualFile: VirtualFile): ContextResource {
            val metadata = mutableMapOf(
                "path" to virtualFile.path,
                "size" to virtualFile.length,
                "extension" to (virtualFile.extension ?: ""),
                "isDirectory" to virtualFile.isDirectory,
                "type" to if (isCodeFile(virtualFile)) "code" else "non-code"
            )
            
            if (!virtualFile.isDirectory) {
                metadata["lineCount"] = getLineCount(virtualFile)
            }
            
            return ContextResource(
                uri = "file://${virtualFile.path}",
                name = virtualFile.name,
                kind = if (virtualFile.isDirectory) "directory" else "file",
                description = if (isCodeFile(virtualFile)) {
                    "Code file in project: ${virtualFile.path}"
                } else {
                    "Non-code file in project: ${virtualFile.path}"
                },
                content = null, // 内容将在需要时按需加载
                metadata = metadata
            )
        }
        
        /**
         * 检查是否为代码文件
         */
        fun isCodeFile(file: VirtualFile): Boolean {
            val codeExtensions = setOf(
                "java", "kt", "kts", "scala", "groovy", "js", "ts", "jsx", "tsx",
                "py", "rb", "php", "go", "rs", "cpp", "c", "h", "hpp", "swift",
                "dart", "cs", "vb", "fs", "ml", "mli", "sql", "xml", "html", "css",
                "json", "yaml", "yml", "toml", "md", "txt"
            )
            return file.extension?.lowercase() in codeExtensions
        }
        
        /**
         * 获取文件行数
         */
        private fun getLineCount(virtualFile: VirtualFile): Int {
            return try {
                val content = virtualFile.contentsToByteArray().toString(kotlin.text.Charsets.UTF_8)
                content.count { it == '\n' } + 1
            } catch (e: Exception) {
                0
            }
        }
    }
    
    /**
     * 创建一个副本，更新内容
     */
    fun withContent(content: String?): ContextResource {
        return copy(content = content)
    }
}