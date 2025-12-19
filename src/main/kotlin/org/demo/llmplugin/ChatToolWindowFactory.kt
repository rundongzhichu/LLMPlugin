package org.demo.llmplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.demo.llmplugin.ui.ChatPanel
import org.jetbrains.annotations.NotNull

class ChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        val chatPanel = ChatPanel(project)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(chatPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}