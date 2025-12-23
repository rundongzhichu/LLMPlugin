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
import org.demo.llmplugin.ui.RefactorInputPopup
import org.demo.llmplugin.util.HttpUtils
import org.demo.llmplugin.util.ChatMessage
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.util.ContextManager
import com.intellij.psi.PsiManager

class GenerateUnitTestAction : AnAction("Generate Unit Test") {

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
            CoroutineScope(Dispatchers.Swing).launch {
                // 调用大模型的函数都封装成协程
                try {
                    val prompt = """
                        Generate unit test code for the following code:
                        ```java
                        $selectedText
                        ```
                        Instruction: $instruction
                        Return ONLY the unit test code, no explanation.
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
                        popup.mode = RefactorInputPopup.Mode.GENERATE_TEST
                        popup.originalCode = "" // 空的原始代码，因为我们是要生成新代码
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
                            "Failed to generate unit test: ${ex.message}",
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
            popup.mode = RefactorInputPopup.Mode.GENERATE_TEST
            // 设置预设模板
            popup.presetTemplate = "请为这段代码生成完整的单元测试，包括以下方面:\n" +
                    "1. 正常流程测试: （验证代码在正常输入下的行为）\n" +
                    "2. 边界条件测试: （测试边界值和极值情况）\n" +
                    "3. 异常情况测试: （验证代码对异常输入和错误条件的处理）\n" +
                    "4. 性能测试: （如适用，请考虑性能相关的测试用例）\n" +
                    "5. 安全性测试: （如适用，请考虑安全性相关的测试场景）"
            
            popup.show()
        }
    }

    private suspend fun callLLMAPI(messages: List<ChatMessage>, onChunkReceived: (String) -> Unit): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(messages, onChunkReceived)
    }
    
    private suspend fun callLLMAPIWithMCP(prompt: String, mcpService: MCPManagerService, onChunkReceived: (String) -> Unit): String {
        // 使用MCP协议调用LLM
        return mcpService.getMCPServer().callLLMWithMCPContext(prompt, onChunkReceived)
    }
}