package io.github.aiwao.mine2dengine

/**
 * A vertex consumed by [Mine2DEngine].
 *
 * [color] uses Minecraft's ARGB integer format.
 */
data class Render2DVertex @JvmOverloads constructor(
    val x: Float,
    val y: Float,
    val color: Int = 0xFFFFFFFF.toInt(),
)
