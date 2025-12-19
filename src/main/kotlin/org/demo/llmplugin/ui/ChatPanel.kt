package org.demo.llmplugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import org.demo.llmplugin.HttpUtils
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val textArea: JTextArea
    private val inputField: JTextField
    private val sendButton: JButton
    private var aiThinkingLineStartIndex = -1

    init {
        // 创建聊天历史记录显示区域
        textArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        val scrollPane = JScrollPane(textArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
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
            appendMessage("You: $message\n")
            
            // 禁用输入框和发送按钮
            inputField.isEnabled = false
            sendButton.isEnabled = false
            
            // 显示AI正在思考
            appendMessage("AI is thinking...\n")
            aiThinkingLineStartIndex = textArea.document.length - "AI is thinking...\n".length
            
            // 在后台线程中调用AI服务
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    // 使用流式响应处理
                    val response = runBlocking {
                        HttpUtils.callLocalLlm(message) { chunk ->
                            // 流式接收数据块并在UI上逐个显示
                            ApplicationManager.getApplication().invokeLater {
                                if (aiThinkingLineStartIndex >= 0) {
                                    // 移除"AI is thinking..."行
                                    val doc = textArea.document
                                    doc.remove(aiThinkingLineStartIndex, "AI is thinking...\n".length)
                                    
                                    // 添加AI标识前缀
                                    doc.insertString(aiThinkingLineStartIndex, "AI: ", null)
                                    aiThinkingLineStartIndex = -1
                                }
                                
                                // 追加新的内容块
                                appendMessage(chunk)
                            }
                        }
                    }
                    
                    // 在所有数据接收完成后添加额外的换行
                    ApplicationManager.getApplication().invokeLater {
                        if (aiThinkingLineStartIndex >= 0) {
                            // 如果还没有移除"AI is thinking..."，则移除它
                            val doc = textArea.document
                            doc.remove(aiThinkingLineStartIndex, "AI is thinking...\n".length)
                            
                            // 显示完整响应
                            appendMessage("AI: $response\n\n")
                        } else {
                            // 已经开始流式显示，只需添加结尾换行
                            appendMessage("\n\n")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        if (aiThinkingLineStartIndex >= 0) {
                            // 移除"AI is thinking..."行
                            val doc = textArea.document
                            try {
                                doc.remove(aiThinkingLineStartIndex, "AI is thinking...\n".length)
                                aiThinkingLineStartIndex = -1
                            } catch (e: Exception) {
                                // 忽略可能的索引错误
                            }
                        }
                        
                        // 显示错误信息
                        appendMessage("AI: Error occurred - ${e.message}\n\n")
                    }
                } finally {
                    // 重新启用输入框和发送按钮
                    ApplicationManager.getApplication().invokeLater {
                        inputField.isEnabled = true
                        sendButton.isEnabled = true
                        inputField.requestFocusInWindow()
                    }
                }
            }
            
            // 清空输入框
            inputField.text = ""
        }
    }

    private fun appendMessage(message: String) {
        textArea.append(message)
        textArea.caretPosition = textArea.document.length
    }
}