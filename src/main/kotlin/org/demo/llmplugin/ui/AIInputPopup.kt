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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import javax.swing.JButton
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints

class AIInputPopup(
    private val project: Project,
    private val editor: Editor,
    private val onGenerate: suspend (String) -> Unit
) {
    private var popup: JBPopup? = null
    private lateinit var textField: JBTextField
    private lateinit var escHintLabel: JLabel
    private lateinit var loadingHintLabel: JLabel
    private lateinit var bottomPanel: JPanel
    private lateinit var buttonPanel: JPanel
    private lateinit var acceptButton: JButton
    private lateinit var rejectButton: JButton
    private var generatedResult: String? = null

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
                        // 使用协程执行挂起函数
                        CoroutineScope(Dispatchers.Swing).launch {
                            try {
                                onGenerate(text)
                            } finally {
                                // 确保在任何情况下都隐藏加载状态
                                ApplicationManager.getApplication().invokeLater {
                                    showCompletionButtons()
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

    private fun showCompletionButtons() {
        // 移除生成中提示
        if (::loadingHintLabel.isInitialized) {
            bottomPanel.remove(loadingHintLabel)
        }
        
        // 添加接受和拒绝按钮
        buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            border = EmptyBorder(0, 0, 0, 0)
            
            acceptButton = JButton("接受").apply {
                font = JBFont.medium()
                addActionListener {
                    popup?.cancel()
                }
            }
            
            rejectButton = JButton("拒绝").apply {
                font = JBFont.medium()
                addActionListener {
                    // 恢复初始状态
                    restoreInitialState()
                }
            }
            
            add(acceptButton)
            add(rejectButton)
        }
        
        bottomPanel.add(buttonPanel, BorderLayout.EAST)
        bottomPanel.revalidate()
        bottomPanel.repaint()
    }
    
    private fun restoreInitialState() {
        // 移除按钮面板
        if (::buttonPanel.isInitialized) {
            bottomPanel.remove(buttonPanel)
        }
        
        // 显示ESC退出标签
        escHintLabel.isVisible = true
        bottomPanel.add(escHintLabel, BorderLayout.EAST)
        
        bottomPanel.revalidate()
        bottomPanel.repaint()
        textField.text = ""
    }
}