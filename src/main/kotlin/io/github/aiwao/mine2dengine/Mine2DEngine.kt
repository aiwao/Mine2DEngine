package io.github.aiwao.mine2dengine

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f
import org.joml.Vector2fc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.require

/**
 * Immediate-style 2D drawing API backed by Minecraft's extracted GUI render state.
 *
 * Calls enqueue immutable render states for the current frame. Coordinates are interpreted in the
 * current [GuiGraphicsExtractor.pose] and respect its active scissor rectangle. Polygon calls use
 * [material] unless an explicit material is supplied.
 */
class Mine2DEngine(
    val graphics: GuiGraphicsExtractor,
) {
    companion object {
        // The box-shadow vertex shader interprets red and green as normalized local coordinates.
        private const val SHADOW_UV_TOP_LEFT: Int = -16777216
        private const val SHADOW_UV_TOP_RIGHT: Int = -65536
        private const val SHADOW_UV_BOTTOM_RIGHT: Int = -256
        private const val SHADOW_UV_BOTTOM_LEFT: Int = -16711936

        /** Registers built-in pipelines. Call once during mod initialization. */
        @JvmStatic
        fun initialize() {
            Mine2DShaders.initialize()
        }
    }

    /** Material used by polygon calls that do not supply one explicitly. */
    var material: Mine2DMaterial = Mine2DMaterials.COLOR

    /** Draws a convex or concave simple polygon using [material]. */
    fun polygon(vertices: Iterable<Mine2DVertex>) {
        polygon(vertices, material)
    }

    /** Draws a convex or concave simple polygon using [material]. */
    fun polygon(vararg vertices: Mine2DVertex) {
        polygon(vertices.asIterable(), material)
    }

    /** Draws a convex or concave simple polygon with an explicit [material]. */
    fun polygon(vertices: Iterable<Mine2DVertex>, material: Mine2DMaterial) {
        enqueuePolygon(vertices, material, uniformContext = null)
    }

    /** Convenience overload for a single-color polygon. */
    fun polygon(color: Int, points: Iterable<Vector2fc>) {
        polygon(color, points, material)
    }

    /** Convenience overload for a single-color polygon with an explicit [material]. */
    fun polygon(color: Int, points: Iterable<Vector2fc>, material: Mine2DMaterial) {
        polygon(points.map { point -> Mine2DVertex(point.x(), point.y(), color) }, material)
    }

    /** Convenience overload for a single-color polygon. */
    fun polygon(color: Int, vararg points: Vector2fc) {
        polygon(color, points.asIterable(), material)
    }

    /** Draws a rectangle with the given bounds using [material]. */
    fun quad(x: Float, y: Float, width: Float, height: Float, color: Int) {
        quad(x, y, width, height, color, material)
    }

    /** Draws a rectangle with the given bounds using an explicit [material]. */
    fun quad(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        material: Mine2DMaterial,
    ) {
        enqueueQuad(x, y, width, height, color, material, uniformContext = null)
    }

    internal fun quad(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext,
    ) {
        enqueueQuad(x, y, width, height, color, material, uniformContext)
    }

    /** Draws a line segment of [width] as a filled quadrilateral with butt caps. */
    fun line(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        width: Float,
        color: Int,
    ) {
        line(startX, startY, endX, endY, width, color, material)
    }

    /** Draws a line segment with an explicit [material]. */
    fun line(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        width: Float,
        color: Int,
        material: Mine2DMaterial,
    ) {
        require(startX.isFinite() && startY.isFinite() && endX.isFinite() && endY.isFinite()) {
            "Line coordinates must be finite"
        }
        require(width.isFinite() && width > 0f) { "A line width must be finite and positive" }

        val deltaX = endX.toDouble() - startX
        val deltaY = endY.toDouble() - startY
        val length = hypot(deltaX, deltaY)
        require(length > 0.0) { "A line requires distinct start and end points" }

        val offsetScale = width / (2.0 * length)
        val offsetX = (-deltaY * offsetScale).toFloat()
        val offsetY = (deltaX * offsetScale).toFloat()

        polygon(
            listOf(
                Mine2DVertex(startX + offsetX, startY + offsetY, color),
                Mine2DVertex(endX + offsetX, endY + offsetY, color),
                Mine2DVertex(endX - offsetX, endY - offsetY, color),
                Mine2DVertex(startX - offsetX, startY - offsetY, color),
            ),
            material,
        )
    }

    /** Draws a filled circle approximated by a regular polygon using [material]. */
    fun circle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        segments: Int,
    ) {
        circle(centerX, centerY, radius, color, segments, material)
    }

    /** Draws a filled circle approximated by a regular polygon with an explicit [material]. */
    fun circle(
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        segments: Int,
        material: Mine2DMaterial,
    ) {
        require(centerX.isFinite() && centerY.isFinite()) { "Circle coordinates must be finite" }
        require(radius.isFinite() && radius > 0f) { "A circle radius must be finite and positive" }
        require(segments >= 3) { "A circle requires at least three segments" }

        polygon(
            List(segments) { index ->
                val angle = 2.0 * PI * index / segments
                Mine2DVertex(
                    x = centerX + cos(angle).toFloat() * radius,
                    y = centerY + sin(angle).toFloat() * radius,
                    color = color,
                )
            },
            material,
        )
    }

    /**
     * Draws a soft rounded-box shadow behind the supplied bounds.
     *
     * The shadow does not draw the box itself. [spreadRadius] grows or shrinks the shadow shape,
     * while [blurRadius] expands and softens its edge. A negative spread that collapses either
     * dimension produces no draw. Coordinates and radii use GUI units and follow the active pose
     * and scissor rectangle.
     */
    @JvmOverloads
    fun boxShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int = 0x80000000.toInt(),
        offsetX: Float = 0f,
        offsetY: Float = 2f,
        blurRadius: Float = 4f,
        spreadRadius: Float = 0f,
        cornerRadius: Float = 0f,
    ) {
        val geometry = calculateBoxShadowGeometry(
            x = x,
            y = y,
            width = width,
            height = height,
            offsetX = offsetX,
            offsetY = offsetY,
            blurRadius = blurRadius,
            spreadRadius = spreadRadius,
            cornerRadius = cornerRadius,
        ) ?: return
        if (color ushr 24 == 0) return

        val material = Mine2DMaterials.boxShadow(
            color = color,
            width = geometry.shadowWidth,
            height = geometry.shadowHeight,
            blurRadius = blurRadius,
            cornerRadius = geometry.cornerRadius,
        )
        enqueuePolygon(
            listOf(
                Mine2DVertex(geometry.left, geometry.top, SHADOW_UV_TOP_LEFT),
                Mine2DVertex(geometry.right, geometry.top, SHADOW_UV_TOP_RIGHT),
                Mine2DVertex(geometry.right, geometry.bottom, SHADOW_UV_BOTTOM_RIGHT),
                Mine2DVertex(geometry.left, geometry.bottom, SHADOW_UV_BOTTOM_LEFT),
            ),
            material,
            uniformContext = null,
        )
    }

    /** Draws [text] with a loaded TrueType [font]. */
    @JvmOverloads
    fun text(
        font: Mine2DFont,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        dropShadow: Boolean = false,
    ) {
        font.checkOpen()
        graphics.text(font.renderer, text, x, y, color, dropShadow)
    }

    /** Temporarily changes the default material and restores it after [draw]. */
    fun withMaterial(material: Mine2DMaterial, draw: Mine2DEngine.() -> Unit) {
        val previous = this.material
        this.material = material
        try {
            draw()
        } finally {
            this.material = previous
        }
    }

    internal fun uniformTimeSeconds(): Float = Mine2DClock.seconds()

    internal fun uniformContext(
        elementBounds: Mine2DUniformRect,
        contentBounds: Mine2DUniformRect,
        timeSeconds: Float,
    ): Mine2DUniformContext = Mine2DUniformContext(
        elementBounds = elementBounds,
        contentBounds = contentBounds,
        viewportWidth = graphics.guiWidth().toFloat(),
        viewportHeight = graphics.guiHeight().toFloat(),
        timeSeconds = timeSeconds,
    )

    private fun enqueueQuad(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext?,
    ) {
        enqueuePolygon(
            listOf(
                Mine2DVertex(x, y, color),
                Mine2DVertex(x + width, y, color),
                Mine2DVertex(x + width, y + height, color),
                Mine2DVertex(x, y + height, color),
            ),
            material,
            uniformContext,
        )
    }

    private fun enqueuePolygon(
        vertices: Iterable<Mine2DVertex>,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext?,
    ) {
        val polygon = PolygonTriangulator.triangulate(vertices)
        val context = uniformContext ?: defaultUniformContext(polygon)
        graphics.guiRenderState.addGuiElement(
            PolygonRenderState(
                shader = material.shader,
                bindings = material.resolveBindings(context),
                pose = Matrix3x2f(graphics.pose()),
                polygon = polygon,
                scissor = graphics.scissorStack.peek(),
            ),
        )
    }

    private fun defaultUniformContext(polygon: TriangulatedPolygon): Mine2DUniformContext {
        val minX = polygon.vertices.minOf(Mine2DVertex::x)
        val minY = polygon.vertices.minOf(Mine2DVertex::y)
        val maxX = polygon.vertices.maxOf(Mine2DVertex::x)
        val maxY = polygon.vertices.maxOf(Mine2DVertex::y)
        val bounds = Mine2DUniformRect(minX, minY, maxX - minX, maxY - minY)
        return uniformContext(bounds, bounds, uniformTimeSeconds())
    }
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
    require(offsetX.isFinite() && offsetY.isFinite()) { "Box shadow offsets must be finite" }
    require(blurRadius.isFinite() && blurRadius >= 0f) {
        "Box shadow blur radius must be finite and non-negative"
    }
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
