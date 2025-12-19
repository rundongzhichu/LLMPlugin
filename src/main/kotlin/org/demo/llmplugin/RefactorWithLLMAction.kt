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
import org.demo.llmplugin.ui.RefactorInputPopup



class RefactorWithLLMAction : AnAction("Refactor with LLM...") {
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
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

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

                    // 使用 runBlocking 来调用 suspend 函数
                    var newCode = callLLMAPI(prompt)


                    // 4. 将AI生成的代码和原始代码传递给popup
                    ApplicationManager.getApplication().invokeLater {
                        popup.aiGeneratedCode = newCode
                        popup.originalCode = selectedText
                    }

                    // 调用完成回调
                    onComplete()
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to refactor code: ${ex.message}",
                            "LLM Error"
                        )
                    }
                    // 即使出错也要调用完成回调
                    onComplete()
                }
            }
        }

        // 2. 显示输入框（在 EDT 中）
        ApplicationManager.getApplication().invokeLater {
            popup.show()
        }
    }

    private suspend fun callLLMAPI(prompt: String): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(prompt)
    }
}