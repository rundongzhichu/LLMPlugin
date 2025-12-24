package org.demo.llmplugin.lsp

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiField
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiElementVisitor

/**
 * LSP上下文提取器
 * 用于从代码文件中提取结构化上下文信息
 */
class LSPContextExtractor(private val project: Project) {
    
    /**
     * 从PSI文件中提取结构化上下文
     */
    fun extractStructureContext(psiFile: PsiFile): String {
        val contextBuilder = StringBuilder()
        
        // 提取包声明
        val packageStatement = PsiTreeUtil.findChildOfType(psiFile, PsiPackageStatement::class.java)
        if (packageStatement != null) {
            contextBuilder.append("package: ${packageStatement.packageReference.text}\n")
        }
        
        // 提取导入语句
        val importList = PsiTreeUtil.findChildOfType(psiFile, PsiImportList::class.java)
        if (importList != null) {
            val imports = importList.allImportStatements
            if (imports.isNotEmpty()) {
                contextBuilder.append("\nimports:\n")
                for (import in imports) {
                    contextBuilder.append("  ${import.text}\n")
                }
            }
        }
        
        // 提取顶级元素（类、接口、枚举等）
        val topLevelElements = psiFile.children.filter { child ->
            child is PsiClass || isTopLevelFunction(child) || isTopLevelVariable(child)
        }
        
        contextBuilder.append("\ntop-level elements:\n")
        for (element in topLevelElements) {
            if (element is PsiClass) {
                contextBuilder.append(extractClassContext(element, 1))
            } else if (isTopLevelFunction(element)) {
                contextBuilder.append("  function: ${element.text.substringBefore('{').trim()}\n")
            } else if (isTopLevelVariable(element)) {
                contextBuilder.append("  variable: ${element.text.substringBefore(';').trim()}\n")
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 提取类的结构化上下文
     */
    private fun extractClassContext(psiClass: PsiClass, depth: Int): String {
        val indent = "  ".repeat(depth)
        val contextBuilder = StringBuilder()
        
        // 类的基本信息
        val modifiers = psiClass.modifierList?.text ?: ""
        val className = psiClass.name ?: "anonymous"
        val extendsList = psiClass.extendsList?.text ?: ""
        val implementsList = psiClass.implementsList?.text ?: ""
        
        contextBuilder.append("${indent}class: $modifiers $className")
        if (extendsList.isNotEmpty()) {
            contextBuilder.append(" extends $extendsList")
        }
        if (implementsList.isNotEmpty()) {
            contextBuilder.append(" implements $implementsList")
        }
        contextBuilder.append("\n")
        
        // 提取类的成员
        val fields = psiClass.fields
        val methods = psiClass.methods
        val innerClasses = psiClass.innerClasses
        
        // 提取字段
        if (fields.isNotEmpty()) {
            contextBuilder.append("${indent}  fields:\n")
            for (field in fields) {
                val fieldModifiers = field.modifierList?.text ?: ""
                val fieldName = field.name ?: "unnamed"
                val fieldType = field.type.presentableText
                contextBuilder.append("${indent}    field: $fieldModifiers $fieldType $fieldName\n")
            }
        }
        
        // 提取方法
        if (methods.isNotEmpty()) {
            contextBuilder.append("${indent}  methods:\n")
            for (method in methods) {
                val methodModifiers = method.modifierList?.text ?: ""
                val methodName = method.name ?: "unnamed"
                val methodSignature = buildMethodSignature(method)
                contextBuilder.append("${indent}    method: $methodModifiers $methodSignature\n")
            }
        }
        
        // 提取内部类
        if (innerClasses.isNotEmpty()) {
            contextBuilder.append("${indent}  inner classes:\n")
            for (innerClass in innerClasses) {
                contextBuilder.append(extractClassContext(innerClass, depth + 2))
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 构建方法签名
     */
    private fun buildMethodSignature(method: PsiMethod): String {
        val methodName = method.name
        val parameterList = method.parameterList
        val parameters = mutableListOf<String>()
        
        for (param in parameterList.parameters) {
            val paramName = param.name
            val paramType = param.type.presentableText
            parameters.add("$paramType $paramName")
        }
        
        val returnType = method.returnType?.presentableText ?: "void"
        val paramStr = parameters.joinToString(", ", "(", ")")
        
        return "$returnType $methodName$paramStr"
    }
    
    /**
     * 检查元素是否为顶级函数（适用于Kotlin等支持顶级函数的语言）
     */
    private fun isTopLevelFunction(element: PsiElement): Boolean {
        // 简化实现，可以根据具体语言特性扩展
        return false
    }
    
    /**
     * 检查元素是否为顶级变量（适用于Kotlin等支持顶级变量的语言）
     */
    private fun isTopLevelVariable(element: PsiElement): Boolean {
        // 简化实现，可以根据具体语言特性扩展
        return false
    }
    
    /**
     * 提取语法上下文（如特定行附近的代码）
     */
    fun extractSyntaxContext(psiFile: PsiFile, startLine: Int, endLine: Int = startLine + 10): String {
        val document = psiFile.viewProvider.document
        if (document == null) {
            return psiFile.text
        }
        
        val text = psiFile.text
        val lines = text.split("\n")
        
        val start = if (startLine < 0) 0 else startLine
        val end = if (endLine >= lines.size) lines.size - 1 else endLine
        
        val contextBuilder = StringBuilder()
        for (i in start until end) {
            if (i < lines.size) {
                contextBuilder.append("${i + 1}: ${lines[i]}\n")
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 提取代码文件的分片上下文（用于大文件）
     */
    fun extractChunkedStructureContext(psiFile: PsiFile, maxLinesPerChunk: Int = 200): List<String> {
        val chunks = mutableListOf<String>()
        val fullText = psiFile.text
        val lines = fullText.split("\n")
        
        if (lines.size <= maxLinesPerChunk) {
            // 文件不大，直接返回完整结构化上下文
            chunks.add(extractStructureContext(psiFile))
            return chunks
        }
        
        // 对于大文件，按行数分片处理
        for (i in lines.indices step maxLinesPerChunk) {
            val chunkLines = lines.subList(i, minOf(i + maxLinesPerChunk, lines.size))
            val chunkText = chunkLines.joinToString("\n")
            
            // 为每个分片创建一个虚拟的PSI文件用于分析
            val virtualPsiFile = com.intellij.psi.PsiFileFactory.getInstance(psiFile.project)
                .createFileFromText(psiFile.name, psiFile.fileType, chunkText)
            
            // 为每个分片添加描述
            val chunkIndex = (i / maxLinesPerChunk) + 1
            val totalChunks = kotlin.math.ceil(lines.size.toDouble() / maxLinesPerChunk).toInt()
            val chunkContext = "// Chunk ${chunkIndex}/${totalChunks} of ${psiFile.name} (lines ${i + 1}-${minOf(i + maxLinesPerChunk, lines.size)})\n" +
                             extractStructureContext(virtualPsiFile)
            
            chunks.add(chunkContext)
        }
        
        return chunks
    }
}