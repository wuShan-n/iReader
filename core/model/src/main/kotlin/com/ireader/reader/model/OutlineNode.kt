package com.ireader.reader.model

data class OutlineNode(
    val title: String,
    val locator: Locator,
    val children: List<OutlineNode> = emptyList(),
)
