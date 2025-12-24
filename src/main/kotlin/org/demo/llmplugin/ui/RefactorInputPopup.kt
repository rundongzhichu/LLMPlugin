package org.demo.llmplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
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
import com.intellij.openapi.vfs.VfsUtil
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
import org.demo.llmplugin.util.ContextUtils
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.containers.ContainerUtil
import java.awt.event.FocusListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.ui.awt.RelativePoint

class RefactorInputPopup(
    private val project: Project,
    private val editor: Editor,
    private val mode: Mode = Mode.REFATOR,
    private val onGenerate: (String, (String) -> Unit) -> Unit
) : JPanel(BorderLayout()) {
    enum class Mode {
        REFATOR, GENERATE_TEST
    }

    private lateinit var textArea: JTextArea
    private lateinit var contextPanel: JPanel
    private lateinit var contextTable: JTable
    private lateinit var contextTableModel: ContextTableModel
    private var popup: JBPopup? = null
    private lateinit var bottomPanel: JPanel
    private lateinit var escHintLabel: JLabel
    private val contextManager = ContextManager.createInstance(project)
    private var contextCode = ""
    private val maxContextLength = 10000 // 限制上下文长度

    init {
        initializeUI()
        loadInitialContext()
    }

    private fun initializeUI() {
        layout = BorderLayout()
        
        // 创建主面板
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        // 文本输入区域
        textArea = JTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 8
            columns = 40
            font = JBFont.monospace()
            
            // 添加键盘监听器
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    // Ctrl+Enter 触发生成
                    if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                        e.consume()
                        generate()
                    }
                    // ESC 关闭弹窗
                    else if (e.keyCode == KeyEvent.VK_ESCAPE) {
                        e.consume()
                        closePopup()
                    }
                }
            })
            
            // 添加焦点监听器，以便在获得焦点时更新ESC提示
            addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent?) {
                    updateEscHint("ESC关闭")
                }
                
                override fun focusLost(e: FocusEvent?) {
                    updateEscHint("ESC退出")
                }
            })
        }
        
        val textScrollPane = JBScrollPane(textArea).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        // 上下文面板
        createContextPanel()

        // 按钮面板
        val buttonPanel = createButtonPanel()

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
        panel.add(textScrollPane, BorderLayout.NORTH)
        panel.add(contextPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.EAST)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        add(panel, BorderLayout.CENTER)
    }
    
    private fun createContextPanel() {
        contextPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineLeft(JBUI.CurrentTheme.Border.CONTRAST_BORDER)
            preferredSize = Dimension(200, 0) // 设置最小宽度
        }
        
        val contextTitlePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JBLabel("上下文文件:"))
        }
        contextPanel.add(contextTitlePanel, BorderLayout.NORTH)
        
        // 初始化表格模型和表格
        contextTableModel = ContextTableModel()
        contextTable = JTable(contextTableModel).apply {
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            tableHeader.reorderingAllowed = false
            rowHeight = 30
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        }
        
        // 设置列宽
        if (contextTable.columnModel.columnCount > 1) {
            contextTable.columnModel.getColumn(1).preferredWidth = 30 // 删除按钮列较窄
        }
        
        // 创建表格按钮编辑器
        val buttonEditor = ContextTableButtonEditor(JBTextField()).apply {
            onRemove = { file -> removeContextFile(file) }
        }
        contextTable.columnModel.getColumn(1).cellEditor = buttonEditor
        
        val contextTableScrollPane = JBScrollPane(contextTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        contextPanel.add(contextTableScrollPane, BorderLayout.CENTER)
        
        // 添加添加上下文文件按钮
        val addContextButton = JButton("添加文件")
        addContextButton.addActionListener {
            addContextFiles()
        }
        
        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(addContextButton, BorderLayout.NORTH)
        contextPanel.add(buttonPanel, BorderLayout.SOUTH)
    }
    
    private fun createButtonPanel() = JPanel(GridLayout(2, 1)).apply {
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
        
        add(addButton)
        add(generateButton)
    }
    
    private fun updateEscHint(text: String) {
        escHintLabel.text = text
    }
    
    private fun loadInitialContext() {
        // 添加当前编辑的文件到上下文
        val psiFile = PsiManager.getInstance(project).getPsiFile(editor.document)
        if (psiFile != null) {
            val virtualFile = psiFile.virtualFile
            if (virtualFile != null) {
                addRichContextForCodeFile(virtualFile)
            }
        }
        
        // 更新上下文面板
        updateContextPanel()
        
        // 重新构建上下文代码字符串（使用压缩版本）
        contextCode = contextManager.buildCompressedContextCode()
    }
    
    private fun addRichContextForCodeFile(file: VirtualFile): Boolean {
        var added = false
        
        if (ContextUtils.isCodeFile(file)) {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file)
            if (psiFile != null) {
                // 添加结构化上下文（优先使用）
                val structureAdded = contextManager.addStructureContextFromPsiFile(psiFile)
                // 添加语法上下文（在文件开头）
                val syntaxAdded = contextManager.addSyntaxContext(psiFile, 0)
                // 添加虚拟文件上下文
                val virtualFileAdded = contextManager.addFileToContext(file)
                
                added = structureAdded > 0 || syntaxAdded > 0 || virtualFileAdded
            } else {
                // 如果无法获取PSI文件，则直接添加文件
                added = contextManager.addFileToContext(file)
            }
        } else {
            // 对于非代码文件，直接添加
            added = contextManager.addFileToContext(file)
        }
        
        return added
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
     * 添加上下文文件
     */
    private fun addContextFiles() {
        val descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
        val files = FileChooser.chooseFiles(descriptor, project, null)
        
        var addedCount = 0
        for (file in files) {
            if (file.virtualFile != null && addRichContextForCodeFile(file.virtualFile)) {
                addedCount++
            }
        }
        
        if (addedCount > 0) {
            updateContextPanel()
            contextCode = contextManager.buildCompressedContextCode()
            Messages.showInfoMessage(project, "成功添加 $addedCount 个文件到上下文", "添加成功")
        }
    }
    
    /**
     * 更新上下文面板
     */
    private fun updateContextPanel() {
        contextTableModel.setData(contextManager.getContextFiles())
    }
    
    private fun showLoading() {
        // 显示加载状态
        textArea.isEnabled = false
    }
    
    private fun showDiffPreview() {
        // 显示差异预览
        textArea.isEnabled = true
    }
    
    private fun generate() {
        val instruction = textArea.text.trim()
        if (instruction.isNotEmpty()) {
            showLoading()
            // 使用executeOnPooledThread在后台线程执行生成逻辑
            ApplicationManager.getApplication().executeOnPooledThread {
                onGenerate(instruction) { result ->
                    // 在EDT线程更新UI
                    ApplicationManager.getApplication().invokeLater {
                        showDiffPreview()
                        closePopup()
                    }
                }
            }
        }
    }
    
    private fun closePopup() {
        popup?.cancel()
    }
    
    fun show() {
        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(this, textArea)
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
    
    private fun adjustTextAreaHeight() {
        val lineCount = textArea.lineCount
        val preferredHeight = minOf(maxOf(lineCount * 25, 100), 300) // 根据行数调整高度，最小100，最大300
        textArea.preferredSize = Dimension(textArea.preferredSize.width, preferredHeight)
        revalidate()
    }
}