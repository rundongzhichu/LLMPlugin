package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import org.demo.llmplugin.ui.AIInputPopup

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

        // 1. 弹出输入框（在 EDT 中）
//        val instruction = Messages.showInputDialog(
//            project,
//            "Enter your instruction for the selected code:",
//            "Refactor with LLM",
//            Messages.getQuestionIcon(),
//            "",
//            null
//        )?.trim() ?: return
//
//        if (instruction.isEmpty()) return

        // 2. 启动后台任务
        val popup = AIInputPopup(project, editor) { instruction ->
            ApplicationManager.getApplication().executeOnPooledThread {
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
                    // tongguo
                    val newCode = runBlocking {
                        callLLMAPI(prompt)
                    }

                    // 4. 回到 UI 线程更新编辑器
                    ApplicationManager.getApplication().invokeLater {
                        replaceSelectedText(editor, newCode)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to refactor code: ${ex.message}",
                            "LLM Error"
                        )
                    }
                }
            }
        }
        popup.show()
    }

    private fun replaceSelectedText(editor: Editor, newCode: String) {
        val project = editor.project ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val selectionModel = editor.selectionModel
            val start = selectionModel.selectionStart
            val end = selectionModel.selectionEnd

            // 替换选中区域
            document.replaceString(start, end, newCode)

            // 取消选中，并定位光标到末尾
            selectionModel.removeSelection()
            editor.caretModel.moveToOffset(start + newCode.length)
        }
    }

    private suspend fun callLLMAPI(prompt: String): String {

        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(prompt)
    }
}