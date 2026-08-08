package io.github.aiwao.mine2dengine

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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

internal data class Mine2DTextShadowSample(
    val offsetX: Float,
    val offsetY: Float,
    val alpha: Int,
)

internal fun calculateTextShadowSamples(
    blurRadius: Float,
    colorAlpha: Int,
): List<Mine2DTextShadowSample> {
    require(blurRadius.isFinite() && blurRadius >= 0f) {
        "Text shadow blur radius must be finite and non-negative"
    }
    require(colorAlpha in 0..255) { "Text shadow alpha must be between 0 and 255" }
    if (colorAlpha == 0) return emptyList()
    if (blurRadius == 0f) return listOf(Mine2DTextShadowSample(0f, 0f, colorAlpha))

    val ringCount = ceil(blurRadius / 2f).toInt().coerceIn(1, MAX_TEXT_SHADOW_RINGS)
    val weightedSamples = buildList {
        add(WeightedTextShadowSample(0f, 0f, 1.0))
        for (ring in 1..ringCount) {
            val normalizedRadius = ring.toDouble() / ringCount
            val radius = blurRadius * normalizedRadius.toFloat()
            val sampleCount = ring * 4
            val weight = exp(-2.0 * normalizedRadius * normalizedRadius)
            val angleOffset = if (ring % 2 == 0) PI / sampleCount else 0.0
            repeat(sampleCount) { index ->
                val angle = 2.0 * PI * index / sampleCount + angleOffset
                add(
                    WeightedTextShadowSample(
                        offsetX = cos(angle).toFloat() * radius,
                        offsetY = sin(angle).toFloat() * radius,
                        weight = weight,
                    ),
                )
            }
        }
    }
    val weightSum = weightedSamples.sumOf(WeightedTextShadowSample::weight)
    // Avoid assigning fully opaque alpha to every displaced sample when the requested alpha is 255.
    val targetAlpha = minOf(colorAlpha / 255.0, 254.0 / 255.0)

    val samples = weightedSamples.mapNotNull { sample ->
        val normalizedWeight = sample.weight / weightSum
        val alpha = ((1.0 - (1.0 - targetAlpha).pow(normalizedWeight)) * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
        if (alpha == 0) {
            null
        } else {
            Mine2DTextShadowSample(sample.offsetX, sample.offsetY, alpha)
        }
    }
    return samples.ifEmpty { listOf(Mine2DTextShadowSample(0f, 0f, colorAlpha)) }
}

internal fun Int.withAlpha(alpha: Int): Int =
    (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private data class WeightedTextShadowSample(
    val offsetX: Float,
    val offsetY: Float,
    val weight: Double,
)

private const val MAX_TEXT_SHADOW_RINGS = 3
