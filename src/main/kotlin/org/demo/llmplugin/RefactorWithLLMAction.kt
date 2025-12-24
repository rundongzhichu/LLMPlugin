package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.ui.RefactorInputPopup
import org.demo.llmplugin.util.HttpUtils

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

        // 使用LSP获取更精确的代码上下文
        val lspContextExtractor = LSPContextExtractor(project)
        val contextResources = lspContextExtractor.extractContextFromEditor(editor)
        
        // 将上下文资源添加到MCP服务器
        val mcpService = MCPManagerService.getInstance(project)
        contextResources.forEach { resource ->
            mcpService.getMCPServer().getMCPContextManager().addResource(resource)
        }
        
        // 如果有PSI文件，添加结构化和语法上下文
        psiFile?.let { psi ->
            // 添加结构化上下文（类、方法、字段信息）
            val structureContextResources = lspContextExtractor.extractStructureContextFromPsiFile(psi)
            structureContextResources.forEach { resource ->
                mcpService.getMCPServer().getMCPContextManager().addResource(resource)
            }
            
            // 添加语法上下文（当前光标位置）
            val caretOffset = editor.caretModel.primaryCaret.offset
            val syntaxContextResources = lspContextExtractor.extractSyntaxContext(psi, caretOffset)
            syntaxContextResources.forEach { resource ->
                mcpService.getMCPServer().getMCPContextManager().addResource(resource)
            }
            
            // 添加虚拟文件上下文
            psi.virtualFile?.let { virtualFile ->
                val virtualFileResource = lspContextExtractor.createResourceFromVirtualFile(virtualFile)
                mcpService.getMCPServer().getMCPContextManager().addResource(virtualFileResource)
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