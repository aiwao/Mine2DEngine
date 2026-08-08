package io.github.aiwao.mine2dengine

import kotlin.math.abs

internal fun validateShadowParameters(
    label: String,
    offsetX: Float,
    offsetY: Float,
    blurRadius: Float,
) {
    require(offsetX.isFinite() && offsetY.isFinite()) { "$label offsets must be finite" }
    require(blurRadius.isFinite() && blurRadius >= 0f) {
        "$label blur radius must be finite and non-negative"
    }
    require(
        (offsetX - blurRadius).isFinite() && (offsetX + blurRadius).isFinite() &&
            (offsetY - blurRadius).isFinite() && (offsetY + blurRadius).isFinite(),
    ) { "$label parameters must produce finite offsets" }
}

internal data class Mine2DBoxShadowGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val shadowWidth: Float,
    val shadowHeight: Float,
    val cornerRadius: Float,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height
}

internal fun calculateBoxShadowGeometry(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    offsetX: Float,
    offsetY: Float,
    blurRadius: Float,
    spreadRadius: Float,
    cornerRadius: Float,
): Mine2DBoxShadowGeometry? {
    require(x.isFinite() && y.isFinite()) { "Box shadow coordinates must be finite" }
    require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
        "Box shadow dimensions must be finite and non-negative"
    }
    validateShadowParameters("Box shadow", offsetX, offsetY, blurRadius)
    require(spreadRadius.isFinite()) { "Box shadow spread radius must be finite" }
    require(cornerRadius.isFinite() && cornerRadius >= 0f) {
        "Box shadow corner radius must be finite and non-negative"
    }
    if (width == 0f || height == 0f) return null

    val shadowWidth = width + spreadRadius * 2f
    val shadowHeight = height + spreadRadius * 2f
    require(shadowWidth.isFinite() && shadowHeight.isFinite()) {
        "Box shadow spread produced non-finite dimensions"
    }
    if (shadowWidth <= 0f || shadowHeight <= 0f) return null

    val left = x + offsetX - spreadRadius - blurRadius
    val top = y + offsetY - spreadRadius - blurRadius
    val drawWidth = shadowWidth + blurRadius * 2f
    val drawHeight = shadowHeight + blurRadius * 2f
    val effectiveCornerRadius = maxOf(0f, cornerRadius + spreadRadius)
    val right = left + drawWidth
    val bottom = top + drawHeight
    require(
        left.isFinite() && top.isFinite() &&
            drawWidth.isFinite() && drawHeight.isFinite() &&
            right.isFinite() && bottom.isFinite() && effectiveCornerRadius.isFinite(),
    ) { "Box shadow parameters produced non-finite geometry" }

    return Mine2DBoxShadowGeometry(
        left = left,
        top = top,
        width = drawWidth,
        height = drawHeight,
        shadowWidth = shadowWidth,
        shadowHeight = shadowHeight,
        cornerRadius = effectiveCornerRadius,
    )
}

internal data class Mine2DTextShadowVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val color: Int,
    val u: Float,
    val v: Float,
    val light: Int,
)

internal data class Mine2DTextShadowGlyphGeometry(
    val vertices: List<Mine2DTextShadowVertex>,
    val minU: Float,
    val minV: Float,
    val maxU: Float,
    val maxV: Float,
    val uPerGuiUnit: Float,
    val vPerGuiUnit: Float,
)

/** Expands glyph quads while preserving their atlas-to-GUI coordinate mapping for shader blur. */
internal fun calculateTextShadowGlyphGeometry(
    vertices: List<Mine2DTextShadowVertex>,
    blurRadius: Float,
): Mine2DTextShadowGlyphGeometry? {
    require(blurRadius.isFinite() && blurRadius >= 0f) {
        "Text shadow blur radius must be finite and non-negative"
    }
    require(vertices.size % TEXT_SHADOW_QUAD_VERTEX_COUNT == 0) {
        "Text shadow glyph geometry must contain complete quads"
    }
    if (vertices.isEmpty()) return null

    val minU = vertices.minOf(Mine2DTextShadowVertex::u)
    val minV = vertices.minOf(Mine2DTextShadowVertex::v)
    val maxU = vertices.maxOf(Mine2DTextShadowVertex::u)
    val maxV = vertices.maxOf(Mine2DTextShadowVertex::v)
    val firstQuad = vertices.take(TEXT_SHADOW_QUAD_VERTEX_COUNT)
    val leftX = firstQuad.filter { vertex -> closestToMinimum(vertex.u, minU, maxU) }
        .map(Mine2DTextShadowVertex::x)
        .average()
    val rightX = firstQuad.filterNot { vertex -> closestToMinimum(vertex.u, minU, maxU) }
        .map(Mine2DTextShadowVertex::x)
        .average()
    val topY = firstQuad.filter { vertex -> closestToMinimum(vertex.v, minV, maxV) }
        .map(Mine2DTextShadowVertex::y)
        .average()
    val bottomY = firstQuad.filterNot { vertex -> closestToMinimum(vertex.v, minV, maxV) }
        .map(Mine2DTextShadowVertex::y)
        .average()
    val glyphWidth = abs(rightX - leftX).toFloat()
    val glyphHeight = abs(bottomY - topY).toFloat()
    val uRange = maxU - minU
    val vRange = maxV - minV
    require(
        glyphWidth > 0f && glyphHeight > 0f &&
            uRange.isFinite() && uRange > 0f && vRange.isFinite() && vRange > 0f,
    ) { "Text shadow glyph must have finite, non-empty position and UV bounds" }

    val uPerGuiUnit = uRange / glyphWidth
    val vPerGuiUnit = vRange / glyphHeight
    val expanded = vertices.map { vertex ->
        val horizontalDirection = if (closestToMinimum(vertex.u, minU, maxU)) -1f else 1f
        val verticalDirection = if (closestToMinimum(vertex.v, minV, maxV)) -1f else 1f
        vertex.copy(
            x = vertex.x + horizontalDirection * blurRadius,
            y = vertex.y + verticalDirection * blurRadius,
            u = vertex.u + horizontalDirection * uPerGuiUnit * blurRadius,
            v = vertex.v + verticalDirection * vPerGuiUnit * blurRadius,
        )
    }

    return Mine2DTextShadowGlyphGeometry(
        vertices = expanded,
        minU = minU,
        minV = minV,
        maxU = maxU,
        maxV = maxV,
        uPerGuiUnit = uPerGuiUnit,
        vPerGuiUnit = vPerGuiUnit,
    )
}

private fun closestToMinimum(value: Float, minimum: Float, maximum: Float): Boolean =
    abs(value - minimum) <= abs(value - maximum)

private const val TEXT_SHADOW_QUAD_VERTEX_COUNT = 4
