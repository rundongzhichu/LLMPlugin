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
                    // 3. 调用 LLM（模拟或真实 API）
                    val prompt = """
                        You are an expert programmer.
                        Generate unit test code for the following code:
                        ```java
                        $selectedText
                        ```
                        Instruction: $instruction
                        Return ONLY the unit test code, no explanation.
                    """.trimIndent()

                    val responseBuilder = StringBuilder()
                    // 使用流式响应处理
                    val response = withContext(Dispatchers.IO) {
                        // 采用流式读取AI返回值，拼接成最终字符串
                        callLLMAPI(prompt) { chunk ->
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
            popup.show()
        }
    }

    private suspend fun callLLMAPI(prompt: String, onChunkReceived: (String) -> Unit): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(prompt, onChunkReceived)
    }
}