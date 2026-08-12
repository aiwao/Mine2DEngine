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
    val radii: Mine2DRoundedRectRadii,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    /** Compatibility view for callers using the equal circular-radius overload. */
    val cornerRadius: Float
        get() = radii.topLeft.horizontal
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
): Mine2DBoxShadowGeometry? = calculateBoxShadowGeometry(
    x = x,
    y = y,
    width = width,
    height = height,
    offsetX = offsetX,
    offsetY = offsetY,
    blurRadius = blurRadius,
    spreadRadius = spreadRadius,
    cornerRadii = Mine2DRoundedRectRadii(cornerRadius),
)

internal fun calculateBoxShadowGeometry(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    offsetX: Float,
    offsetY: Float,
    blurRadius: Float,
    spreadRadius: Float,
    cornerRadii: Mine2DRoundedRectRadii,
): Mine2DBoxShadowGeometry? {
    require(x.isFinite() && y.isFinite()) { "Box shadow coordinates must be finite" }
    require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
        "Box shadow dimensions must be finite and non-negative"
    }
    validateShadowParameters("Box shadow", offsetX, offsetY, blurRadius)
    require(spreadRadius.isFinite()) { "Box shadow spread radius must be finite" }
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
    val effectiveRadii = cornerRadii
        .normalized(width, height)
        .outset(spreadRadius, shadowWidth, shadowHeight)
    val right = left + drawWidth
    val bottom = top + drawHeight
    require(
        left.isFinite() && top.isFinite() &&
            drawWidth.isFinite() && drawHeight.isFinite() &&
            right.isFinite() && bottom.isFinite(),
    ) { "Box shadow parameters produced non-finite geometry" }

    return Mine2DBoxShadowGeometry(
        left = left,
        top = top,
        width = drawWidth,
        height = drawHeight,
        shadowWidth = shadowWidth,
        shadowHeight = shadowHeight,
        radii = effectiveRadii,
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
    /** UV deltas for one GUI unit along X, followed by one GUI unit along Y. */
    val uPerGuiX: Float,
    val vPerGuiX: Float,
    val uPerGuiY: Float,
    val vPerGuiY: Float,
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
    require(
        vertices.all { vertex ->
            vertex.x.isFinite() && vertex.y.isFinite() && vertex.z.isFinite() &&
                vertex.u.isFinite() && vertex.v.isFinite()
        },
    ) { "Text shadow glyph vertices must be finite" }

    val minU = vertices.minOf(Mine2DTextShadowVertex::u)
    val minV = vertices.minOf(Mine2DTextShadowVertex::v)
    val maxU = vertices.maxOf(Mine2DTextShadowVertex::u)
    val maxV = vertices.maxOf(Mine2DTextShadowVertex::v)
    val firstQuad = vertices.take(TEXT_SHADOW_QUAD_VERTEX_COUNT)
    val minX = firstQuad.minOf(Mine2DTextShadowVertex::x)
    val minY = firstQuad.minOf(Mine2DTextShadowVertex::y)
    val maxX = firstQuad.maxOf(Mine2DTextShadowVertex::x)
    val maxY = firstQuad.maxOf(Mine2DTextShadowVertex::y)
    val glyphWidth = maxX - minX
    val glyphHeight = maxY - minY
    val uRange = maxU - minU
    val vRange = maxV - minV
    require(
        glyphWidth.isFinite() && glyphWidth > 0f &&
            glyphHeight.isFinite() && glyphHeight > 0f &&
            uRange.isFinite() && uRange > 0f &&
            vRange.isFinite() && vRange > 0f,
    ) { "Text shadow glyph must have finite, non-empty position and UV bounds" }

    val leftVertices = firstQuad.filter { vertex -> closestToMinimum(vertex.x, minX, maxX) }
    val rightVertices = firstQuad.filterNot { vertex -> closestToMinimum(vertex.x, minX, maxX) }
    val topVertices = firstQuad.filter { vertex -> closestToMinimum(vertex.y, minY, maxY) }
    val bottomVertices = firstQuad.filterNot { vertex -> closestToMinimum(vertex.y, minY, maxY) }
    val uPerGuiX = (rightVertices.averageU() - leftVertices.averageU()) / glyphWidth
    val vPerGuiX = (rightVertices.averageV() - leftVertices.averageV()) / glyphWidth
    val uPerGuiY = (bottomVertices.averageU() - topVertices.averageU()) / glyphHeight
    val vPerGuiY = (bottomVertices.averageV() - topVertices.averageV()) / glyphHeight
    val uvDeterminant = uPerGuiX * vPerGuiY - uPerGuiY * vPerGuiX
    require(
        uPerGuiX.isFinite() && vPerGuiX.isFinite() &&
            uPerGuiY.isFinite() && vPerGuiY.isFinite() &&
            uvDeterminant.isFinite() && uvDeterminant != 0f,
    ) { "Text shadow glyph position-to-UV mapping must be finite and non-degenerate" }

    val expanded = vertices.chunked(TEXT_SHADOW_QUAD_VERTEX_COUNT).flatMap { quad ->
        val quadMinX = quad.minOf(Mine2DTextShadowVertex::x)
        val quadMinY = quad.minOf(Mine2DTextShadowVertex::y)
        val quadMaxX = quad.maxOf(Mine2DTextShadowVertex::x)
        val quadMaxY = quad.maxOf(Mine2DTextShadowVertex::y)
        require(quadMinX < quadMaxX && quadMinY < quadMaxY) {
            "Text shadow glyph quads must have non-empty position bounds"
        }

        quad.map { vertex ->
            val horizontalDirection =
                if (closestToMinimum(vertex.x, quadMinX, quadMaxX)) -1f else 1f
            val verticalDirection =
                if (closestToMinimum(vertex.y, quadMinY, quadMaxY)) -1f else 1f
            vertex.copy(
                x = vertex.x + horizontalDirection * blurRadius,
                y = vertex.y + verticalDirection * blurRadius,
                u = vertex.u +
                    horizontalDirection * uPerGuiX * blurRadius +
                    verticalDirection * uPerGuiY * blurRadius,
                v = vertex.v +
                    horizontalDirection * vPerGuiX * blurRadius +
                    verticalDirection * vPerGuiY * blurRadius,
            )
        }
    }

    return Mine2DTextShadowGlyphGeometry(
        vertices = expanded,
        minU = minU,
        minV = minV,
        maxU = maxU,
        maxV = maxV,
        uPerGuiX = uPerGuiX,
        vPerGuiX = vPerGuiX,
        uPerGuiY = uPerGuiY,
        vPerGuiY = vPerGuiY,
    )
}

private fun List<Mine2DTextShadowVertex>.averageU(): Float =
    map(Mine2DTextShadowVertex::u).average().toFloat()

private fun List<Mine2DTextShadowVertex>.averageV(): Float =
    map(Mine2DTextShadowVertex::v).average().toFloat()

private fun closestToMinimum(value: Float, minimum: Float, maximum: Float): Boolean =
    abs(value - minimum) <= abs(value - maximum)

private const val TEXT_SHADOW_QUAD_VERTEX_COUNT = 4
