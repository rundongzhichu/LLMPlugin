package org.demo.llmplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.*
import org.demo.llmplugin.util.HttpUtils
import org.demo.llmplugin.util.ContextManager
import org.demo.llmplugin.util.ChatMessage
import java.awt.*
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.border.TitledBorder
import javax.swing.ListSelectionModel
import javax.swing.JTextField
import javax.swing.JCheckBox
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.border.EmptyBorder
import javax.swing.text.StyledDocument
import org.demo.llmplugin.lsp.LSPContextExtractor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val chatContainer: JPanel
    private val inputField: JTextField
    private val sendButton: JButton
    private val addButton: JButton
    private var aiMessageTextPane: JTextPane? = null
    private lateinit var scrollPane: JBScrollPane
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val messageSpacing = 10 // 消息之间的固定间距
    private val contextManager = ContextManager.createInstance(project) // 使用独立的上下文管理器实例
    private val chatHistory = mutableListOf<ChatMessage>()
    private var isStream = false
    private lateinit var contextPanel: JPanel
    private lateinit var contextListPanel: JPanel
    private lateinit var contextTable: JTable
    private lateinit var contextTableModel: ContextTableModel

    init {
        // 创建聊天历史记录显示区域容器，使用GridBagLayout确保组件从顶部开始并保持固定间距
        chatContainer = JPanel(GridBagLayout())

        scrollPane = JBScrollPane(chatContainer).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
        }

        // 创建上下文面板
        createContextPanel()

        // 创建输入区域
        val inputPanel = createInputPanel()

        // 组装主面板
        add(scrollPane, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)
        add(contextPanel, BorderLayout.NORTH)
    }

    private fun createInputPanel(): JPanel {
        val inputPanel = JPanel(BorderLayout())
        inputPanel.border = JBUI.Borders.empty(8)

        // 输入框
        inputField = JTextField()
        inputField.addActionListener {
            sendMessage()
        }

        // 添加键盘快捷键：Ctrl+Enter 发送消息
        inputField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "send")
        inputField.actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                sendMessage()
            }
        })

        // 发送按钮
        sendButton = JButton("发送 (Ctrl+Enter)").apply {
            addActionListener {
                sendMessage()
            }
        }

        // 添加文件按钮
        addButton = JButton("添加上下文文件").apply {
            addActionListener {
                addContextFiles()
            }
        }

        // 创建按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(addButton)
            add(sendButton)
        }

        // 组装输入面板
        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.SOUTH)

        return inputPanel
    }

    private fun createContextPanel() {
        contextPanel = JPanel(BorderLayout())
        contextPanel.border = JBUI.Borders.customLineBelow(JBUI.CurrentTheme.Border.CONTRAST_BORDER)
        
        val contextTitlePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JBLabel("上下文文件:"))
        }
        contextPanel.add(contextTitlePanel, BorderLayout.NORTH)
        
        contextListPanel = JPanel(BorderLayout())
        contextTableModel = ContextTableModel()
        contextTable = JTable(contextTableModel).apply {
            // 设置表格选择模式，只允许选择行
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            // 禁用列移动
            tableHeader.reorderingAllowed = false
            // 设置行高
            rowHeight = 30
            // 设置表格不可调整列宽
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        }
        
        // 设置列宽
        if (contextTable.columnModel.columnCount > 1) {
            contextTable.columnModel.getColumn(1).preferredWidth = 30 // 删除按钮列较窄
        }
        
        val contextTableScrollPane = JBScrollPane(contextTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        contextListPanel.add(contextTableScrollPane, BorderLayout.CENTER)
        contextPanel.add(contextListPanel, BorderLayout.CENTER)
        
        // 初始化上下文列表
        updateContextList()
    }

    private fun addContextFiles() {
        val fileChooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createFileChooser(
                com.intellij.openapi.fileChooser.FileChooserDescriptor(true, true, false, false, false, false),
                null,
                null
            )
        
        val files = com.intellij.openapi.fileChooser.FileChooser.chooseFiles(fileChooser, project, null)
        
        if (files.isNotEmpty()) {
            var addedCount = 0
            
            for (file in files) {
                val virtualFile = file.virtualFile
                if (virtualFile != null && virtualFile.isValid) {
                    // 根据文件类型决定如何添加到上下文
                    if (org.demo.llmplugin.util.ContextUtils.isCodeFile(virtualFile)) {
                        // 对于代码文件，使用LSP提取结构化上下文
                        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
                        if (psiFile != null) {
                            val structureAdded = contextManager.addStructureContextFromPsiFile(psiFile)
                            val virtualFileAdded = contextManager.addFileToContext(virtualFile)
                            if (structureAdded > 0 || virtualFileAdded) {
                                addedCount++
                            }
                        } else {
                            // 如果无法获取PSI文件，则直接添加虚拟文件
                            if (contextManager.addFileToContext(virtualFile)) {
                                addedCount++
                            }
                        }
                    } else {
                        // 对于非代码文件，直接添加
                        if (contextManager.addFileToContext(virtualFile)) {
                            addedCount++
                        }
                    }
                }
            }
            
            updateContextList()
            addSystemMessage("已添加 $addedCount 个文件到上下文")
        }
    }

    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isEmpty()) return

        // 添加用户消息到聊天历史
        addHumanMessage(message)
        inputField.text = ""

        // 显示AI正在思考的消息
        addAiThinkingMessage()

        // 使用协程发送消息到LLM
        isStream = false
        coroutineScope.launch {
            try {
                // 获取当前上下文
                val contextCode = contextManager.buildCompressedContextCode()
                
                // 构建完整提示
                val fullPrompt = if (contextCode.isNotEmpty()) {
                    "基于以下上下文信息回答问题：\n\n$contextCode\n\n问题：$message"
                } else {
                    message
                }
                
                // 使用HttpUtils调用LLM，遵循大模型交互规范
                val messages = listOf(
                    ChatMessage("system", "You are a helpful coding assistant. Use the provided context to answer questions accurately."),
                    ChatMessage("user", fullPrompt)
                )
                
                val response = HttpUtils.callLocalLlm(messages) { chunk ->
                    isStream = true
                    // 流式接收数据块并在UI上逐个显示
                    SwingUtilities.invokeLater {
                        updateAiMessage(chunk)
                    }
                }
                
                // 如果不是流式响应，则显示完整响应
                if (!isStream) {
                    SwingUtilities.invokeLater {
                        updateAiMessage(response)
                    }
                } else {
                    // 如果是流式响应，添加换行以分隔消息
                    SwingUtilities.invokeLater {
                        updateAiMessage("\n\n")
                    }
                }
                
                // 完成AI消息
                SwingUtilities.invokeLater {
                    finishAiMessage()
                }
                
                // 将消息添加到历史记录
                chatHistory.add(ChatMessage("user", message))
                chatHistory.add(ChatMessage("assistant", if (!isStream) response else aiMessageTextPane?.text ?: response))
                
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addAiErrorMessage("Error: ${e.message}")
                }
            }
        }
    }

    private fun addHumanMessage(message: String) {
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = chatContainer.componentCount
            anchor = GridBagConstraints.NORTHEAST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(messageSpacing, 0, 0, 0) // 与前一个组件保持固定间距
        }

        val messagePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 10)
            background = Color(0, 100, 0) // 绿色背景表示用户消息
        }

        val messageTextPane = JTextPane().apply {
            border = JBUI.Borders.empty(5)
            text = message
            isEditable = false
            font = Font(Font.DIALOG, Font.PLAIN, 12)
            isOpaque = false
            // 使用样式设置文本颜色
            val style = SimpleAttributeSet()
            StyleConstants.setForeground(style, Color.WHITE)
            StyleConstants.setAlignment(style, StyleConstants.ALIGN_RIGHT)
            styledDocument.setParagraphAttributes(0, styledDocument.length, style, false)
        }

        messagePanel.add(messageTextPane, BorderLayout.CENTER)

        // 创建一个包装面板，将消息靠右对齐
        val wrapper = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(messagePanel)
        }

        chatContainer.add(wrapper, gbc)
        scrollToBottom()
    }

    private fun addSystemMessage(message: String) {
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = chatContainer.componentCount
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(messageSpacing, 0, 0, 0)
        }

        val messagePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 10)
            background = Color(50, 50, 50) // 灰色背景表示系统消息
        }

        val messageTextPane = JTextPane().apply {
            border = JBUI.Borders.empty(5)
            text = message
            isEditable = false
            font = Font(Font.DIALOG, Font.PLAIN, 12)
            isOpaque = false
            // 使用样式设置文本颜色
            val style = SimpleAttributeSet()
            StyleConstants.setForeground(style, Color.LIGHT_GRAY)
            StyleConstants.setAlignment(style, StyleConstants.ALIGN_CENTER)
            styledDocument.setParagraphAttributes(0, styledDocument.length, style, false)
        }

        messagePanel.add(messageTextPane, BorderLayout.CENTER)

        // 创建一个包装面板，将消息居中对齐
        val wrapper = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(messagePanel)
        }

        chatContainer.add(wrapper, gbc)
        scrollToBottom()
    }

    private fun addAiThinkingMessage() {
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = chatContainer.componentCount
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(messageSpacing, 0, 0, 0) // 与前一个组件保持固定间距
        }

        val messagePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 10)
            background = Color(0, 0, 0)
        }

        aiMessageTextPane = JTextPane().apply {
            border = JBUI.Borders.empty(5)
            text = "AI is thinking..."
            isEditable = false
            font = Font(Font.DIALOG, Font.PLAIN, 12)
            isOpaque = false
            // 使用样式设置文本颜色，避免背景变黑
            val style = SimpleAttributeSet()
            StyleConstants.setForeground(style, Color.WHITE)  // 设置黑色文本
            styledDocument.setParagraphAttributes(0, styledDocument.length, style, false)
        }

        messagePanel.add(aiMessageTextPane, BorderLayout.CENTER)

        // 创建一个包装面板，将消息靠左对齐
        val wrapper = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(messagePanel)
        }

        chatContainer.add(wrapper, gbc)
        scrollToBottom()
    }

    private fun updateAiMessage(content: String) {
        // 更新AI消息内容并支持Markdown渲染
        aiMessageTextPane?.text = content
        scrollToBottom()
    }

    private fun finishAiMessage() {
        // 消息完成，可以做一些清理工作
        scrollToBottom()
    }

    private fun addAiErrorMessage(message: String) {
        // 更新错误消息
        aiMessageTextPane?.apply {
            text = message
            foreground = Color.RED
        }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val vertical = scrollPane.verticalScrollBar
            vertical.value = vertical.maximum
        }
        chatContainer.revalidate()
        chatContainer.repaint()
    }

    /**
     * 更新上下文文件列表
     */
    private fun updateContextList() {
        contextTableModel.setData(contextManager.getContextFiles())

        // 如果没有上下文文件，显示提示信息
        if (contextManager.getContextFiles().isEmpty()) {
            contextPanel.removeAll()
            val emptyLabel = JLabel("暂无文件")
            emptyLabel.font = JBUI.CurrentTheme.Label.disabledForeground()
            emptyLabel.horizontalAlignment = JLabel.CENTER
            contextPanel.add(emptyLabel, BorderLayout.CENTER)
        } else {
            // 确保表格视图被添加回去
            if (contextPanel.componentCount == 0 || contextPanel.components[0] is JLabel) {
                contextPanel.removeAll()
                val contextTableScrollPane = JScrollPane(contextTable).apply {
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                }
                contextPanel.add(contextTableScrollPane, BorderLayout.CENTER)
            }
        }

        contextPanel.revalidate()
        contextPanel.repaint()
    }

    /**
     * 从上下文中删除文件
     */
    private fun removeContextFile(file: VirtualFile) {
        contextManager.removeFileFromContext(file)
        updateContextList()
        addSystemMessage("已从上下文中移除文件: ${file.name}")
    }
}