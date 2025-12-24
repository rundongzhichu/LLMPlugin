package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.runBlocking
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.ui.ExplanationDialog
import org.demo.llmplugin.util.ContextManager
import org.demo.llmplugin.util.ContextUtils

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
        
        // 使用统一上下文管理器
        val contextManager = ContextManager.createInstance(project)
        
        // 使用工具类获取完整的上下文资源
        val contextResources = ContextUtils.getContextResourcesFromEditor(contextManager, editor, psiFile)
        
        // 将上下文资源添加到MCP服务器
        val mcpService = MCPManagerService.getInstance(project)
        contextResources.forEach { resource ->
            mcpService.getMCPServer().getMCPContextManager().addResource(resource)
        }
        
        val prompt = "Explain the following code in simple terms:\n\n$selectedText"
        print("Prompt: $prompt")

        // 显示解释对话框（默认显示加载状态）
        val dialog = ExplanationDialog(project)
        
        // 在新的线程中显示对话框，避免阻塞当前线程
        ApplicationManager.getApplication().invokeLater {
            dialog.show()
        }

        ApplicationManager.getApplication().executeOnPooledThread  {
            try {
                // 使用MCP协议调用LLM
                val response = runBlocking {
                    mcpService.getMCPServer().callLLMWithMCPContext(prompt) { chunk ->
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
                    if (isStream) {
                        // 已经开始流式显示，只需添加结尾换行
                        dialog.appendMessage("\n\n")
                    } else {
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