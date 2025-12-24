package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.runBlocking
import org.demo.llmplugin.ui.ExplanationDialog
import org.demo.llmplugin.util.ContextManager
import org.demo.llmplugin.util.ContextUtils
import org.demo.llmplugin.util.ChatMessage
import org.demo.llmplugin.util.HttpUtils

class ExplainCodeAction : AnAction("Explain Selected Code") {

    private var isStream = false
    override fun update(e: AnActionEvent) {
        val hasSelection = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        
        // 使用新的上下文管理器
        val contextManager = ContextManager.createInstance(project)
        
        // 使用工具类获取完整的上下文资源
        val contextResources = ContextUtils.getContextResourcesFromEditor(contextManager, editor, psiFile)

        // 创建解释对话框
        val dialog = ExplanationDialog(project)
        dialog.show()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val contextCode = contextManager.buildCompressedContextCode()
                val prompt = """
                    Please explain the following code:
                    
                    $selectedText
         
                    
                    Context resources:
                    $contextCode
                """.trimIndent()
                
                // 使用HttpUtils直接调用LLM，遵循大模型交互规范
                val messages = listOf(
                    ChatMessage("system", "You are an expert code assistant. Provide clear and concise explanations of the code functionality, structure, and purpose."),
                    ChatMessage("user", prompt)
                )
                
                val response = runBlocking {
                    HttpUtils.callLocalLlm(messages) { chunk ->
                        isStream = true
                        // 流式接收数据块并在UI上逐个显示
                        ApplicationManager.getApplication().invokeLater {
                            dialog.showContent()
                            // 追加新的内容块
                            dialog.appendMessage(chunk)
                        }
                    }
                }
                
                // 显示解释结果
                ApplicationManager.getApplication().invokeLater {
                    if (!isStream) {
                        dialog.showContent()
                        // 显示完整响应
                        dialog.appendMessage("$response\n\n")
                    }
                }
            } catch (e: Exception) {
                // 显示错误信息
                ApplicationManager.getApplication().invokeLater {
                    dialog.appendMessage("Failed to explain code: ${e.message}")
                }
            } finally {
                isStream = false
            }
        }
    }
}