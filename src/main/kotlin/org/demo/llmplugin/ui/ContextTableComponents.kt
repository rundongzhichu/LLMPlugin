package org.demo.llmplugin.ui

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer

/**
 * 上下文文件表格模型
 */
class ContextTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("文件名", "操作")
    private var data: List<VirtualFile> = emptyList()

    fun setData(files: Set<VirtualFile>) {
        data = files.toList()
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = data.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val file = data[rowIndex]
        return when (columnIndex) {
            0 -> file.name
            1 -> "x"
            else -> null
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return columnIndex == 1 // 只有操作列可编辑
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        // 不在模型中直接处理删除操作，而是由使用者处理
        // 使用者可以通过getFileAt获取文件，然后执行删除操作
    }

    fun getFileAt(row: Int): VirtualFile? {
        return if (row >= 0 && row < data.size) data[row] else null
    }
}

/**
 * 上下文表格单元格渲染器
 */
class ContextTableCellRenderer : TableCellRenderer {
    private val label = JLabel()
    private val button = JButton("x")

    init {
        label.font = JBFont.small()
        label.isOpaque = true
        button.font = JBFont.small()
        button.margin = JBUI.insets(0, 3, 0, 3)
        button.toolTipText = "移除此文件"
    }

    override fun getTableCellRendererComponent(table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        when (column) {
            0 -> {
                label.text = value as String?
                label.background = if (isSelected) table?.selectionBackground else table?.background
                label.foreground = if (isSelected) table?.selectionForeground else table?.foreground
                return label
            }
            1 -> {
                return button
            }
            else -> {
                label.text = ""
                return label
            }
        }
    }
}

/**
 * 上下文表格按钮编辑器
 */
class ContextTableButtonEditor(textField: JTextField) : DefaultCellEditor(textField) {
    private val button = JButton("x")
    var onRemove: ((VirtualFile) -> Unit)? = null
    private var currentRow = -1
    private var table: JTable? = null

    init {
        button.font = JBFont.small()
        button.margin = JBUI.insets(0, 3, 0, 3)
        button.toolTipText = "移除此文件"
        button.addActionListener {
            val file = (table?.model as? ContextTableModel)?.getFileAt(currentRow)
            file?.let { onRemove?.invoke(it) }
        }
    }

    override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        this.currentRow = row
        this.table = table
        return button
    }

    override fun getCellEditorValue(): Any = "x"
}