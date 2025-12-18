package org.demo.llmplugin.util

import kotlin.math.min

/**
 * 简单的差异比对工具类
 */
class DiffUtils {
    
    companion object {
        /**
         * 比较两个字符串列表，返回差异块信息
         */
        fun computeDiffBlocks(originalLines: List<String>, newLines: List<String>): List<DiffBlock> {
            val blocks = mutableListOf<DiffBlock>()
            
            // 使用最长公共子序列算法的简化版本
            val maxLines = maxOf(originalLines.size, newLines.size)
            
            var i = 0
            while (i < maxLines) {
                val originalLine = if (i < originalLines.size) originalLines[i] else ""
                val newLine = if (i < newLines.size) newLines[i] else ""
                
                val startLine = i
                var isSame = originalLine == newLine
                
                // 找到连续相同或不同的行
                var j = i
                while (j < maxLines) {
                    val nextOriginalLine = if (j < originalLines.size) originalLines[j] else ""
                    val nextNewLine = if (j < newLines.size) newLines[j] else ""
                    val nextIsSame = nextOriginalLine == nextNewLine
                    
                    if (nextIsSame != isSame) break
                    j++
                }
                
                val endLine = j - 1
                
                // 构造块内容
                val originalContent = if (startLine < originalLines.size) {
                    originalLines.subList(startLine, min(endLine + 1, originalLines.size)).joinToString("\n")
                } else ""
                
                val newContent = if (startLine < newLines.size) {
                    newLines.subList(startLine, min(endLine + 1, newLines.size)).joinToString("\n")
                } else ""
                
                blocks.add(DiffBlock(
                    type = if (isSame) DiffType.SAME else DiffType.DIFFERENT,
                    startLine = startLine,
                    endLine = endLine,
                    originalContent = originalContent,
                    newContent = newContent
                ))
                
                i = j
            }
            
            return blocks
        }
    }
}

/**
 * 表示一个差异块的数据类
 *
 * @param type 差异类型（相同或不同）
 * @param startLine 开始行号
 * @param endLine 结束行号
 * @param originalContent 原始内容
 * @param newContent 新内容
 */
data class DiffBlock(
    val type: DiffType,
    val startLine: Int,
    val endLine: Int,
    val originalContent: String,
    val newContent: String
)

/**
 * 差异类型枚举
 */
enum class DiffType {
    SAME,      // 相同的代码块
    DIFFERENT  // 不同的代码块
}