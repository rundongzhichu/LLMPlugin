package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.runBlocking
import org.demo.llmplugin.ui.ExplanationDialog
import org.demo.llmplugin.util.HttpUtils
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.util.ContextManager

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
        
        // 使用LSP获取更精确的代码上下文
        val lspContextExtractor = LSPContextExtractor(project)
        val contextResources = lspContextExtractor.extractContextFromEditor(editor)
        
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