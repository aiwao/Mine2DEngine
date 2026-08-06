package io.github.aiwao.mine2dengine

import net.minecraft.client.gui.GuiGraphicsExtractor
import org.joml.Matrix3x2f
import org.joml.Vector2fc

/**
 * Immediate-style 2D drawing API backed by Minecraft's extracted GUI render state.
 *
 * Calls enqueue immutable render states for the current frame. Layout is
 * intentionally outside this class; coordinates are interpreted in the current
 * [GuiGraphicsExtractor.pose] and respect its active scissor rectangle.
 */
class Mine2DEngine(
    val graphics: GuiGraphicsExtractor,
) {
    companion object {
        /** Registers built-in pipelines. Call once during mod initialization. */
        @JvmStatic
        fun initialize() {
            Mine2DShaders.initialize()
        }
    }

    /** Shader used by polygon calls that do not supply one explicitly. */
    var shader: Mine2DShader = Mine2DShaders.COLOR

    /** Draws a convex or concave simple polygon using [shader]. */
    fun polygon(vertices: Iterable<Mine2DVertex>) {
        polygon(vertices, shader)
    }

    /** Draws a convex or concave simple polygon using [shader]. */
    fun polygon(vararg vertices: Mine2DVertex) {
        polygon(vertices.asIterable(), shader)
    }

    /** Draws a convex or concave simple polygon with an explicit [shader]. */
    fun polygon(vertices: Iterable<Mine2DVertex>, shader: Mine2DShader) {
        val polygon = PolygonTriangulator.triangulate(vertices)
        graphics.guiRenderState.addGuiElement(
            PolygonRenderState(
                shader = shader,
                pose = Matrix3x2f(graphics.pose()),
                polygon = polygon,
                scissor = graphics.scissorStack.peek(),
            ),
        )
    }

    /** Convenience overload for a single-color polygon. */
    fun polygon(color: Int, points: Iterable<Vector2fc>) {
        polygon(points.map { point -> Mine2DVertex(point.x(), point.y(), color) })
    }

    /** Convenience overload for a single-color polygon. */
    fun polygon(color: Int, vararg points: Vector2fc) {
        polygon(color, points.asIterable())
    }

    /** Draws a rectangle with the given bounds using [shader]. */
    fun quad(x: Float, y: Float, width: Float, height: Float, color: Int) {
        polygon(
            Mine2DVertex(x, y, color),
            Mine2DVertex(x + width, y, color),
            Mine2DVertex(x + width, y + height, color),
            Mine2DVertex(x, y + height, color),
        )
    }

    /**
     * Temporarily changes the default shader. Nested scopes are supported and
     * the previous shader is restored even if [draw] throws.
     */
    fun withShader(shader: Mine2DShader, draw: Mine2DEngine.() -> Unit) {
        val previous = this.shader
        this.shader = shader
        try {
            draw()
        } finally {
            this.shader = previous
        }
    }
}
