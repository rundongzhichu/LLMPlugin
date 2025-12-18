package org.demo.llmplugin.ui

// DiffBlockInlay.kt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import org.demo.llmplugin.util.DiffBlock
import java.awt.*
import java.util.concurrent.atomic.AtomicReference

class DiffBlockInlay(
    private val editor: Editor,
    private val project: Project,
    private val block: DiffBlock,
    private val inlayRef: AtomicReference<Inlay<*>?>
) : EditorCustomElementRenderer {

    // 缓存计算结果，避免频繁重绘
    private var preferredSize: Dimension? = null
    private val lineHeight = editor.lineHeight
    private val font = try {
        editor.colorsScheme.getFont(EditorFontType.PLAIN)
    } catch (e: Exception) {
        Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    override fun calcWidthInPixels(inlay: Inlay<out EditorCustomElementRenderer>): Int {
        val maxWidth = block.newLines.map {
            editor.component.getFontMetrics(font).stringWidth("+ $it")
        }.maxOrNull() ?: 100
        return maxWidth + 40 // 留出按钮空间（简化处理）
    }

    override fun calcHeightInPixels(inlay: Inlay<out EditorCustomElementRenderer>): Int {
        return lineHeight * (block.newLines.size + 1) // +1 给按钮
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val g2 = g.create() as Graphics2D
        g2.font = font
        g2.color = JBColor.GRAY

        val yStart = targetRegion.y + lineHeight

        // 绘制新代码行（绿色）
        for ((index, line) in block.newLines.withIndex()) {
            g2.color = JBColor(Color(0, 150, 0), Color(0, 180, 0)) // 绿色（支持 Darcula）
            g2.drawString("+ $line", targetRegion.x, yStart + index * lineHeight)
        }

        // 绘制操作提示（简化：用文字代替按钮）
        g2.color = JBColor.BLUE
        g2.drawString("[Accept] [Reject]", targetRegion.x, yStart + block.newLines.size * lineHeight)

        g2.dispose()
    }

    // 可选：响应点击（需额外注册 MouseListener 到 editor.component）
    fun handleClick(x: Int, y: Int): Boolean {
        // 简化：点击任意位置接受
        applyChange()
        inlayRef.get()?.dispose()
        return true
    }

    private fun applyChange() {
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = editor.document
            val startOffset = editor.logicalPositionToOffset(
                LogicalPosition(block.startLineInOriginal, 0)
            )
            val endLine = block.startLineInOriginal + block.originalLines.size
            val endOffset = if (endLine >= doc.lineCount) {
                doc.textLength
            } else {
                doc.getLineStartOffset(endLine)
            }

            // 替换原始代码块为新代码
            val newText = block.newLines.joinToString("\n")
            doc.replaceString(startOffset, endOffset, newText)
        }
        dismiss()
    }

    private fun dismiss() {
        inlayRef.get()?.dispose()
    }
}