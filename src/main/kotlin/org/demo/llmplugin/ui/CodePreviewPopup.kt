package org.demo.llmplugin.ui

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class CodePreviewPopup(
    private val project: Project,
    private val originalCode: String,
    private val aiGeneratedCode: String,
    private val onApply: (String) -> Unit
) : DialogWrapper(project) {

    private lateinit var aiEditor: Editor
    private lateinit var originalEditor: Editor
    private lateinit var aiDocument: Document
    private lateinit var originalDocument: Document

    init {
        title = "AI Generated Code Preview"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建AI生成代码的文档
        aiDocument = EditorFactory.getInstance().createDocument(aiGeneratedCode)
        
        // 创建原始代码的文档
        originalDocument = EditorFactory.getInstance().createDocument(originalCode)
        
        // 创建AI生成代码的编辑器（可编辑）
        aiEditor = EditorFactory.getInstance().createEditor(aiDocument, project, FileTypeManager.getInstance().getFileTypeByExtension("java"), false)
        
        // 创建原始代码的编辑器（只读）
        originalEditor = EditorFactory.getInstance().createEditor(originalDocument, project, FileTypeManager.getInstance().getFileTypeByExtension("java"), true)
        
        // 配置AI编辑器
        val aiEditorEx = aiEditor as EditorEx
        aiEditorEx.setPlaceholder("AI generated code (editable)...")
        
        // 配置原始代码编辑器
        val originalEditorEx = originalEditor as EditorEx
        originalEditorEx.setPlaceholder("Original code (read-only)...")
        
        // 创建分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.dividerLocation = 400
        splitPane.leftComponent = aiEditor.component
        splitPane.rightComponent = originalEditor.component
        
        // 设置编辑器大小
        aiEditor.component.preferredSize = Dimension(400, 400)
        originalEditor.component.preferredSize = Dimension(400, 400)
        
        panel.add(splitPane, BorderLayout.CENTER)
        
        return panel
    }

    override fun createSouthPanel(): JComponent? {
        val panel = JPanel()
        
        val applyButton = JButton("Apply")
        applyButton.addActionListener {
            val modifiedCode = aiDocument.text
            onApply(modifiedCode)
            close(OK_EXIT_CODE)
        }
        
        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener {
            close(CANCEL_EXIT_CODE)
        }
        
        panel.add(applyButton)
        panel.add(cancelButton)
        
        return panel
    }

    override fun dispose() {
        super.dispose()
        EditorFactory.getInstance().releaseEditor(aiEditor)
        EditorFactory.getInstance().releaseEditor(originalEditor)
    }
}