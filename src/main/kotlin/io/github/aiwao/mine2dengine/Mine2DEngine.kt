package io.github.aiwao.mine2dengine

import io.github.aiwao.mine2dengine.internal.render.Mine2DEffect
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectContext
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowContext
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextShadowContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
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

    /** Draws a filled rounded rectangle with four equal circular corners using [material]. */
    fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: Int,
    ) {
        roundedRect(x, y, width, height, Mine2DRoundedRectRadii(cornerRadius), color, material)
    }

    /** Draws a filled rounded rectangle with four equal circular corners. */
    fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        color: Int,
        material: Mine2DMaterial,
    ) {
        roundedRect(x, y, width, height, Mine2DRoundedRectRadii(cornerRadius), color, material)
    }

    /** Draws a filled rounded rectangle with independently elliptical [radii] using [material]. */
    fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: Mine2DRoundedRectRadii,
        color: Int,
    ) {
        roundedRect(x, y, width, height, radii, color, material)
    }

    /** Draws a filled rounded rectangle with independently elliptical [radii]. */
    fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: Mine2DRoundedRectRadii,
        color: Int,
        material: Mine2DMaterial,
    ) {
        enqueueRoundedRect(x, y, width, height, radii, color, material, uniformContext = null)
    }

    internal fun roundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: Mine2DRoundedRectRadii,
        color: Int,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext,
    ) {
        enqueueRoundedRect(x, y, width, height, radii, color, material, uniformContext)
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
        offsetY: Float = 0f,
        blurRadius: Float = 4f,
        spreadRadius: Float = 0f,
        cornerRadius: Float = 0f,
    ) {
        drawBoxShadow(
            geometry = calculateBoxShadowGeometry(
                x = x,
                y = y,
                width = width,
                height = height,
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
                spreadRadius = spreadRadius,
                cornerRadius = cornerRadius,
            ) ?: return,
            color = color,
            blurRadius = blurRadius,
        )
    }

    /** Draws a soft box shadow with independently elliptical [cornerRadii]. */
    @JvmOverloads
    fun boxShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadii: Mine2DRoundedRectRadii,
        color: Int = 0x80000000.toInt(),
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        blurRadius: Float = 4f,
        spreadRadius: Float = 0f,
    ) {
        drawBoxShadow(
            geometry = calculateBoxShadowGeometry(
                x = x,
                y = y,
                width = width,
                height = height,
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
                spreadRadius = spreadRadius,
                cornerRadii = cornerRadii,
            ) ?: return,
            color = color,
            blurRadius = blurRadius,
        )
    }

    private fun drawBoxShadow(
        geometry: Mine2DBoxShadowGeometry,
        color: Int,
        blurRadius: Float,
    ) {
        if (color ushr 24 == 0) return

        val material = Mine2DMaterials.boxShadow(
            color = color,
            width = geometry.shadowWidth,
            height = geometry.shadowHeight,
            blurRadius = blurRadius,
            cornerRadii = geometry.radii,
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

    /**
     * Draws only the shadow of [text], leaving the foreground text to a subsequent [text] call.
     *
     * The parameters match one CSS `text-shadow`: horizontal and vertical offsets may be negative,
     * while the optional blur radius must be non-negative. Blur is evaluated from the glyph alpha
     * by the text-shadow shader rather than by drawing displaced copies of the text. Coordinates,
     * offsets, and blur use GUI units and follow the active pose and scissor rectangle.
     */
    @JvmOverloads
    fun textShadow(
        font: Mine2DFont,
        text: String,
        x: Float,
        y: Float,
        color: Int = 0x80000000.toInt(),
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        blurRadius: Float = 4f,
    ) {
        font.checkOpen()
        validateShadowParameters("Text shadow", offsetX, offsetY, blurRadius)
        val colorAlpha = color ushr 24
        if (text.isEmpty() || colorAlpha == 0) return

        if (blurRadius == 0f) {
            enqueueText(
                font = font,
                text = text,
                x = x,
                y = y,
                color = color,
                offsetX = offsetX,
                offsetY = offsetY,
            )
            return
        }

        Mine2DTextShadowContext.begin(blurRadius).use {
            enqueueText(
                font = font,
                text = text,
                x = x,
                y = y,
                color = color,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }

    /** Draws [text] with a loaded TrueType [font]. */
    fun text(
        font: Mine2DFont,
        text: String,
        x: Float,
        y: Float,
        color: Int,
    ) {
        font.checkOpen()
        enqueueText(font, text, x, y, color, offsetX = 0f, offsetY = 0f)
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

    /** Clips all GUI draws extracted by [draw] to a transformed rounded rectangle. */
    fun withRoundedClip(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadius: Float,
        draw: Mine2DEngine.() -> Unit,
    ) {
        withRoundedClip(x, y, width, height, Mine2DRoundedRectRadii(cornerRadius), draw)
    }

    /** Clips all GUI draws extracted by [draw] to independently elliptical [radii]. */
    fun withRoundedClip(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: Mine2DRoundedRectRadii,
        draw: Mine2DEngine.() -> Unit,
    ) {
        require(x.isFinite() && y.isFinite()) { "Rounded clip coordinates must be finite" }
        require(width.isFinite() && width >= 0f && height.isFinite() && height >= 0f) {
            "Rounded clip dimensions must be finite and non-negative"
        }
        val pose = Matrix3x2f(graphics.pose())
        if (width == 0f || height == 0f || pose.determinant() == 0f) {
            val emptyEffect = Mine2DEffectContext.nextEffect(Mine2DEffect.Kind.ROUNDED_CLIP)
            Mine2DEffectContext.beginEffect(emptyEffect).use { draw() }
            return
        }
        val inverse = Matrix3x2f(pose).invert()
        val usedRadii = radii.normalized(width, height)
        val corners = listOf(
            pose.transformPosition(x, y, Vector2f()),
            pose.transformPosition(x + width, y, Vector2f()),
            pose.transformPosition(x + width, y + height, Vector2f()),
            pose.transformPosition(x, y + height, Vector2f()),
        )
        val effect = Mine2DEffectContext.nextEffect(Mine2DEffect.Kind.ROUNDED_CLIP)
        val material = Mine2DMaterials.roundedClip(
            left = x,
            top = y,
            width = width,
            height = height,
            radii = usedRadii,
            screenToLocalX = Vector4f(
                inverse.m00(),
                inverse.m10(),
                inverse.m20(),
                0f,
            ),
            screenToLocalY = Vector4f(
                inverse.m01(),
                inverse.m11(),
                inverse.m21(),
                0f,
            ),
            viewportWidth = graphics.guiWidth().toFloat(),
            viewportHeight = graphics.guiHeight().toFloat(),
        )

        Mine2DEffectContext.beginEffect(effect).use {
            draw()
        }
        graphics.guiRenderState.addGuiElement(
            RoundedClipRenderState(
                effect = effect,
                corners = corners,
                bindings = material.resolveBindings(
                    uniformContext(
                        elementBounds = Mine2DUniformRect(x, y, width, height),
                        contentBounds = Mine2DUniformRect(x, y, width, height),
                        timeSeconds = uniformTimeSeconds(),
                    ),
                ),
                scissor = graphics.scissorStack.peek(),
                dropShadowGroups = Mine2DDropShadowContext.currentGroups(),
            ),
        )
    }

    internal fun uniformTimeSeconds(): Float = Mine2DClock.seconds()

    /** Aligns only the transformed vertical text origin to the framebuffer pixel grid. */
    internal fun pixelAlignedTextOriginY(x: Float, y: Float): Vector2f {
        val guiHeight = graphics.guiHeight()
        val framebufferHeight = Minecraft.getInstance().window.height
        if (guiHeight <= 0 || framebufferHeight <= 0) return Vector2f(x, y)

        return alignTextOriginYToPixelGrid(
            x = x,
            y = y,
            pose = graphics.pose(),
            pixelsPerGuiUnitY = framebufferHeight.toFloat() / guiHeight,
        )
    }

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

    /** Applies one CSS drop-shadow operation to all GUI draws extracted by [draw]. */
    internal fun withDropShadow(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int,
        offsetX: Float,
        offsetY: Float,
        blurRadius: Float,
        draw: Mine2DEngine.() -> Unit,
    ) {
        validateShadowParameters("Drop shadow", offsetX, offsetY, blurRadius)
        if (color ushr 24 == 0 || width <= 0f || height <= 0f) {
            draw()
            return
        }

        val pose = Matrix3x2f(graphics.pose())
        val transformedCorners = listOf(
            pose.transformPosition(x, y, Vector2f()),
            pose.transformPosition(x + width, y, Vector2f()),
            pose.transformPosition(x + width, y + height, Vector2f()),
            pose.transformPosition(x, y + height, Vector2f()),
        )
        val transformedOffsetX = pose.m00() * offsetX + pose.m10() * offsetY
        val transformedOffsetY = pose.m01() * offsetX + pose.m11() * offsetY
        val blurAxisXx = pose.m00() * blurRadius
        val blurAxisXy = pose.m01() * blurRadius
        val blurAxisYx = pose.m10() * blurRadius
        val blurAxisYy = pose.m11() * blurRadius
        val blurExtentX = kotlin.math.abs(blurAxisXx) + kotlin.math.abs(blurAxisYx)
        val blurExtentY = kotlin.math.abs(blurAxisXy) + kotlin.math.abs(blurAxisYy)

        val groupId = Mine2DDropShadowContext.nextGroupId()
        val material = Mine2DMaterials.dropShadow(
            color = color,
            offsetX = transformedOffsetX,
            offsetY = transformedOffsetY,
            viewportWidth = graphics.guiWidth().toFloat(),
            viewportHeight = graphics.guiHeight().toFloat(),
            blurAxisXx = blurAxisXx,
            blurAxisXy = blurAxisXy,
            blurAxisYx = blurAxisYx,
            blurAxisYy = blurAxisYy,
            blurRadius = blurRadius,
        )
        graphics.guiRenderState.addGuiElement(
            DropShadowRenderState(
                groupId = groupId,
                outerGroups = Mine2DDropShadowContext.currentGroups(),
                bindings = material.resolveBindings(
                    uniformContext(
                        elementBounds = Mine2DUniformRect(x, y, width, height),
                        contentBounds = Mine2DUniformRect(x, y, width, height),
                        timeSeconds = uniformTimeSeconds(),
                    ),
                ),
                left = transformedCorners.minOf { corner -> corner.x() } +
                    transformedOffsetX - blurExtentX,
                top = transformedCorners.minOf { corner -> corner.y() } +
                    transformedOffsetY - blurExtentY,
                right = transformedCorners.maxOf { corner -> corner.x() } +
                    transformedOffsetX + blurExtentX,
                bottom = transformedCorners.maxOf { corner -> corner.y() } +
                    transformedOffsetY + blurExtentY,
                scissor = graphics.scissorStack.peek(),
            ),
        )

        Mine2DDropShadowContext.beginGroup(groupId).use {
            draw()
        }
    }

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

    private fun enqueueRoundedRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: Mine2DRoundedRectRadii,
        color: Int,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext?,
    ) {
        val polygon = triangulateRoundedRect(x, y, width, height, radii, color) ?: return
        enqueuePolygon(polygon, material, uniformContext)
    }

    private fun enqueuePolygon(
        vertices: Iterable<Mine2DVertex>,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext?,
    ) {
        enqueuePolygon(PolygonTriangulator.triangulate(vertices), material, uniformContext)
    }

    private fun enqueuePolygon(
        polygon: TriangulatedPolygon,
        material: Mine2DMaterial,
        uniformContext: Mine2DUniformContext?,
    ) {
        val context = uniformContext ?: defaultUniformContext(polygon)
        graphics.guiRenderState.addGuiElement(
            PolygonRenderState(
                shader = material.shader,
                bindings = material.resolveBindings(context),
                pose = Matrix3x2f(graphics.pose()),
                polygon = polygon,
                scissor = graphics.scissorStack.peek(),
                dropShadowGroups = Mine2DDropShadowContext.currentGroups(),
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

    private fun enqueueText(
        font: Mine2DFont,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        offsetX: Float,
        offsetY: Float,
    ) {
        require(x.isFinite() && y.isFinite()) { "Text coordinates must be finite" }
        val translatedX = x + offsetX
        val translatedY = y + offsetY
        require(translatedX.isFinite() && translatedY.isFinite()) {
            "Translated text coordinates must be finite"
        }

        val integerX = translatedX.toInt()
        val integerY = translatedY.toInt()
        if (translatedX == integerX.toFloat() && translatedY == integerY.toFloat()) {
            graphics.text(font.renderer, text, integerX, integerY, color, false)
            return
        }

        val pose = graphics.pose()
        pose.pushMatrix()
        try {
            pose.translate(translatedX, translatedY)
            graphics.text(font.renderer, text, 0, 0, color, false)
        } finally {
            pose.popMatrix()
        }
    }
}

internal fun alignTextOriginYToPixelGrid(
    x: Float,
    y: Float,
    pose: Matrix3x2fc,
    pixelsPerGuiUnitY: Float,
): Vector2f {
    val original = Vector2f(x, y)
    if (!pixelsPerGuiUnitY.isFinite() || pixelsPerGuiUnitY <= 0f) return original
    val determinant = pose.determinant()
    if (!determinant.isFinite() || determinant == 0f) return original

    val transformed = pose.transformPosition(x, y, Vector2f())
    if (!transformed.y.isFinite()) return original
    val physicalY = transformed.y * pixelsPerGuiUnitY
    if (!physicalY.isFinite()) return original
    transformed.y = round(physicalY) / pixelsPerGuiUnitY

    return pose.invert(Matrix3x2f()).transformPosition(transformed, Vector2f())
}
