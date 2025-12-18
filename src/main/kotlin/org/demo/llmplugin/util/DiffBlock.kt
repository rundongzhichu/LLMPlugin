package org.demo.llmplugin.util

data class DiffBlock(
    val originalText: String,
    val newText: String,
    val startLine: Int,
    val endLine: Int
)