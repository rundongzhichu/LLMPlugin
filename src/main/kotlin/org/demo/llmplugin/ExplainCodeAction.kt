package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.demo.llmplugin.ui.ExplanationDialog
import javax.swing.*

class ExplainCodeAction : AnAction("Explain Selected Code") {
    override fun update(e: AnActionEvent) {
        val hasSelection = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = editor.project ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
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
                val response = runBlocking {
                    HttpUtils.callLocalLlm(prompt)
                }
                // 显示解释结果
                ApplicationManager.getApplication().invokeLater {
                    dialog.showContent(response)
                }
            } catch (e: Exception) {
                // 显示错误信息
                ApplicationManager.getApplication().invokeLater {
                    dialog.showContent("Failed to explain code: ${e.message}")
                }
            }
        }
    }
}