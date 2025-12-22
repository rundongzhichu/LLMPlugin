package org.demo.llmplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.demo.llmplugin.util.HttpUtils
import java.awt.*
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val chatContainer: JPanel
    private val inputField: JTextField
    private val sendButton: JButton
    private var aiMessageTextPane: JTextPane? = null
    private lateinit var scrollPane: JBScrollPane
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val messageSpacing = 10 // 消息之间的固定间距

    private var isStream =  false

    init {
        // 创建聊天历史记录显示区域容器，使用GridBagLayout确保组件从顶部开始并保持固定间距
        chatContainer = JPanel(GridBagLayout())

        scrollPane = JBScrollPane(chatContainer).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
        }

        // 创建输入区域
        val inputPanel = JPanel(BorderLayout())
        inputField = JTextField()
        sendButton = JButton("Send")

        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)

        // 添加组件到主面板
        add(JLabel("AI Chat").apply { 
            horizontalAlignment = SwingConstants.CENTER 
            border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        }, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        // 添加事件监听器
        sendButton.addActionListener {
            sendMessage()
        }

        inputField.addActionListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val message = inputField.text.trim()
        if (message.isNotEmpty()) {
            // 显示用户消息
            addUserMessage(message)
            
            // 禁用输入框和发送按钮
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
                        HttpUtils.callLocalLlm(message) { chunk ->
                            isStream = true
                            // 流式接收数据块并在UI上逐个显示
                            SwingUtilities.invokeLater {
                                responseBuilder.append(chunk)
                                updateAiMessage(responseBuilder.toString())
                            }
                        }
                    }
                    
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