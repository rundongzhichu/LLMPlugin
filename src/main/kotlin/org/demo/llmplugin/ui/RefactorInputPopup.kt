package org.demo.llmplugin.ui

import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContentImpl
import com.intellij.diff.contents.EmptyContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import javax.swing.JButton
import java.awt.FlowLayout
import javax.swing.JComponent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.ui.Messages
import javax.swing.JTextArea
import javax.swing.JScrollPane
import org.demo.llmplugin.util.ContextManager
import org.demo.llmplugin.mcp.MCPManagerService
import org.demo.llmplugin.lsp.LSPContextExtractor
import javax.swing.BoxLayout
import javax.swing.BorderFactory
import javax.swing.Box
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.awt.Dimension
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.JTable
import javax.swing.ListSelectionModel
import java.awt.Component
import javax.swing.UIManager
import javax.swing.JTextField
import javax.swing.JCheckBox
import com.intellij.psi.PsiManager

class RefactorInputPopup(
    private val project: Project,
    private val editor: Editor,
    private val onGenerate: (String, () -> Unit) -> Unit  // 修改回调签名，添加完成回调
) {
    private var popup: JBPopup? = null
    private lateinit var textArea: JTextArea
    private lateinit var textScrollPane: JScrollPane
    private lateinit var escHintLabel: JLabel
    private lateinit var loadingHintLabel: JLabel
    private lateinit var bottomPanel: JPanel
    private lateinit var buttonPanel: JPanel
    private lateinit var previewButton: JButton
    private lateinit var cancelButton: JButton
    private lateinit var contextPanel: JPanel
    private lateinit var contextTable: JTable
    private lateinit var contextTableModel: ContextTableModel
    private lateinit var contextLabel: JLabel
    var aiGeneratedCode: String? = null
    var originalCode: String? = null
    var fileType : FileType? = null
    var mode: Mode = Mode.REFACTOR
    var presetTemplate: String = ""
    var contextCode: String? = null
    private val contextManager = ContextManager.createInstance(project)
    private val mcpService = MCPManagerService.getInstance(project)
    private val lspContextExtractor = LSPContextExtractor(project)

    enum class Mode {
        REFACTOR,
        GENERATE_TEST
    }

    fun show() {
        // 创建多行文本区域
        textArea = JTextArea(3, 30)
        textArea.border = EmptyBorder(5, 8, 5, 8)
        
        // 设置初始文本
        textArea.text = if (mode == Mode.GENERATE_TEST && presetTemplate.isNotEmpty()) {
            presetTemplate
        } else {
            ""
        }

        // 监听文本变化以调整高度
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                adjustTextAreaHeight()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                adjustTextAreaHeight()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                adjustTextAreaHeight()
            }
        })

        // 回车触发生成（Ctrl+Enter）
        textArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                    val text = textArea.text.trim()
                    if (text.isNotEmpty()) {
                        showLoading()
                        // 使用executeOnPooledThread在后台线程执行生成逻辑
                        ApplicationManager.getApplication().executeOnPooledThread {
                            onGenerate(text) {
                                // 在EDT线程更新UI
                                ApplicationManager.getApplication().invokeLater {
                                    showDiffPreview()
                                }
                            }
                        }
                    }
                } else if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    popup?.cancel()
                }
            }
        })

        // 创建带滚动条的文本区域
        textScrollPane = JScrollPane(textArea).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = EmptyBorder(0, 0, 0, 0)
            preferredSize = Dimension(400, 80) // 设置首选大小
        }

        // 创建上下文面板
        contextPanel = JPanel(BorderLayout())
        contextPanel.border = BorderFactory.createTitledBorder("上下文文件")
        
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
            preferredSize = Dimension(0, 100)
        }
        
        contextPanel.add(contextTableScrollPane, BorderLayout.CENTER)
        updateContextPanel()

        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        // 输入框区域
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 0, 8, 0) // 增加底部边距
            add(textScrollPane, BorderLayout.CENTER)
        }
        
        // 添加按钮面板
        val addButton = JButton("+")
        addButton.toolTipText = "添加上下文文件"
        addButton.addActionListener {
            addContextFiles()
        }
        
        val generateButton = JButton("生成 (Ctrl+Enter)")
        generateButton.addActionListener {
            val text = textArea.text.trim()
            if (text.isNotEmpty()) {
                showLoading()
                // 使用executeOnPooledThread在后台线程执行生成逻辑
                ApplicationManager.getApplication().executeOnPooledThread {
                    onGenerate(text) {
                        // 在EDT线程更新UI
                        ApplicationManager.getApplication().invokeLater {
                            showDiffPreview()
                        }
                    }
                }
            }
        }
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(addButton)
            add(generateButton)
        }

        // 底部面板容器
        bottomPanel = JPanel(BorderLayout()).apply {
            // 左侧：编辑当前选区提示
            val selectionHint = JLabel(
                if (mode == Mode.GENERATE_TEST) "为选中代码生成测试" else "编辑当前选区"
            )
            selectionHint.font = JBFont.small()
            selectionHint.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(selectionHint, BorderLayout.WEST)
            
            // 右侧：ESC退出标签（初始状态）
            escHintLabel = JLabel("ESC退出")
            escHintLabel.font = JBFont.small()
            escHintLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            add(escHintLabel, BorderLayout.EAST)
        }
        
        // 组装主面板
        panel.add(inputPanel, BorderLayout.NORTH)
        panel.add(contextPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, textArea)
            .setRequestFocus(true)
            .setResizable(true)
            .setMovable(false)
            .setShowShadow(true)
            .setMinSize(JBUI.size(400, 200))
            // 添加以下配置使popup在失去焦点时不消失
            .setCancelOnWindowDeactivation(false)
            .setCancelKeyEnabled(false)  // 禁用ESC键以外
            .setCancelOnClickOutside(false)
            .setTitle(if (mode == Mode.GENERATE_TEST) "生成单元测试" else "LLM Refactor")
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
                textArea.requestFocusInWindow()
            }, ModalityState.stateForComponent(editor.contentComponent))
        
        // 初始调整文本区域高度
        adjustTextAreaHeight()
    }

    /**
     * 调整文本区域高度以适应内容
     */
    private fun adjustTextAreaHeight() {
        // 获取文本行数
        val lines = textArea.lineCount
        // 设置最小3行，最大10行
        val displayLines = lines.coerceIn(3, 10)
        
        // 计算新高度 (每行大约20像素)
        val fontHeight = textArea.font.size + 4 // 加上一些padding
        val newHeight = displayLines * fontHeight + 20 // 加上边框和padding
        
        // 更新滚动面板的首选大小
        textScrollPane.preferredSize = Dimension(textScrollPane.width, newHeight)
        
        // 重新验证布局
        textScrollPane.revalidate()
        textScrollPane.parent?.revalidate()
    }

    /**
     * 添加上下文文件
     */
    private fun addContextFiles() {
        // 使用ContextManager选择文件
        val selectedFiles = contextManager.showFileChooser(project)
        
        // 批量添加文件到上下文管理器中
        var addedCount = 0
        for (file in selectedFiles) {
            // 如果是代码文件，尝试使用LSP提取器添加更丰富的上下文
            if (file.extension in listOf("java", "kt", "kts", "scala", "groovy", "js", "ts", "py", "cpp", "c", "h", "go", "rs", "php", "rb", "swift", "dart", "cs")) {
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile != null) {
                    // 添加结构化上下文
                    val structureAdded = contextManager.addStructureContextFromPsiFile(psiFile)
                    // 添加语法上下文（在文件开头）
                    val syntaxAdded = contextManager.addSyntaxContext(psiFile, 0)
                    // 添加虚拟文件上下文
                    val virtualFileAdded = contextManager.addVirtualFileContext(file)
                    
                    if (structureAdded > 0 || syntaxAdded > 0 || virtualFileAdded) {
                        addedCount++
                    }
                } else {
                    // 如果无法获取PSI文件，则使用传统方法添加
                    if (contextManager.addFileToContext(file)) {
                        addedCount++
                    }
                }
            } else {
                // 对于非代码文件，使用传统方法添加
                if (contextManager.addFileToContext(file)) {
                    addedCount++
                }
            }
        }
        
        // 更新上下文面板
        updateContextPanel()
        
        // 构建上下文代码字符串（使用压缩版本）
        contextCode = contextManager.buildCompressedContextCode()
        
        // 显示提示信息
        if (addedCount < selectedFiles.size) {
            Messages.showMessageDialog(
                project,
                "已添加 $addedCount 个新文件到上下文（${selectedFiles.size - addedCount} 个文件已存在）",
                "上下文文件添加结果",
                Messages.getInformationIcon()
            )
        }
    }

    /**
     * 删除上下文文件
     */
    private fun removeContextFile(file: VirtualFile) {
        // 从上下文管理器中移除文件
        contextManager.removeFileFromContext(file)
        
        // 更新上下文面板
        updateContextPanel()
        
        // 重新构建上下文代码字符串（使用压缩版本）
        contextCode = contextManager.buildCompressedContextCode()
    }

    /**
     * 更新上下文面板
     */
    private fun updateContextPanel() {
        contextTableModel.setData(contextManager.getContextFiles())
        
        // 如果没有上下文文件，显示提示信息
        if (contextManager.getContextFiles().isEmpty()) {
            contextPanel.removeAll()
            val emptyLabel = JLabel("暂无上下文文件")
            emptyLabel.font = JBFont.small()
            emptyLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
            emptyLabel.horizontalAlignment = JLabel.CENTER
            contextPanel.add(emptyLabel, BorderLayout.CENTER)
        } else {
            // 确保表格视图被添加回去
            if (contextPanel.componentCount == 0 || contextPanel.components[0] is JLabel) {
                contextPanel.removeAll()
                val contextTableScrollPane = JScrollPane(contextTable).apply {
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    preferredSize = Dimension(0, 100)
                }
                contextPanel.add(contextTableScrollPane, BorderLayout.CENTER)
            }
        }
        
        contextPanel.revalidate()
        contextPanel.repaint()
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

    private fun showDiffPreview() {
        // 移除生成中提示
        if (::loadingHintLabel.isInitialized) {
            bottomPanel.remove(loadingHintLabel)
            escHintLabel.isVisible = true
        }
        // 预览按钮：打开代码预览窗口
        aiGeneratedCode?.let { code ->
            ApplicationManager.getApplication().invokeLater {
                if (mode == Mode.GENERATE_TEST) {
                    showGeneratedCode(code)
                } else {
                    originalCode?.let { original ->
                        showAiDiffInStandardWindow(original, code)
                    }
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
     * 显示生成的代码（用于生成测试的情况）
     */
    private fun showGeneratedCode(generatedCode: String) {
        var title = "AI Generated Test Code"
        // 创建 Document（用于语法高亮）
        val editorFactory = EditorFactory.getInstance()
        val generatedDoc = editorFactory.createDocument(generatedCode)

        // 设置文件类型（关键！否则无高亮）
        generatedDoc.setReadOnly(true)

        // 创建 Diff 内容
        val leftContent = DocumentContentImpl(project, generatedDoc, fileType)
        val rightContent = EmptyContent()

        // 创建请求
        val request = SimpleDiffRequest(title, leftContent, rightContent, "Generated Test", "None")
        
        // 创建 Apply 按钮动作
        val applyAction = object : AnAction("Insert Test Code", "Insert generated test code", com.intellij.icons.AllIcons.Actions.Checked) {
            override fun actionPerformed(e: AnActionEvent) {
                // 应用AI生成的代码
                applyAiGeneratedCode(generatedDoc.text)
            }
        }

        // 创建动作列表并添加Apply按钮
        val actionList = listOf<AnAction>(applyAction)
        request.putUserData(com.intellij.diff.util.DiffUserDataKeys.CONTEXT_ACTIONS, ArrayList(actionList))

        // 在 EDT 中打开
        ApplicationManager.getApplication().invokeLater {
            DiffManager.getInstance().showDiff(project, request)
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
            }
        }

        // 创建动作列表并添加Apply按钮
        val actionList = listOf<AnAction>(applyAction)
        request.putUserData(com.intellij.diff.util.DiffUserDataKeys.CONTEXT_ACTIONS, ArrayList(actionList))

        // 在 EDT 中打开
        ApplicationManager.getApplication().invokeLater {
            DiffManager.getInstance().showDiff(project, request)
        }
    }
}