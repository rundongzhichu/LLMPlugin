package org.demo.llmplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.demo.llmplugin.ui.RefactorInputPopup
import org.demo.llmplugin.util.HttpUtils
import org.demo.llmplugin.util.ChatMessage
import org.demo.llmplugin.util.ContextManager
import com.intellij.psi.PsiManager
import org.demo.llmplugin.util.ContextUtils

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

        // 使用新的上下文管理器
        val contextManager = ContextManager.createInstance(project)
        
        // 将当前选中的代码添加为上下文
        if (psiFile != null) {
            val selectedContextResources = contextManager.addContextFromEditor(editor)
            selectedContextResources.forEach { resource ->
                // 添加到上下文中
            }
        }

        // 1. 弹出输入框（在 EDT 中）
        lateinit var popup: RefactorInputPopup
        popup = RefactorInputPopup(project, editor) { instruction, onComplete ->
            CoroutineScope(Dispatchers.Swing).launch  {
                // 调用大模型的函数都封装成协程
                try {
                    val contextCode = contextManager.buildCompressedContextCode()
                    // 3. 调用 LLM（模拟或真实 API）
                    val prompt = """
                        You are an expert programmer.
                        Original code:
                        ```java
                        $selectedText
                        ```
                        Instruction: $instruction
                        Context: $contextCode
                        Return ONLY the modified code, no explanation.
                    """.trimIndent()

                    val messages = listOf(
                        ChatMessage("system", "You are an expert code refactoring assistant. Follow the user's instructions to refactor the code. Only return the refactored code without any additional explanation or markdown code block markers."),
                        ChatMessage("user", prompt)
                    )

                    val responseBuilder = StringBuilder()
                    // 使用新的上下文管理器和HttpUtils调用LLM，遵循大模型交互规范
                    val response = withContext(Dispatchers.IO) {
                        // 采用流式读取AI返回值，拼接成最终字符串
                        HttpUtils.callLocalLlm(messages) { chunk ->
                            isStream = true
                            responseBuilder.append(chunk)
                        }
                    }

                    // 4. 获取最终结果
                    val result = if (isStream) {
                        responseBuilder.toString()
                    } else {
                        response
                    }
                    
                    // 5. 完成回调（在 EDT 中）
                    withContext(Dispatchers.Main) {
                        onComplete(result)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    // 错误处理（在 EDT 中）
                    withContext(Dispatchers.Main) {
                        Messages.showErrorDialog(
                            project,
                            "Error calling LLM: ${ex.message}",
                            "LLM Error"
                        )
                    }
                }
            }
        }

        // 在编辑器中显示弹出窗口
        ApplicationManager.getApplication().invokeLater {
            popup.show()
        }
    }
}