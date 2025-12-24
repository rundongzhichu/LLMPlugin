package org.demo.llmplugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.demo.llmplugin.lsp.LSPContextExtractor
import org.demo.llmplugin.lsp.LSPIntegration

/**
 * 上下文隔离管理器
 * 为每个功能提供独立的上下文空间
 */
class IsolatedContextManager(private val project: Project) {
    private val lspContextExtractor = LSPContextExtractor()
    private val lspIntegration = LSPIntegration(project)
    
    // 为不同功能维护独立的上下文
    private val contextStores = mutableMapOf<String, MutableList<ContextResource>>()
    
    /**
     * 获取指定功能的上下文
     */
    fun getContextForFeature(featureId: String): List<ContextResource> {
        return contextStores.getOrDefault(featureId, mutableListOf()).toList()
    }
    
    /**
     * 为指定功能添加结构化上下文
     */
    fun addStructureContextForFeature(featureId: String, psiFile: PsiFile): Int {
        val resources = lspContextExtractor.extractContextFromPsiFile(psiFile)
        val store = getContextStoreForFeature(featureId)
        
        var addedCount = 0
        for (resource in resources) {
            store.add(resource)
            addedCount++
        }
        
        return addedCount
    }
    
    /**
     * 为指定功能添加语法上下文
     */
    fun addSyntaxContextForFeature(featureId: String, psiFile: PsiFile, offset: Int): Int {
        val resources = lspContextExtractor.extractSyntaxContext(psiFile, offset)
        val store = getContextStoreForFeature(featureId)
        
        var addedCount = 0
        for (resource in resources) {
            store.add(resource)
            addedCount++
        }
        
        return addedCount
    }
    
    /**
     * 为指定功能从编辑器添加上下文
     */
    fun addContextFromEditorForFeature(featureId: String, editor: Editor): List<ContextResource> {
        val resources = mutableListOf<ContextResource>()
        val store = getContextStoreForFeature(featureId)
        
        // 获取编辑器选择的代码上下文
        val selectionModel = editor.selectionModel
        if (selectionModel.hasSelection()) {
            val selectedText = selectionModel.selectedText ?: return emptyList()
            val startOffset = selectionModel.selectionStart
            val psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            
            psiFile?.let { file ->
                // 提取选中代码的上下文
                val codeContext = lspIntegration.getSelectedCodeContext(editor)
                codeContext?.let {
                    val resource = lspContextExtractor.createResourceFromCodeContext(it, file.virtualFile)
                    store.add(resource)
                    resources.add(resource)
                }
                
                // 提取语法上下文
                val syntaxResources = lspContextExtractor.extractSyntaxContext(file, startOffset)
                for (resource in syntaxResources) {
                    store.add(resource)
                    resources.add(resource)
                }
            }
        }
        
        return resources
    }
    
    /**
     * 为指定功能添加文件到上下文
     */
    fun addFileToContextForFeature(featureId: String, file: VirtualFile): Boolean {
        val store = getContextStoreForFeature(featureId)
        
        // 从虚拟文件创建资源并添加到上下文
        val content = try {
            file.contentsToByteArray().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        
        val resource = ContextResource(
            uri = "file://${file.path}",
            name = file.name,
            kind = if (file.isDirectory) "directory" else "file",
            description = "File in project: ${file.path}",
            content = content,
            metadata = mapOf(
                "path" to file.path,
                "size" to file.length,
                "extension" to (file.extension ?: ""),
                "isDirectory" to file.isDirectory
            )
        )
        
        store.add(resource)
        return true
    }
    
    /**
     * 为指定功能从上下文中移除文件
     */
    fun removeFileFromContextForFeature(featureId: String, file: VirtualFile) {
        val store = getContextStoreForFeature(featureId)
        val uri = "file://${file.path}"
        store.removeAll { it.uri.startsWith(uri) }
    }
    
    /**
     * 清除指定功能的上下文
     */
    fun clearContextForFeature(featureId: String) {
        contextStores[featureId]?.clear()
    }
    
    /**
     * 获取指定功能的上下文文件列表
     */
    fun getContextFilesForFeature(featureId: String): Set<VirtualFile> {
        val files = mutableSetOf<VirtualFile>()
        val store = contextStores[featureId] ?: return files
        
        for (resource in store) {
            if (resource.uri.startsWith("file://")) {
                val path = resource.uri.removePrefix("file://").substringBefore("#")
                val virtualFile = com.intellij.openapi.vfs.VfsUtil.findFile(java.nio.file.Paths.get(path), false)
                virtualFile?.let { files.add(it) }
            }
        }
        
        return files
    }
    
    /**
     * 构建指定功能的压缩上下文代码字符串
     */
    fun buildCompressedContextCodeForFeature(featureId: String): String {
        val contextCode = StringBuilder()
        val store = contextStores[featureId] ?: return ""
        
        for (resource in store) {
            if (resource.content != null) {
                contextCode.append("// Context: ${resource.name}\n")
                contextCode.append("${resource.content}\n\n")
            }
        }
        
        return contextCode.toString()
    }
    
    /**
     * 获取所有功能ID
     */
    fun getAllFeatureIds(): Set<String> {
        return contextStores.keys
    }
    
    /**
     * 获取上下文存储，如果不存在则创建新的
     */
    private fun getContextStoreForFeature(featureId: String): MutableList<ContextResource> {
        return contextStores.getOrPut(featureId) { mutableListOf() }
    }
    
    companion object {
        fun createInstance(project: Project): IsolatedContextManager {
            return IsolatedContextManager(project)
        }
    }
}