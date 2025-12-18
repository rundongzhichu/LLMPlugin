
package org.demo.llmplugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.InlayProperties

class DiffBlockInlay(
    private val editor: Editor,
    private val diffBlock: org.demo.llmplugin.util.DiffBlock,
    private val originalCode: String,
    private val newCode: String
) {
    private var inlay: Inlay<*>? = null
    private var highlighters: MutableList<RangeHighlighter> = mutableListOf()

    fun show() {
        val document = editor.document
        val startOffset = document.getLineStartOffset(diffBlock.startLine)
        
        // 创建包含AI代码和操作按钮的面板
        val panel = createDiffPanel()
        
        // 在指定位置插入inlay
        val props = InlayProperties().showAbove(true).priority(100)
        inlay = editor.inlayModel.addInlineElement(startOffset, props, JComponentEditorCustomElementRenderer(panel))
        
        // 高亮显示原始代码区域
        highlightOriginalCode()
        
        // 高亮显示新代码区域
        highlightNewCode()
    }
    
    private fun createDiffPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor(0xF5F5F5, 0x3C3F41)
        panel.border = CompoundBorder(
            MatteBorder(1, 0, 1, 0, JBColor.GRAY),
            EmptyBorder(5, 0, 5, 0)
        )
        
        // 操作按钮部分 - 放在顶部，左对齐
        val buttonPanel = createButtonPanel()
        panel.add(buttonPanel)
        
        // AI代码部分 - 新代码
        val newCodePanel = createCodePanel(diffBlock.newText, true)
        panel.add(newCodePanel)
        
        return panel
    }
    
    private fun createCodePanel(codeText: String, isNewCode: Boolean): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = EmptyBorder(5, 10, 5, 10)
        panel.background = if (isNewCode) JBColor(0xE0F0FF, 0x2D3033) else JBColor(0xF5F5F5, 0x3C3F41)
        
        val textPane = JTextPane()
        textPane.text = codeText
        textPane.isEditable = false
        textPane.background = if (isNewCode) JBColor(0xE0F0FF, 0x2D3033) else JBColor(0xF5F5F5, 0x3C3F41)
        textPane.border = EmptyBorder(5, 5, 5, 5)
        textPane.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        
        val scrollPane = JBScrollPane(textPane)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.border = EmptyBorder(0, 0, 0, 0)
        scrollPane.background = if (isNewCode) JBColor(0xE0F0FF, 0x2D3033) else JBColor(0xF5F5F5, 0x3C3F41)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createButtonPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT)) // 左对齐确保按钮在左上角
        panel.border = EmptyBorder(5, 10, 5, 10)
        panel.background = JBColor(0xF5F5F5, 0x3C3F41)
        
        val acceptButton = JButton("接受")
        acceptButton.addActionListener {
            applyChanges()
        }
        
        val rejectButton = JButton("拒绝")
        rejectButton.addActionListener {
            removeInlay()
        }
        
        panel.add(acceptButton)
        panel.add(rejectButton)
        
        return panel
    }
    
    private fun highlightOriginalCode() {
        val document = editor.document
        val startLine = diffBlock.startLine
        val endLine = diffBlock.endLine
        
        if (startLine < document.lineCount && endLine <= document.lineCount) {
            val startOffset = document.getLineStartOffset(startLine)
            val endOffset = if (endLine < document.lineCount) {
                document.getLineStartOffset(endLine)
            } else {
                document.textLength
            }
            
            val highlighter = editor.markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SELECTION,
                TextAttributes().apply {
                    backgroundColor = JBColor(0xFFF0E0, 0x454545)
                },
                HighlighterTargetArea.EXACT_RANGE
            )
            
            highlighters.add(highlighter)
        }
    }
    
    private fun highlightNewCode() {
        // 这里可以添加对新代码的高亮显示逻辑
    }
    
    private fun applyChanges() {
        // 应用AI生成的代码更改
        val project = editor.project ?: return
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val startLine = diffBlock.startLine
            val endLine = diffBlock.endLine
            
            if (startLine < document.lineCount && endLine <= document.lineCount) {
                val startOffset = document.getLineStartOffset(startLine)
                val endOffset = if (endLine < document.lineCount) {
                    document.getLineStartOffset(endLine)
                } else {
                    document.textLength
                }
                
                document.replaceString(startOffset, endOffset, diffBlock.newText)
            }
        }
        
        removeInlay()
    }
    
    private fun removeInlay() {
        // 移除inlay元素
        inlay?.dispose()
        
        // 移除所有高亮
        highlighters.forEach { highlighter ->
            editor.markupModel.removeHighlighter(highlighter)
        }
        highlighters.clear()
    }
    
    private class JComponentEditorCustomElementRenderer(private val component: JComponent) : com.intellij.openapi.editor.EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return inlay.editor.component.width
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            SwingUtilities.paintComponent(g, component, inlay.editor.contentComponent, targetRegion)
        }
    }
}