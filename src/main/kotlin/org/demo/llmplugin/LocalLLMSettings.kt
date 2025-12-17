package org.demo.llmplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "LocalLLMSettings", storages = [Storage("LocalLlmSettings.xml")])
class LocalLLMSettings : PersistentStateComponent<LocalLLMSettings> {
    var baseUrl: String = "http://localhost:11434"
    var model: String = "qwen:7b"
    var apiKey: String = ""

    override fun getState(): LocalLLMSettings = this
    override fun loadState(state: LocalLLMSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        val instance: LocalLLMSettings
            get() = ApplicationManager.getApplication().getService(LocalLLMSettings::class.java)
    }
}