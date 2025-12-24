package org.demo.llmplugin.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.demo.llmplugin.util.ContextResource

/**
 * LSP上下文提取器
 * 从IDE中提取代码上下文信息
 */
class LSPContextExtractor {
    
    /**
     * 从PSI文件提取上下文信息
     */
    fun extractContextFromPsiFile(psiFile: PsiFile): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        val virtualFile = psiFile.virtualFile ?: return emptyList()
        
        // 获取LSP集成来提取代码结构
        val lspIntegration = LSPIntegration(psiFile.project)
        val structureContext = lspIntegration.getCodeStructureContext(psiFile)
        
        // 为每个类创建资源
        structureContext.classes.forEach { classInfo ->
            val resource = ContextResource(
                uri = "file://${virtualFile.path}#class:${classInfo.name}",
                name = "Class: ${classInfo.name}",
                kind = "class-definition",
                description = "Class ${classInfo.name} in ${psiFile.name}",
                content = getClassContent(psiFile, classInfo),
                metadata = mapOf(
                    "fileName" to psiFile.name,
                    "filePath" to virtualFile.path,
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
                uri = "file://${virtualFile.path}#method:${methodInfo.name}",
                name = "Method: ${methodInfo.name}",
                kind = "method-definition",
                description = "Method ${methodInfo.name} in ${psiFile.name}",
                content = getMethodContent(psiFile, methodInfo),
                metadata = mapOf(
                    "fileName" to psiFile.name,
                    "filePath" to virtualFile.path,
                    "elementType" to "method",
                    "methodName" to methodInfo.name,
                    "startLine" to methodInfo.startLine,
                    "endLine" to methodInfo.endLine
                )
            )
            resources.add(resource)
        }
        
        // 为每个字段创建资源
        structureContext.fields.forEach { fieldInfo ->
            val resource = ContextResource(
                uri = "file://${virtualFile.path}#field:${fieldInfo.name}",
                name = "Field: ${fieldInfo.name}",
                kind = "field-definition",
                description = "Field ${fieldInfo.name} in ${psiFile.name}",
                content = getFieldContent(psiFile, fieldInfo),
                metadata = mapOf(
                    "fileName" to psiFile.name,
                    "filePath" to virtualFile.path,
                    "elementType" to "field",
                    "fieldName" to fieldInfo.name,
                    "line" to fieldInfo.startLine
                )
            )
            resources.add(resource)
        }
        
        return resources
    }
    
    /**
     * 提取语法上下文
     */
    fun extractSyntaxContext(psiFile: PsiFile, offset: Int): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        val virtualFile = psiFile.virtualFile ?: return emptyList()
        
        // 获取LSP集成来提取语法上下文
        val lspIntegration = LSPIntegration(psiFile.project)
        val syntaxContext = lspIntegration.getSyntaxContext(psiFile, offset)
        
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
        
        resources.add(resource)
        return resources
    }
    
    /**
     * 从代码上下文创建MCP资源
     */
    fun createResourceFromCodeContext(codeContext: CodeContext, virtualFile: VirtualFile): ContextResource {
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
            "// Error getting class content: ${e.message}"
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
            "// Error getting method content: ${e.message}"
        }
    }
    
    /**
     * 获取字段的内容
     */
    private fun getFieldContent(psiFile: PsiFile, fieldInfo: FieldInfo): String {
        val document = psiFile.viewProvider.document ?: return ""
        val lineStart = document.getLineStartOffset(fieldInfo.startLine)
        val lineEnd = document.getLineEndOffset(fieldInfo.startLine)
        
        return try {
            document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
        } catch (e: Exception) {
            "// Error getting field content: ${e.message}"
        }
    }
}