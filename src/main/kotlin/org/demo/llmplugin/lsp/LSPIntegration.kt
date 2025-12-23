package org.demo.llmplugin.lsp

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * LSP (Language Server Protocol) 集成类
 * 用于精确定位代码上下文
 */
class LSPIntegration(private val project: Project) {
    
    /**
     * 获取当前编辑器的选中代码范围
     */
    fun getSelectedCodeContext(editor: Editor): CodeContext? {
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val startOffset = selectionModel.selectionStart
            val endOffset = selectionModel.selectionEnd
            
            val startLine = editor.document.getLineNumber(startOffset)
            val endLine = editor.document.getLineNumber(endOffset)
            
            val selectedText = selectionModel.selectedText ?: return null
            
            val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            if (file != null) {
                return CodeContext(
                    fileName = file.name,
                    filePath = file.path,
                    startLine = startLine,
                    endLine = endLine,
                    selectedText = selectedText,
                    fullFilePath = file.path
                )
            }
        }
        return null
    }
    
    /**
     * 获取指定文件中的符号定义
     */
    fun getSymbolDefinitions(psiFile: PsiFile, line: Int, character: Int): List<SymbolInformation> {
        val symbols = mutableListOf<SymbolInformation>()
        
        // 使用PsiTreeUtil获取元素
        val element = psiFile.findElementAt(psiFile.viewProvider.document?.getLineStartOffset(line) ?: 0 + character)
        if (element != null) {
            // 查找相关的符号定义
            val parentClass = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiClass::class.java)
            if (parentClass != null) {
                val range = parentClass.textRange
                val start = psiFile.viewProvider.document?.getLineNumber(range.startOffset) ?: 0
                val end = psiFile.viewProvider.document?.getLineNumber(range.endOffset) ?: 0
                
                symbols.add(SymbolInformation(
                    name = parentClass.name ?: "unknown",
                    kind = "Class",
                    location = Location(
                        uri = psiFile.virtualFile.path,
                        range = Range(
                            startLine = start,
                            startCharacter = 0,
                            endLine = end,
                            endCharacter = 0
                        )
                    )
                ))
            }
            
            val parentMethod = PsiTreeUtil.getParentOfType(element, com.intellij.psi.PsiMethod::class.java)
            if (parentMethod != null) {
                val range = parentMethod.textRange
                val start = psiFile.viewProvider.document?.getLineNumber(range.startOffset) ?: 0
                val end = psiFile.viewProvider.document?.getLineNumber(range.endOffset) ?: 0
                
                symbols.add(SymbolInformation(
                    name = parentMethod.name,
                    kind = "Method",
                    location = Location(
                        uri = psiFile.virtualFile.path,
                        range = Range(
                            startLine = start,
                            startCharacter = 0,
                            endLine = end,
                            endCharacter = 0
                        )
                    )
                ))
            }
        }
        
        return symbols
    }
    
    /**
     * 获取代码的上下文信息，包括类、方法、变量等
     */
    fun getCodeStructureContext(psiFile: PsiFile): CodeStructureContext {
        val classes = PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiClass::class.java)
        val methods = PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiMethod::class.java)
        val fields = PsiTreeUtil.findChildrenOfType(psiFile, com.intellij.psi.PsiField::class.java)
        
        val classInfos = classes.map { psiClass ->
            ClassInfo(
                name = psiClass.name ?: "anonymous",
                startLine = psiFile.viewProvider.document?.getLineNumber(psiClass.textRange.startOffset) ?: 0,
                endLine = psiFile.viewProvider.document?.getLineNumber(psiClass.textRange.endOffset) ?: 0,
                methods = psiClass.methods.map { method ->
                    MethodInfo(
                        name = method.name,
                        startLine = psiFile.viewProvider.document?.getLineNumber(method.textRange.startOffset) ?: 0,
                        endLine = psiFile.viewProvider.document?.getLineNumber(method.textRange.endOffset) ?: 0
                    )
                },
                fields = psiClass.fields.map { field ->
                    FieldInfo(
                        name = field.name,
                        startLine = psiFile.viewProvider.document?.getLineNumber(field.textRange.startOffset) ?: 0
                    )
                }
            )
        }
        
        return CodeStructureContext(
            fileName = psiFile.name,
            classes = classInfos,
            methods = methods.map { method ->
                MethodInfo(
                    name = method.name,
                    startLine = psiFile.viewProvider.document?.getLineNumber(method.textRange.startOffset) ?: 0,
                    endLine = psiFile.viewProvider.document?.getLineNumber(method.textRange.endOffset) ?: 0
                )
            },
            fields = fields.map { field ->
                FieldInfo(
                    name = field.name,
                    startLine = psiFile.viewProvider.document?.getLineNumber(field.textRange.startOffset) ?: 0
                )
            }
        )
    }
    
    /**
     * 获取代码的语法上下文（如当前函数、类、命名空间等）
     */
    fun getSyntaxContext(psiFile: PsiFile, offset: Int): SyntaxContext {
        val element = psiFile.findElementAt(offset)
        val contextElements = mutableListOf<String>()
        
        if (element != null) {
            var currentElement: PsiElement? = element
            while (currentElement != null && currentElement != psiFile) {
                when (currentElement) {
                    is com.intellij.psi.PsiMethod -> contextElements.add(0, "method:${currentElement.name}")
                    is com.intellij.psi.PsiClass -> contextElements.add(0, "class:${currentElement.name}")
                    is com.intellij.psi.PsiField -> contextElements.add(0, "field:${currentElement.name}")
                    is com.intellij.psi.PsiParameter -> contextElements.add(0, "parameter:${currentElement.name}")
                }
                currentElement = currentElement.parent
            }
        }
        
        return SyntaxContext(
            currentContext = contextElements,
            fileName = psiFile.name,
            filePath = psiFile.virtualFile.path
        )
    }
    
    /**
     * 获取代码的引用信息
     */
    fun getReferences(psiFile: PsiFile, element: PsiElement): List<ReferenceLocation> {
        val references = mutableListOf<ReferenceLocation>()
        
        // 获取元素的定义位置
        val definitionOffset = element.textRange.startOffset
        val definitionLine = psiFile.viewProvider.document?.getLineNumber(definitionOffset) ?: 0
        val definitionChar = definitionOffset - (psiFile.viewProvider.document?.getLineStartOffset(definitionLine) ?: 0)
        
        // 在IDEA中获取引用通常需要使用Find Usages API
        // 这里简化实现，返回定义位置
        references.add(ReferenceLocation(
            uri = psiFile.virtualFile.path,
            range = Range(
                startLine = definitionLine,
                startCharacter = definitionChar,
                endLine = definitionLine,
                endCharacter = definitionChar + element.textLength
            ),
            isDefinition = true
        ))
        
        return references
    }
}

/**
 * 代码上下文数据类
 */
data class CodeContext(
    val fileName: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val selectedText: String,
    val fullFilePath: String
)

/**
 * 代码结构上下文
 */
data class CodeStructureContext(
    val fileName: String,
    val classes: List<ClassInfo>,
    val methods: List<MethodInfo>,
    val fields: List<FieldInfo>
)

data class ClassInfo(
    val name: String,
    val startLine: Int,
    val endLine: Int,
    val methods: List<MethodInfo> = emptyList(),
    val fields: List<FieldInfo> = emptyList()
)

data class MethodInfo(
    val name: String,
    val startLine: Int,
    val endLine: Int
)

data class FieldInfo(
    val name: String,
    val startLine: Int
)

/**
 * 语法上下文
 */
data class SyntaxContext(
    val currentContext: List<String>,
    val fileName: String,
    val filePath: String
)

/**
 * LSP Range 类
 */
data class Range(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int
)

/**
 * LSP Location 类
 */
data class Location(
    val uri: String,
    val range: Range
)

/**
 * LSP SymbolInformation 类
 */
data class SymbolInformation(
    val name: String,
    val kind: String,
    val location: Location
)

/**
 * 引用位置
 */
data class ReferenceLocation(
    val uri: String,
    val range: Range,
    val isDefinition: Boolean = false
)