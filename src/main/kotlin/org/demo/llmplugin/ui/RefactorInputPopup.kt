package org.demo.llmplugin.ui

import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContentImpl
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import javax.swing.JButton
import java.awt.FlowLayout
import javax.swing.JComponent

class RefactorInputPopup(
    private val project: Project,
    private val editor: Editor,
    private val onGenerate: (String, () -> Unit) -> Unit  // 修改回调签名，添加完成回调
) {
    private var popup: JBPopup? = null
    private lateinit var textField: JBTextField
    private lateinit var escHintLabel: JLabel
    private lateinit var loadingHintLabel: JLabel
    private lateinit var bottomPanel: JPanel
    private lateinit var buttonPanel: JPanel
    private lateinit var previewButton: JButton
    private lateinit var cancelButton: JButton
    var aiGeneratedCode: String? = null
    var originalCode: String? = null
    var fileType : FileType? = null

    fun show() {
        textField = JBTextField()
        textField.border = EmptyBorder(5, 8, 5, 8)
        textField.preferredSize = JBUI.size(300, 30)
        // 添加输入框内部提示文字
        textField.emptyText.text = "输入你的编码诉求"

        // 回车触发生成
        textField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    val text = textField.text.trim()
                    if (text.isNotEmpty()) {
                        showLoading()
                        // 使用executeOnPooledThread在后台线程执行生成逻辑
                        ApplicationManager.getApplication().executeOnPooledThread {
                            onGenerate(text) {
                                // 在EDT线程更新UI
                                ApplicationManager.getApplication().invokeLater {
                                    showPreviewButton()
                                }
                            }
                        }
                    }
                } else if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    popup?.cancel()
                }
            }
        })

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            
            // 输入框区域
            val inputPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 0, 8, 0) // 增加底部边距
                add(textField, BorderLayout.CENTER)

                // 输入框后面的Enter生成提示
                val enterHint = JLabel("Enter生成")
                enterHint.font = JBFont.small()
                enterHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                add(enterHint, BorderLayout.EAST)
            }
            add(inputPanel, BorderLayout.NORTH)

            // 底部面板容器
            bottomPanel = JPanel(BorderLayout()).apply {
                // 左侧：编辑当前选区提示
                val selectionHint = JLabel("编辑当前选区")
                selectionHint.font = JBFont.small()
                selectionHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                add(selectionHint, BorderLayout.WEST)
                
                // 右侧：ESC退出标签（初始状态）
                escHintLabel = JLabel("ESC退出")
                escHintLabel.font = JBFont.small()
                escHintLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                add(escHintLabel, BorderLayout.EAST)
            }
            add(bottomPanel, BorderLayout.SOUTH)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, textField)
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .setShowShadow(true)
            .setMinSize(JBUI.size(360, 110)) // 增加最小尺寸
            // 添加以下配置使popup在失去焦点时不消失
            .setCancelOnWindowDeactivation(false)
            .setCancelKeyEnabled(false)  // 禁用ESC键以外
            .setCancelOnClickOutside(false)
            .setTitle("LLM Refactor")
            .setMovable(true)
            .setModalContext(false)
            .createPopup()

        // 在光标位置下方显示
        val caret = editor.caretModel.primaryCaret
        val logicalPosition = caret.logicalPosition
        val point = editor.logicalPositionToXY(logicalPosition)
        // 增加偏移量避免遮挡
        val adjustedPoint = java.awt.Point(point.x, point.y + 60) // 增加Y轴偏移量
        val relativePoint = RelativePoint(editor.contentComponent, adjustedPoint)
        popup?.show(relativePoint)
        ApplicationManager.getApplication().invokeLater({
                textField.requestFocusInWindow()
            }, ModalityState.stateForComponent(editor.contentComponent))
    }

    private fun showLoading() {
        // 隐藏ESC提示，显示生成中提示
        escHintLabel.isVisible = false
        loadingHintLabel = JLabel("生成中...")
        loadingHintLabel.font = JBFont.small()
        loadingHintLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
        bottomPanel.add(loadingHintLabel, BorderLayout.EAST)
        bottomPanel.revalidate()
        bottomPanel.repaint()
    }

    private fun showPreviewButton() {
        // 移除生成中提示
        if (::loadingHintLabel.isInitialized) {
            bottomPanel.remove(loadingHintLabel)
            escHintLabel.isVisible = true
        }
        // 预览按钮：打开代码预览窗口
        aiGeneratedCode?.let { code ->
            originalCode?.let { original ->
                ApplicationManager.getApplication().invokeLater {
                    showAiDiffInStandardWindow(original, code)
                }
            }
        }

    }

    private fun applyAiGeneratedCode(newCode: String) {
        val project = editor.project ?: return
        // 修改代码用WriteCommandAction
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val selectionModel = editor.selectionModel
            val start = selectionModel.selectionStart
            val end = selectionModel.selectionEnd

            // 替换选中区域为AI生成的代码
            document.replaceString(start, end, newCode)

            // 取消选中，并定位光标到末尾
            selectionModel.removeSelection()
            editor.caretModel.moveToOffset(start + newCode.length)
        }
    }


    /**
     * 显示AI生成的代码的差异
     */
    private fun showAiDiffInStandardWindow(originalCode : String, aiGeneratedCode: String) {
        var title = "AI Generated Code Preview"
        // 创建 Document（用于语法高亮）
        val editorFactory = EditorFactory.getInstance()
        val originalDoc = editorFactory.createDocument(originalCode)
        val revisedDoc = editorFactory.createDocument(aiGeneratedCode)

        // 设置文件类型（关键！否则无高亮）
        originalDoc.setReadOnly(false)
        revisedDoc.setReadOnly(true)

        // 创建 Diff 内容
        val leftContent = DocumentContentImpl(project, revisedDoc, fileType)
        val rightContent = DocumentContentImpl(project, originalDoc, fileType)

        // 创建请求
        val request = SimpleDiffRequest(title, leftContent, rightContent, "AI Suggested", "Original")
        
        // 创建 Apply 按钮动作
        val applyAction = object : AnAction("Apply Changes", "Apply AI generated code", com.intellij.icons.AllIcons.Actions.Checked) {
            override fun actionPerformed(e: AnActionEvent) {
                // 应用AI生成的代码
                applyAiGeneratedCode(originalDoc.text)
                
                // 查找并关闭差异窗口
//                val diffWindow = WindowManager.getInstance().suggestParentWindow(project)
//                diffWindow?.dispose()
            }
        }

        
        // 创建动作列表并添加Apply和Merge All按钮
        val actionList = listOf<AnAction>(applyAction)
        request.putUserData(com.intellij.diff.util.DiffUserDataKeys.CONTEXT_ACTIONS, ArrayList(actionList))

        // 在 EDT 中打开
        ApplicationManager.getApplication().invokeLater {
            DiffManager.getInstance().showDiff(project, request)
        }
    }
}