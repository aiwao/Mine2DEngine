package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import org.joml.Vector2f
import kotlin.math.ceil
import kotlin.math.floor

internal class PolygonRenderState(
    private val shader: Render2DShader,
    private val pose: Matrix3x2fc,
    private val polygon: TriangulatedPolygon,
    private val scissor: ScreenRectangle?,
) : GuiElementRenderState {
    private val bounds = calculateBounds(pose, polygon.vertices, scissor)

    override fun buildVertices(vertexConsumer: VertexConsumer) {
        for (index in polygon.indices) {
            val vertex = polygon.vertices[index]
            vertexConsumer
                .addVertexWith2DPose(pose, vertex.x, vertex.y)
                .setColor(vertex.color)
        }
    }

    override fun pipeline(): RenderPipeline = shader.pipeline

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? = bounds

    private companion object {
        fun calculateBounds(
            pose: Matrix3x2fc,
            vertices: List<Render2DVertex>,
            scissor: ScreenRectangle?,
        ): ScreenRectangle? {
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            val transformed = Vector2f()

            for (vertex in vertices) {
                pose.transformPosition(vertex.x, vertex.y, transformed)
                minX = minOf(minX, transformed.x)
                minY = minOf(minY, transformed.y)
                maxX = maxOf(maxX, transformed.x)
                maxY = maxOf(maxY, transformed.y)
            }

            val left = floor(minX).toInt()
            val top = floor(minY).toInt()
            val right = ceil(maxX).toInt()
            val bottom = ceil(maxY).toInt()
            if (right <= left || bottom <= top) return null

            val polygonBounds = ScreenRectangle(left, top, right - left, bottom - top)
            return if (scissor != null) scissor.intersection(polygonBounds) else polygonBounds
        }
    }
}
