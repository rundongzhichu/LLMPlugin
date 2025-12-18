package org.demo.llmplugin.util

import difflib.DiffUtils
import difflib.Patch

data class DiffBlock(
    val originalLines: List<String>,
    val newLines: List<String>,
    val startLineInOriginal: Int // 从 0 开始
)

object CodeDiffer {
    fun computeDiff(original: String, revised: String): List<DiffBlock> {
        val origLines = original.lines()
        val revLines = revised.lines()

        val patch: Patch<String> = DiffUtils.diff(origLines, revLines)
        val deltas = patch.deltas

        return deltas.map { delta ->
            DiffBlock(
                originalLines = delta.original.lines,
                newLines = delta.revised.lines,
                startLineInOriginal = delta.original.position
            )
        }
    }
}

