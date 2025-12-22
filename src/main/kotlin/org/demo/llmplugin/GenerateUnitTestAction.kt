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

        // 1. 弹出输入框（在 EDT 中）
        lateinit var popup: RefactorInputPopup
        popup = RefactorInputPopup(project, editor) { instruction, onComplete ->
            CoroutineScope(Dispatchers.Swing).launch {
                // 调用大模型的函数都封装成协程
                try {
                    // 获取上下文代码（如果有）
                    val contextCode = popup.contextCode ?: ""
                    
                    // 构建消息列表
                    val messages = mutableListOf<ChatMessage>()
                    
                    // 添加系统消息
                    val systemMessage = if (contextCode.isNotEmpty()) {
                        "You are an expert programmer. The following is the context code that may be referenced in the code to be tested:\n\n$contextCode"
                    } else {
                        "You are an expert programmer."
                    }
                    messages.add(ChatMessage("system", systemMessage))
                    
                    // 添加用户消息
                    val userMessage = """
                        Generate unit test code for the following code:
                        ```java
                        $selectedText
                        ```
                        Instruction: $instruction
                        Return ONLY the unit test code, no explanation.
                    """.trimIndent()
                    messages.add(ChatMessage("user", userMessage))

                    val responseBuilder = StringBuilder()
                    // 使用流式响应处理
                    val response = withContext(Dispatchers.IO) {
                        // 采用流式读取AI返回值，拼接成最终字符串
                        callLLMAPI(messages) { chunk ->
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
            
            // 询问用户是否需要添加上下文代码
            val result = Messages.showYesNoDialog(
                project,
                "是否需要为生成测试提供上下文代码？\n上下文代码可以帮助AI更好地理解被测试代码的依赖关系和使用场景。",
                "添加上下文代码",
                "添加上下文代码",
                "直接生成测试",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                popup.showContextCodeDialog()
            }
            
            popup.show()
        }
    }

    private suspend fun callLLMAPI(messages: List<ChatMessage>, onChunkReceived: (String) -> Unit): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(messages, onChunkReceived)
    }
}