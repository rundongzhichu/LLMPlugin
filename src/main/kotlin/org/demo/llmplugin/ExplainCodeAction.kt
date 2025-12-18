package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.demo.llmplugin.ui.ExplanationDialog
import javax.swing.SwingUtilities

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

        CoroutineScope(Dispatchers.Swing).launch {
            val response = HttpUtils.callLocalLlm(prompt)
            SwingUtilities.invokeLater {
                val dialog = ExplanationDialog(project, response)
                dialog.show()
            }
        }
    }
}

