package org.demo.llmplugin.ui

// DiffBlockInlay.kt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.ui.ClickListener
import org.demo.llmplugin.util.DiffBlock
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.*

class DiffBlockInlay(
    private val editor: Editor,
    private val project: Project,
    private val diffBlock: DiffBlock,
    private val inlayRef: MutableRef<Inlay<*>?>
) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false

        // 显示新代码（绿色背景示意）
        for (line in diffBlock.newLines) {
            val label = JLabel("+ $line").apply {
                foreground = java.awt.Color(0, 128, 0) // 绿色
                font = editor.colorsScheme.getFont(EditorColorsScheme.EditorFontName)
            }
            add(label)
        }

        // 操作按钮
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
        }

        val acceptBtn = JButton("✅").apply {
            toolTipText = "采纳此变更"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                applyChange()
            }
        }

        val rejectBtn = JButton("❌").apply {
            toolTipText = "忽略此变更"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                dismiss()
            }
        }

        buttonPanel.add(acceptBtn)
        buttonPanel.add(Box.createHorizontalStrut(8))
        buttonPanel.add(rejectBtn)
        add(buttonPanel)
    }

    private fun applyChange() {
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = editor.document
            val startOffset = editor.logicalPositionToOffset(
                LogicalPosition(diffBlock.startLineInOriginal, 0)
            )
            val endLine = diffBlock.startLineInOriginal + diffBlock.originalLines.size
            val endOffset = if (endLine >= doc.lineCount) {
                doc.textLength
            } else {
                doc.getLineStartOffset(endLine)
            }

            // 替换原始代码块为新代码
            val newText = diffBlock.newLines.joinToString("\n")
            doc.replaceString(startOffset, endOffset, newText)
        }
        dismiss()
    }

    private fun dismiss() {
        inlayRef.value?.dispose()
    }
}