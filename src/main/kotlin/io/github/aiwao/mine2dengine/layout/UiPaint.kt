package io.github.aiwao.mine2dengine.layout

import io.github.aiwao.mine2dengine.Mine2DMaterial

/**
 * Paints an element's bounds with a vertex [color] and optional [material].
 *
 * A null material uses the [io.github.aiwao.mine2dengine.Mine2DEngine.material] active when the
 * layout is rendered. Background paint is local to one element and is never inherited by children.
 */
data class UiPaint(
    val color: Int = 0xFFFFFFFF.toInt(),
    val material: Mine2DMaterial? = null,
)
