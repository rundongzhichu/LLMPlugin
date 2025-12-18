package org.demo.llmplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ExplanationDialog(project: Project?, private val explanation: String) : DialogWrapper(project) {
    private lateinit var textArea: JTextArea

    init {
        title = "Code Explanation"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        textArea = JTextArea(explanation).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }

        val scrollPane = JScrollPane(textArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            preferredSize = Dimension(600, 400)
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }
}