package org.demo.llmplugin.lsp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.demo.llmplugin.mcp.ContextResource
import java.nio.file.Paths

/**
 * LSP上下文提取器
 * 使用LSP服务精确定位代码上下文并将其转换为MCP资源
 */
class LSPContextExtractor(private val project: Project) {
    private val lspIntegration = LSPIntegration(project)
    
    /**
     * 从编辑器中提取代码上下文并转换为MCP资源
     */
    fun extractContextFromEditor(editor: Editor): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        
        // 获取当前选中的代码上下文
        val codeContext = lspIntegration.getSelectedCodeContext(editor)
        if (codeContext != null) {
            val virtualFile = VfsUtil.findFile(
                 Paths.get(codeContext.fullFilePath),
                false
            )
            if (virtualFile != null) {
                val resource = createResourceFromCodeContext(codeContext, virtualFile)
                resources.add(resource)
            }
        }
        
        return resources
    }
    
    /**
     * 从PSI文件中提取代码结构上下文并转换为MCP资源
     */
    fun extractStructureContextFromPsiFile(psiFile: PsiFile): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        
        // 获取代码结构上下文
        val structureContext = lspIntegration.getCodeStructureContext(psiFile)
        
        // 为每个类创建资源
        structureContext.classes.forEach { classInfo ->
            val resource = ContextResource(
                uri = "file://${psiFile.virtualFile.path}#class:${classInfo.name}",
                name = "Class: ${classInfo.name}",
                kind = "class-definition",
                description = "Class ${classInfo.name} in ${psiFile.name}",
                content = getClassContent(psiFile, classInfo),
                metadata = mapOf(
                    "fileName" to psiFile.name,
                    "filePath" to psiFile.virtualFile.path,
                    "elementType" to "class",
                    "className" to classInfo.name,
                    "startLine" to classInfo.startLine,
                    "endLine" to classInfo.endLine
                )
            )
            resources.add(resource)
        }
        
        // 为每个方法创建资源
        structureContext.methods.forEach { methodInfo ->
            val resource = ContextResource(
                uri = "file://${psiFile.virtualFile.path}#method:${methodInfo.name}",
                name = "Method: ${methodInfo.name}",
                kind = "method-definition",
                description = "Method ${methodInfo.name} in ${psiFile.name}",
                content = getMethodContent(psiFile, methodInfo),
                metadata = mapOf(
                    "fileName" to psiFile.name,
                    "filePath" to psiFile.virtualFile.path,
                    "elementType" to "method",
                    "methodName" to methodInfo.name,
                    "startLine" to methodInfo.startLine,
                    "endLine" to methodInfo.endLine
                )
            )
            resources.add(resource)
        }
        
        return resources
    }
    
    /**
     * 从语法上下文提取MCP资源
     */
    fun extractSyntaxContext(psiFile: PsiFile, offset: Int): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        
        val syntaxContext = lspIntegration.getSyntaxContext(psiFile, offset)
        
        // 为当前语法上下文创建资源
        val contextDescription = syntaxContext.currentContext.joinToString(" -> ")
        val resource = ContextResource(
            uri = "file://${psiFile.virtualFile.path}#context:${offset}",
            name = "Syntax Context at Offset $offset",
            kind = "syntax-context",
            description = "Current syntax context: $contextDescription",
            content = buildSyntaxContextContent(syntaxContext),
            metadata = mapOf(
                "fileName" to psiFile.name,
                "filePath" to psiFile.virtualFile.path,
                "offset" to offset,
                "contextPath" to syntaxContext.currentContext
            )
        )
        resources.add(resource)
        
        return resources
    }
    
    /**
     * 创建从代码上下文到MCP资源的转换
     */
    private fun createResourceFromCodeContext(codeContext: CodeContext, virtualFile: VirtualFile): ContextResource {
        return ContextResource(
            uri = "file://${codeContext.fullFilePath}#L${codeContext.startLine}-${codeContext.endLine}",
            name = "${virtualFile.name}:${codeContext.startLine}-${codeContext.endLine}",
            kind = "code-selection",
            description = "Selected code from line ${codeContext.startLine} to ${codeContext.endLine}",
            content = codeContext.selectedText,
            metadata = mapOf(
                "fileName" to codeContext.fileName,
                "filePath" to codeContext.filePath,
                "startLine" to codeContext.startLine,
                "endLine" to codeContext.endLine,
                "selectionLength" to codeContext.selectedText.length
            )
        )
    }
    
    /**
     * 获取类的内容
     */
    private fun getClassContent(psiFile: PsiFile, classInfo: ClassInfo): String {
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
    
    /**
     * 获取方法的内容
     */
    private fun getMethodContent(psiFile: PsiFile, methodInfo: MethodInfo): String {
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
    
    /**
     * 构建语法上下文内容
     */
    private fun buildSyntaxContextContent(syntaxContext: SyntaxContext): String {
        return """
            File: ${syntaxContext.fileName}
            Path: ${syntaxContext.filePath}
            Current Context: ${syntaxContext.currentContext.joinToString(" -> ")}
        """.trimIndent()
    }
    
    /**
     * 从虚拟文件创建MCP资源
     */
    fun createResourceFromVirtualFile(virtualFile: VirtualFile): ContextResource {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        val content = try {
            virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            "// Error reading file content: ${e.message}"
        }
        
        val metadata = mutableMapOf(
            "path" to virtualFile.path as Any,
            "size" to virtualFile.length as Any,
            "extension" to (virtualFile.extension ?: "") as Any,
            "isDirectory" to virtualFile.isDirectory as Any
        )
        
        if (psiFile != null) {
            // 如果是代码文件，添加代码结构信息
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
}