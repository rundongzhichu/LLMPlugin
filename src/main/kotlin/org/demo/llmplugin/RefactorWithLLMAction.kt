package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.demo.llmplugin.ui.RefactorInputPopup
import org.demo.llmplugin.util.HttpUtils
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.util.ContextManager
import com.intellij.psi.PsiManager
import org.demo.llmplugin.util.ContextUtils

class RefactorWithLLMAction : AnAction("Refactor with LLM...") {

    private var isStream = false
    override fun update(e: AnActionEvent) {
        // 仅当有文本被选中时启用
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selection = editor?.selectionModel?.selectedText
        e.presentation.isEnabledAndVisible = !selection.isNullOrEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)

        // 使用统一上下文管理器
        val contextManager = ContextManager.createInstance(project)
        
        // 将当前选中的代码添加为上下文
        val mcpService = MCPManagerService.getInstance(project)
        // 只添加当前选中的代码，不添加其他自动上下文
        if (psiFile != null) {
            val selectedContextResources = contextManager.addContextFromEditor(editor)
            selectedContextResources.forEach { resource ->
                mcpService.getMCPServer().getMCPContextManager().addResource(resource)
            }
        }

        // 1. 弹出输入框（在 EDT 中）
        lateinit var popup: RefactorInputPopup
        popup = RefactorInputPopup(project, editor) { instruction, onComplete ->
            CoroutineScope(Dispatchers.Swing).launch  {
                // 调用大模型的函数都封装成协程
                try {
                    // 3. 调用 LLM（模拟或真实 API）
                    val prompt = """
                       You are an expert programmer.
                        Original code:
                        ```java
                        $selectedText
                        ```
                        Instruction: $instruction
                        Return ONLY the modified code, no explanation.
                    """.trimIndent()

                    val responseBuilder = StringBuilder()
                    // 使用MCP协议调用LLM
                    val response = withContext(Dispatchers.IO) {
                        // 采用流式读取AI返回值，拼接成最终字符串
                        callLLMAPIWithMCP(prompt, mcpService) { chunk ->
                            isStream = true
                            responseBuilder.append(chunk)
                        }
                    }

                    // 4. 将AI生成的代码和原始代码传递给popup
                    ApplicationManager.getApplication().invokeLater {
                        popup.originalCode = selectedText
                        if(isStream) {
                            popup.aiGeneratedCode = responseBuilder.toString()
                        } else {
                            popup.aiGeneratedCode = response
                        }
                        onComplete()
                    }

                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to refactor code: ${ex.message}",
                            "LLM Error"
                        )
                        // 即使出错也要调用完成回调
                        ApplicationManager.getApplication().invokeLater {
                            onComplete()
                        }
                    }
                } finally {
                    isStream = false
                }
            }
        }

        // 2. 显示输入框（在 EDT 中）
        ApplicationManager.getApplication().invokeLater {
            popup.show()
        }
    }

    private suspend fun callLLMAPI(prompt: String, onChunkReceived: (String) -> Unit): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(prompt, onChunkReceived)
    }
    
    private suspend fun callLLMAPIWithMCP(prompt: String, mcpService: MCPManagerService, onChunkReceived: (String) -> Unit): String {
        // 使用MCP协议调用LLM
        return mcpService.getMCPServer().callLLMWithMCPContext(prompt, onChunkReceived)
    }

}