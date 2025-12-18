package org.demo.llmplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants

class ExplanationDialog(project: Project?) : DialogWrapper(project) {
    private lateinit var textArea: JTextArea
    private lateinit var loadingPanel: JPanel
    private lateinit var contentPanel: JPanel
    private lateinit var cardLayout: CardLayout
    private lateinit var mainPanel: JPanel

    init {
        title = "Code Explanation"
        isModal = false // 设置为非模态对话框，避免阻塞
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = JPanel(BorderLayout())
        
        // 创建卡片布局用于切换加载面板和内容面板
        cardLayout = CardLayout()
        mainPanel.layout = cardLayout
        
        // 创建加载面板
        loadingPanel = JPanel(BorderLayout()).apply {
            val label = JLabel("Generating code explanation, please wait...", SwingConstants.CENTER)
            add(label, BorderLayout.CENTER)
        }
        
        // 创建内容面板
        contentPanel = JPanel(BorderLayout()).apply {
            textArea = JTextArea().apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            }

            val scrollPane = JScrollPane(textArea).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                preferredSize = Dimension(600, 400)
            }
            
            add(scrollPane, BorderLayout.CENTER)
        }
        
        mainPanel.add(loadingPanel, "LOADING")
        mainPanel.add(contentPanel, "CONTENT")

        // 默认显示加载面板
        cardLayout.show(mainPanel, "LOADING")

        return mainPanel
    }
    
    fun showContent(explanation: String) {
        textArea.text = explanation
        cardLayout.show(mainPanel, "CONTENT")
    }
}