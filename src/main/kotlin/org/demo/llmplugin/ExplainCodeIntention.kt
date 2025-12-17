package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JOptionPane

class ExplainCodeAction : AnAction("Explain Selected Code") {
    override fun update(e: AnActionEvent) {
        val hasSelection = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        val prompt = "Explain the following code in simple terms:\n\n$selectedText"
        print("Prompt: $prompt")

        CoroutineScope(Dispatchers.Main).launch {
            val response = HttpUtils.callLocalLlm(prompt)
            JOptionPane.showMessageDialog(
                editor.component,
                "<html><body width='400px'>${response.replace("\n", "<br>")}</body></html>",
                "LLM Response",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

}