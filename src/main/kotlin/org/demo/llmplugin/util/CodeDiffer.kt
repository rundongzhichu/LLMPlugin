package org.demo.llmplugin.util

import difflib.Delta
import difflib.DiffUtils
import difflib.Patch

class CodeDiffer {
    companion object {
        fun computeDifferences(originalCode: String, newCode: String): List<DiffBlock> {
            val originalLines = originalCode.lines()
            val newLines = newCode.lines()
            
            val patch = DiffUtils.diff(originalLines, newLines)
            val deltas = patch.deltas
            
            val result = mutableListOf<DiffBlock>()
            for (delta in deltas) {
                result.add(convertDeltaToDiffBlock(delta as Delta<String>, originalLines, newLines))
            }
            return result
        }
        
        private fun convertDeltaToDiffBlock(delta: Delta<String>, originalLines: List<String>, newLines: List<String>): DiffBlock {
            val originalChunk = delta.original
            val revisedChunk = delta.revised
            
            val startLine = originalChunk.position
            val endLine = startLine + originalChunk.lines.size
            
            val originalText = originalChunk.lines.joinToString("\n")
            val newText = revisedChunk.lines.joinToString("\n")
            
            return DiffBlock(
                originalText = originalText,
                newText = newText,
                startLine = startLine,
                endLine = endLine
            )
        }
    }
}