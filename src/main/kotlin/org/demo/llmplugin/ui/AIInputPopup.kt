package org.demo.llmplugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class AIInputPopup(
    private val project: Project,
    private val editor: Editor,
    private val onGenerate: (String) -> Unit
) {
    private var popup: JBPopup? = null

    fun show() {
        val textField = JBTextField()
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
                        onGenerate(text)
                    }
                } else if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    popup?.cancel()
                }
            }
        })

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(textField, BorderLayout.CENTER)

            // 输入框后面的Enter生成提示
            val enterHint = JLabel("Enter生成")
            enterHint.font = JBFont.small()
            enterHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(enterHint, BorderLayout.EAST)

            // 底部提示面板
            val bottomPanel = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, 0)

                // 左下角提示编辑当前选区
                val selectionHint = JLabel("编辑当前选区")
                selectionHint.font = JBFont.small()
                selectionHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                add(selectionHint, BorderLayout.WEST)

                // 右下角提示ESC退出
                val escHint = JLabel("ESC退出")
                escHint.font = JBFont.small()
                escHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                add(escHint, BorderLayout.EAST)
            }
            add(bottomPanel, BorderLayout.NORTH)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, textField)
            .setRequestFocus(true)
            .setResizable(false)
            .setMovable(false)
            .setShowShadow(true)
            .setMinSize(JBUI.size(300, 50))
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
        // 修改坐标计算部分，增大垂直偏移量
        val adjustedPoint = java.awt.Point(point.x, point.y + 150)  // 从30增加到50
        val relativePoint = RelativePoint(editor.contentComponent, adjustedPoint)
        popup?.show(relativePoint)
        ApplicationManager.getApplication().invokeLater({
                textField.requestFocusInWindow()
            }, ModalityState.stateForComponent(editor.contentComponent))
    }
}