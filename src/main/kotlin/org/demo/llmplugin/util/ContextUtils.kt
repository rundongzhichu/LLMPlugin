package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import org.demo.llmplugin.mcp.ContextResource

/**
 * 上下文管理工具类
 * 提供上下文管理的公共工具方法
 */
object ContextUtils {
    
    /**
     * 检查文件是否为代码文件
     */
    fun isCodeFile(file: VirtualFile): Boolean {
        val codeExtensions = setOf(
            "java", "kt", "kts", "scala", "groovy", "js", "ts", "py", 
            "cpp", "c", "h", "go", "rs", "php", "rb", "swift", "dart", "cs"
        )
        return file.extension in codeExtensions
    }
    
    /**
     * 从编辑器获取完整的上下文资源
     */
    fun getContextResourcesFromEditor(
        contextManager: ContextManager,
        editor: Editor,
        psiFile: PsiFile?
    ): List<ContextResource> {
        // 获取编辑器选择的上下文
        val contextResources = contextManager.addContextFromEditor(editor)
        
        // 如果有PSI文件，添加结构化和语法上下文
        psiFile?.let { psi ->
            // 添加结构化上下文（类、方法、字段信息）
            contextManager.addStructureContextFromPsiFile(psi)
            
            // 添加语法上下文（当前光标位置）
            val caretOffset = editor.caretModel.primaryCaret.offset
            contextManager.addSyntaxContext(psi, caretOffset)
            
            // 添加虚拟文件上下文
            psi.virtualFile?.let { virtualFile ->
                contextManager.addFileToContext(virtualFile)
            }
        }
        
        return contextResources
    }
}