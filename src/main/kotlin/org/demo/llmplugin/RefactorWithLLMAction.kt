package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.demo.llmplugin.ui.DiffBlockInlay
import org.demo.llmplugin.ui.RefactorInputPopup
import org.demo.llmplugin.util.CodeDiffer
import java.util.concurrent.atomic.AtomicReference

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
        val popup = RefactorInputPopup(project, editor) { instruction ->
            CoroutineScope(Dispatchers.Swing).launch {
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
                    val newCode = runBlocking {
                        callLLMAPI(prompt)
                    }

                    // 4. 显示AI生成代码的内联差异界面
                    ApplicationManager.getApplication().invokeLater {
                        showDiffSuggestions(editor, project, selectedText, newCode)
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

    fun showDiffSuggestions(
        editor: Editor,
        project: Project,
        originalCode: String,
        aiGeneratedCode: String
    ) {
        val diffBlocks = CodeDiffer.computeDiff(originalCode, aiGeneratedCode)

        // 按行号倒序插入（避免 offset 变化影响后续位置）
        diffBlocks.reversed().forEach { block ->
            val insertOffset = editor.logicalPositionToOffset(
                LogicalPosition(block.startLineInOriginal, 0)
            )

            val inlayRef = AtomicReference<Inlay<*>?>()
            val renderer = DiffBlockInlay(editor, project, block, inlayRef)

            val inlay = editor.inlayModel.addBlockElement(
                insertOffset,
                /* relatesToPrecedingText = */ true,
                /* showAbove = */ false,
                /* priority = */ 0,
                renderer
            )
            inlayRef.set(inlay)
        }
    }

    private suspend fun callLLMAPI(prompt: String): String {
        // 实际应调用 HTTP API
        return HttpUtils.callLocalLlm(prompt)
    }
}