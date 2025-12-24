package org.demo.llmplugin.util

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import org.demo.llmplugin.mcp.MCPContextManager
import org.demo.llmplugin.mcp.ContextResource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 统一上下文管理器
 * 所有上下文资源都统一添加到MCP上下文管理器中
 */
class ContextManager(private val project: Project) {
    private val mcpContextManager = MCPContextManager(project)
    
    // 通过MCP上下文管理器管理所有上下文资源
    fun addFileToContext(file: VirtualFile): Boolean {
        val resource = createResourceFromVirtualFile(file)
        return mcpContextManager.addResource(resource)
    }
    
    fun addFilesToContext(files: Array<VirtualFile>): Int {
        var addedCount = 0
        for (file in files) {
            val resource = createResourceFromVirtualFile(file)
            if (mcpContextManager.addResource(resource)) {
                addedCount++
            }
        }
        return addedCount
    }
    
    fun removeFileFromContext(file: VirtualFile) {
        mcpContextManager.removeResource("file://${file.path}")
    }
    
    fun clearContext() {
        mcpContextManager.clearAllResources()
    }
    
    fun getContextFiles(): Set<VirtualFile> {
        // 从MCP上下文管理器中获取资源并转换为VirtualFile
        return mcpContextManager.getContextResources()
            .mapNotNull { resource ->
                val path = resource.uri.removePrefix("file://").substringBefore("#")
                com.intellij.openapi.vfs.VfsUtil.findFile(java.nio.file.Paths.get(path), false)
            }
            .toSet()
    }
    
    fun isFileInContext(file: VirtualFile): Boolean {
        return mcpContextManager.listResources().resources.any { resource ->
            resource.uri.startsWith("file://${file.path}")
        }
    }
    
    fun getContextFilesCount(): Int {
        return getContextFiles().size
    }
    
    fun buildCompressedContextCode(): String {
        val contextBuilder = StringBuilder()
        
        // 从MCP上下文管理器获取资源并构建压缩上下文
        val resources = mcpContextManager.getContextResources()
        for (resource in resources) {
            if (resource.uri.startsWith("file://")) {
                val path = resource.uri.removePrefix("file://").substringBefore("#")
                val fileName = java.io.File(path).name
                contextBuilder.append("[文件: $fileName]\n")
                
                if (resource.content != null) {
                    contextBuilder.append(resource.content).append("\n\n")
                } else {
                    // 如果内容为空，尝试从虚拟文件读取
                    val virtualFile = com.intellij.openapi.vfs.VfsUtil.findFile(java.nio.file.Paths.get(path), false)
                    if (virtualFile != null) {
                        try {
                            val inputStream = virtualFile.inputStream
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
                                        contextBuilder.append("$trimmedLine\n")
                                        lineCount++
                                    }
                                }
                            }
                            
                            // 如果文件内容超过限制，添加提示
                            if (lineCount >= maxLines) {
                                contextBuilder.append("// ... 文件内容已截断 ...\n")
                            }
                            
                            reader.close()
                        } catch (e: Exception) {
                            contextBuilder.append("// 读取文件失败: $fileName - ${e.message}\n")
                        }
                    }
                }
                contextBuilder.append("\n")
            }
        }
        
        return contextBuilder.toString()
    }
    
    fun showFileChooser(): Array<VirtualFile> {
        // 创建文件选择描述符，允许选择文件和目录
        val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
            .withTitle("选择上下文文件或目录")
            .withDescription("选择要作为上下文包含的文件或目录")
        
        // 显示文件选择对话框
        return FileChooser.chooseFiles(descriptor, project, null)
    }
    
    // MCP上下文管理方法
    fun addStructureContextFromPsiFile(psiFile: PsiFile): Int {
        val virtualFile = psiFile.virtualFile ?: return 0
        
        // 获取代码结构上下文
        val lspIntegration = org.demo.llmplugin.lsp.LSPIntegration(project)
        val structureContext = lspIntegration.getCodeStructureContext(psiFile)
        
        var addedCount = 0
        
        // 为每个类创建资源
        for (classInfo in structureContext.classes) {
            val resource = ContextResource(
                uri = "file://${virtualFile.path}#class:${classInfo.name}",
                name = "Class: ${classInfo.name}",
                kind = "class-definition",
                description = "Class ${classInfo.name} in ${virtualFile.name}",
                content = getClassContent(psiFile, classInfo),
                metadata = mapOf(
                    "fileName" to virtualFile.name,
                    "filePath" to virtualFile.path,
                    "elementType" to "class",
                    "className" to classInfo.name,
                    "startLine" to classInfo.startLine,
                    "endLine" to classInfo.endLine
                )
            )
            if (mcpContextManager.addResource(resource)) {
                addedCount++
            }
        }
        
        // 为每个方法创建资源
        for (methodInfo in structureContext.methods) {
            val resource = ContextResource(
                uri = "file://${virtualFile.path}#method:${methodInfo.name}",
                name = "Method: ${methodInfo.name}",
                kind = "method-definition",
                description = "Method ${methodInfo.name} in ${virtualFile.name}",
                content = getMethodContent(psiFile, methodInfo),
                metadata = mapOf(
                    "fileName" to virtualFile.name,
                    "filePath" to virtualFile.path,
                    "elementType" to "method",
                    "methodName" to methodInfo.name,
                    "startLine" to methodInfo.startLine,
                    "endLine" to methodInfo.endLine
                )
            )
            if (mcpContextManager.addResource(resource)) {
                addedCount++
            }
        }
        
        return addedCount
    }
    
    fun addSyntaxContext(psiFile: PsiFile, offset: Int): Int {
        val virtualFile = psiFile.virtualFile ?: return 0
        
        // 获取语法上下文
        val lspIntegration = org.demo.llmplugin.lsp.LSPIntegration(project)
        val syntaxContext = lspIntegration.getSyntaxContext(psiFile, offset)
        
        // 为当前语法上下文创建资源
        val contextDescription = syntaxContext.currentContext.joinToString(" -> ")
        val resource = ContextResource(
            uri = "file://${virtualFile.path}#context:${offset}",
            name = "Syntax Context at Offset $offset",
            kind = "syntax-context",
            description = "Current syntax context: $contextDescription",
            content = buildSyntaxContextContent(syntaxContext),
            metadata = mapOf(
                "fileName" to virtualFile.name,
                "filePath" to virtualFile.path,
                "offset" to offset,
                "contextPath" to syntaxContext.currentContext
            )
        )
        
        return if (mcpContextManager.addResource(resource)) 1 else 0
    }
    
    fun addContextFromEditor(editor: Editor): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        
        // 获取编辑器选择的代码上下文
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val selectedText = selectionModel.selectedText ?: return emptyList()
            val caretModel = editor.caretModel
            val psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            
            psiFile?.virtualFile?.let { virtualFile ->
                val document = psiFile.viewProvider.document
                if (document != null) {
                    val startLine = document.getLineNumber(selectionModel.selectionStart)
                    val endLine = document.getLineNumber(selectionModel.selectionEnd)
                    
                    val resource = ContextResource(
                        uri = "file://${virtualFile.path}#L${startLine + 1}-${endLine + 1}",
                        name = "${virtualFile.name}:${startLine + 1}-${endLine + 1}",
                        kind = "code-selection",
                        description = "Selected code from line ${startLine + 1} to ${endLine + 1}",
                        content = selectedText,
                        metadata = mapOf(
                            "fileName" to virtualFile.name,
                            "filePath" to virtualFile.path,
                            "startLine" to startLine + 1,
                            "endLine" to endLine + 1,
                            "selectionLength" to selectedText.length
                        )
                    )
                    
                    if (mcpContextManager.addResource(resource)) {
                        resources.add(resource)
                    }
                }
            }
        }
        
        return resources
    }
    
    fun getMCPContextManager(): MCPContextManager {
        return mcpContextManager
    }
    
    fun clearMCPContext() {
        mcpContextManager.clearAllResources()
    }
    
    fun getMCPContextResourcesCount(): Int {
        return mcpContextManager.getContextResources().size
    }
    
    fun getCurrentUserSelectedContextResources(): List<ContextResource> {
        // 返回当前MCP上下文管理器中的所有资源，这些资源应该是用户明确选择的
        return mcpContextManager.getContextResources()
    }
    
    // 创建虚拟文件资源的辅助方法
    private fun createResourceFromVirtualFile(virtualFile: VirtualFile): ContextResource {
        val content = try {
            virtualFile.contentsToByteArray().toString(kotlin.text.Charsets.UTF_8)
        } catch (e: Exception) {
            "// Error reading file content: ${e.message}"
        }
        
        val metadata = mutableMapOf(
            "path" to virtualFile.path as Any,
            "size" to virtualFile.length as Any,
            "extension" to (virtualFile.extension ?: "") as Any,
            "isDirectory" to virtualFile.isDirectory as Any
        )
        
        // 如果是代码文件，添加代码结构信息
        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile != null) {
            val lspIntegration = org.demo.llmplugin.lsp.LSPIntegration(project)
            val structureContext = lspIntegration.getCodeStructureContext(psiFile)
            metadata["classCount"] = structureContext.classes.size as Any
            metadata["methodCount"] = structureContext.methods.size as Any
            metadata["type"] = "source-code" as Any
        }
        
        return ContextResource(
            uri = "file://${virtualFile.path}",
            name = virtualFile.name,
            kind = if (virtualFile.isDirectory) "directory" else "file",
            description = "File in project: ${virtualFile.path}",
            content = content,
            metadata = metadata
        )
    }
    
    // 获取类的内容
    private fun getClassContent(psiFile: PsiFile, classInfo: org.demo.llmplugin.lsp.ClassInfo): String {
        val document = psiFile.viewProvider.document ?: return ""
        val startOffset = document.getLineStartOffset(classInfo.startLine)
        val endLine = if (classInfo.endLine < document.lineCount) classInfo.endLine else document.lineCount - 1
        val endOffset = document.getLineEndOffset(endLine)
        
        return try {
            document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
        } catch (e: Exception) {
            "// Error reading class content: ${e.message}"
        }
    }
    
    // 获取方法的内容
    private fun getMethodContent(psiFile: PsiFile, methodInfo: org.demo.llmplugin.lsp.MethodInfo): String {
        val document = psiFile.viewProvider.document ?: return ""
        val startOffset = document.getLineStartOffset(methodInfo.startLine)
        val endLine = if (methodInfo.endLine < document.lineCount) methodInfo.endLine else document.lineCount - 1
        val endOffset = document.getLineEndOffset(endLine)
        
        return try {
            document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
        } catch (e: Exception) {
            "// Error reading method content: ${e.message}"
        }
    }
    
    // 构建语法上下文内容
    private fun buildSyntaxContextContent(syntaxContext: org.demo.llmplugin.lsp.SyntaxContext): String {
        return """
            File: ${syntaxContext.fileName}
            Path: ${syntaxContext.filePath}
            Current Context: ${syntaxContext.currentContext.joinToString(" -> ")}
        """.trimIndent()
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