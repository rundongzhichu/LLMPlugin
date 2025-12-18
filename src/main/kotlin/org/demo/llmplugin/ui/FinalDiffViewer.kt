package org.demo.llmplugin.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.demo.llmplugin.util.DiffBlock
import org.demo.llmplugin.util.DiffType
import org.demo.llmplugin.util.DiffUtils
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class FinalDiffViewer(
    private val project: Project,
    private val editor: Editor,
    private val originalCode: String,
    private val aiGeneratedCode: String
) : DialogWrapper(project, false) {

    private val textPane = JTextPane()
    private val checkBoxes = mutableMapOf<Int, JCheckBox>()
    private var diffBlocks: List<DiffBlock> = emptyList()

    init {
        title = "AI Generated Code Diff Viewer"
        isModal = false
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 创建带滚动条的文本区域
        textPane.isEditable = false
        textPane.background = JBUI.CurrentTheme.CustomFrameDecorations.paneBackground()
        
        val scrollPane = JBScrollPane(textPane)
        scrollPane.preferredSize = Dimension(800, 500)
        
        // 显示差异内容
        displayDiffContent()
        
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    private fun displayDiffContent() {
        // 计算差异块
        val originalLines = originalCode.lines()
        val newLines = aiGeneratedCode.lines()
        diffBlocks = DiffUtils.computeDiffBlocks(originalLines, newLines)
        
        val doc = textPane.styledDocument
        textPane.text = ""
        
        // 定义样式
        val defaultStyle = SimpleAttributeSet()
        
        val originalStyle = SimpleAttributeSet()
        StyleConstants.setBackground(originalStyle, Color(255, 235, 235)) // 浅红色背景
        
        val newStyle = SimpleAttributeSet()
        StyleConstants.setBackground(newStyle, Color(235, 255, 235)) // 浅绿色背景
        
        val sameStyle = SimpleAttributeSet()
        StyleConstants.setForeground(sameStyle, Color.GRAY)
        
        var offset = 0
        checkBoxes.clear()
        
        diffBlocks.forEachIndexed { index, block ->
            when (block.type) {
                DiffType.SAME -> {
                    // 对于相同的代码块，我们只显示开始和结束部分，中间省略
                    val lines = block.originalContent.lines()
                    if (lines.size > 6) {
                        // 显示前3行和后3行，中间用省略号表示
                        val startLines = lines.take(3).joinToString("\n")
                        insertText(doc, "$startLines\n", sameStyle, offset)
                        offset += startLines.length + 1
                        
                        insertText(doc, "// ... (${lines.size - 6} lines skipped) ...\n", sameStyle, offset)
                        offset += "// ... (${lines.size - 6} lines skipped) ...\n".length
                        
                        val endLines = lines.takeLast(3).joinToString("\n")
                        insertText(doc, "$endLines\n", sameStyle, offset)
                        offset += endLines.length + 1
                    } else {
                        insertText(doc, "${block.originalContent}\n", sameStyle, offset)
                        offset += block.originalContent.length + 1
                    }
                }
                DiffType.DIFFERENT -> {
                    // 为不同的代码块添加复选框
                    val checkBox = JCheckBox("", true)
                    checkBoxes[index] = checkBox
                    
                    // 显示原始代码
                    insertText(doc, "// Original code:\n", defaultStyle, offset)
                    offset += "// Original code:\n".length
                    
                    insertText(doc, "${block.originalContent}\n", originalStyle, offset)
                    offset += block.originalContent.length + 1
                    
                    // 显示AI生成的代码
                    insertText(doc, "// AI suggested code:\n", defaultStyle, offset)
                    offset += "// AI suggested code:\n".length
                    
                    insertText(doc, "${block.newContent}\n", newStyle, offset)
                    offset += block.newContent.length + 1
                    
                    insertText(doc, "\n", defaultStyle, offset)
                    offset += 1
                }
            }
        }
    }

    private fun insertText(doc: StyledDocument, text: String, style: SimpleAttributeSet, offset: Int) {
        try {
            doc.insertString(offset, text, style)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("Apply Selected Changes") {
                override fun doAction(e: ActionEvent) {
                    applySelectedChanges()
                    close(OK_EXIT_CODE)
                }
            },
            object : DialogWrapperAction("Cancel") {
                override fun doAction(e: ActionEvent) {
                    close(CANCEL_EXIT_CODE)
                }
            }
        )
    }

    fun showInlineDiff() {
        show()
    }

    private fun applySelectedChanges() {
        // 根据用户选择应用更改
        val result = buildResultCode()
        
        // 应用到编辑器
        applyCodeToEditor(result)
    }

    private fun buildResultCode(): String {
        val lines = mutableListOf<String>()
        
        diffBlocks.forEachIndexed { index, block ->
            when (block.type) {
                DiffType.SAME -> {
                    lines.addAll(block.originalContent.lines())
                }
                DiffType.DIFFERENT -> {
                    val checkBox = checkBoxes[index]
                    if (checkBox != null && checkBox.isSelected) {
                        lines.addAll(block.newContent.lines())
                    } else {
                        lines.addAll(block.originalContent.lines())
                    }
                }
            }
        }
        
        return lines.joinToString("\n")
    }

    private fun applyCodeToEditor(newCode: String) {
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
}