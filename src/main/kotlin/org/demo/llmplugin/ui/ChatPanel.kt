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

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val chatContainer: JPanel
    private val inputField: JTextField
    private val sendButton: JButton
    private val addButton: JButton
    private var aiMessageTextPane: JTextPane? = null
    private lateinit var scrollPane: JBScrollPane
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val messageSpacing = 10 // 消息之间的固定间距
    private val contextManager = ContextManager.createInstance(project) // 使用带项目参数的上下文管理器实例
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
        val inputPanel = JPanel(BorderLayout())
        inputField = JTextField()
        sendButton = JButton("Send")
        addButton = JButton("+")

        // 设置按钮提示
        addButton.toolTipText = "添加文件到上下文"
        sendButton.toolTipText = "发送消息"

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            add(addButton)
            add(sendButton)
        }

        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(buttonPanel, BorderLayout.EAST)

        // 添加组件到主面板
        add(JLabel("AI Chat").apply { 
            horizontalAlignment = SwingConstants.CENTER 
            border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        }, BorderLayout.NORTH)
        add(contextPanel, BorderLayout.WEST)
        add(scrollPane, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        // 添加事件监听器
        sendButton.addActionListener {
            sendMessage()
        }

        addButton.addActionListener {
            addFilesToContext()
        }

        inputField.addActionListener {
            sendMessage()
        }
        
        // 添加系统消息介绍上下文功能
        addSystemMessage("欢迎使用AI助手！点击'+'按钮可以添加文件到上下文，帮助AI更好地理解您的需求。")
    }

    /**
     * 创建上下文面板
     */
    private fun createContextPanel() {
        contextPanel = JPanel(BorderLayout())
        contextPanel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), 
            "上下文文件", 
            TitledBorder.LEFT, 
            TitledBorder.TOP
        )
        contextPanel.preferredSize = Dimension(150, 0) // 设置宽度为150像素

        // 创建表格模型和表格
        contextTableModel = ContextTableModel()
        val contextTableEditor = ContextTableButtonEditor(JTextField())
        contextTableEditor.onRemove = { file -> removeContextFile(file) }
        
        contextTable = JTable(contextTableModel).apply {
            tableHeader
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            setDefaultRenderer(Object::class.java, ContextTableCellRenderer())
            setDefaultEditor(Object::class.java, contextTableEditor)
            intercellSpacing = Dimension(0, 0)
            rowHeight = 25
            setShowGrid(false)
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        }
        
        val contextTableScrollPane = JScrollPane(contextTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        contextPanel.add(contextTableScrollPane, BorderLayout.CENTER)
        updateContextList()
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
            emptyLabel.font = JBFont.small()
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

    private fun addFilesToContext() {
        // 使用ContextManager选择文件
        val selectedFiles = contextManager.showFileChooser(project)
        
        // 批量添加文件到上下文管理器中
        val addedCount = contextManager.addFilesToContext(selectedFiles)
        
        // 更新上下文列表
        updateContextList()
        
        // 显示一条消息，告知用户已添加文件到上下文
        val message = if (addedCount == selectedFiles.size) {
            "已将 ${selectedFiles.size} 个文件添加到上下文"
        } else {
            "已将 $addedCount 个新文件添加到上下文（${selectedFiles.size - addedCount} 个文件已存在）"
        }
        addSystemMessage(message)
    }

    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isNotEmpty()) {
            // 显示用户消息
            addUserMessage(message)
            
            // 添加用户消息到历史记录
            chatHistory.add(ChatMessage("user", message))
            
            // 构建包含上下文的系统消息（使用压缩版本）
            val contextCode = contextManager.buildCompressedContextCode()
            val systemMessageContent = if (contextCode.isNotEmpty()) {
                "以下代码作为上下文提供，请在回答时考虑这些信息:\n\n$contextCode"
            } else {
                "你是一个专业的编程助手，帮助用户解答编程相关问题。"
            }
            
            // 准备消息列表
            val messages = mutableListOf<ChatMessage>()
            messages.add(ChatMessage("system", systemMessageContent))
            messages.addAll(chatHistory)
            
            // 要用输入框和发送按钮
            inputField.isEnabled = false
            sendButton.isEnabled = false
            
            // 显示AI正在思考
            addAiThinkingMessage()
            
            // 在后台线程中调用AI服务
            coroutineScope.launch {
                try {
                    val responseBuilder = StringBuilder()
                    
                    // 使用流式响应处理
                    val response = withContext(Dispatchers.IO) {
                        HttpUtils.callLocalLlm(messages) { chunk ->
                            isStream = true
                            // 流式接收数据块并在UI上逐个显示
                            SwingUtilities.invokeLater {
                                responseBuilder.append(chunk)
                                updateAiMessage(responseBuilder.toString())
                            }
                        }
                    }
                    
                    // 添加AI回复到历史记录
                    chatHistory.add(ChatMessage("assistant", response))
                    
                    // 在所有数据接收完成后更新完整响应
                    SwingUtilities.invokeLater {
                        if(isStream) {
                            updateAiMessage(responseBuilder.toString())
                        } else {
                            updateAiMessage(response)
                        }
                        finishAiMessage()
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        // 显示错误信息
                        addAiErrorMessage("Error occurred: ${e.message ?: e.javaClass.simpleName}")
                    }
                } finally {
                    // 重新启用输入框和发送按钮
                    SwingUtilities.invokeLater {
                        inputField.isEnabled = true
                        sendButton.isEnabled = true
                        inputField.requestFocusInWindow()
                    }
                    isStream = false
                }
            }
            
            // 清空输入框
            inputField.text = ""
        }
    }

    private fun addUserMessage(message: String) {
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = chatContainer.componentCount
            anchor = GridBagConstraints.NORTHWEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(if (chatContainer.componentCount > 0) messageSpacing else 0, 0, 0, 0)
        }
        
        val messagePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 10)
            background = Color(0, 0, 0)
        }
        
        val messageTextPane = JTextPane().apply {
            border = JBUI.Borders.empty(5)
            text = message
            isEditable = false
            font = Font(Font.DIALOG, Font.PLAIN, 12)
            isOpaque = false
            // 设置文本右对齐
            // 使用样式设置文本颜色，避免背景变黑
            val style = SimpleAttributeSet()
            StyleConstants.setForeground(style, Color.WHITE)  // 设置黑色文本
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
}