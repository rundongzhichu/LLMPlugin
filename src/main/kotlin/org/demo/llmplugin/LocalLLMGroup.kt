package org.demo.llmplugin

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

// LocalLLMGroup.kt
class LocalLLMGroup : ActionGroup("Local LLM Assistant", true) {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ExplainCodeAction(),
        )
    }
}