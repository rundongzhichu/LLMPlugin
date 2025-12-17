package org.demo.llmplugin

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class LocalLLMConfigurable : Configurable {
    private val settings = LocalLLMSettings.instance
    private val baseUrlField = JBTextField()
    private val modelField = JBTextField()
    private val apiKeyField = JBTextField()

    override fun createComponent(): JComponent {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Base URL:", baseUrlField, 1, false)
                .addLabeledComponent("Model Name:", modelField, 1, false)
                .addLabeledComponent("API Key (optional):", apiKeyField, 1, false)
                .panel
    }

    override fun reset() {
        baseUrlField.text = settings.baseUrl
        modelField.text = settings.model
        apiKeyField.text = settings.apiKey
    }

    override fun apply() {
        settings.baseUrl = baseUrlField.text.trim()
        settings.model = modelField.text.trim()
        settings.apiKey = apiKeyField.text.trim()
    }

    override fun isModified(): Boolean {
        return baseUrlField.text != settings.baseUrl ||
                modelField.text != settings.model ||
                apiKeyField.text != settings.apiKey
    }

    override fun getDisplayName(): String = "Local LLM"
}