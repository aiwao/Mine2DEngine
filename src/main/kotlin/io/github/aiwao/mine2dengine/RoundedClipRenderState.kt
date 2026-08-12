package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffect
import io.github.aiwao.mine2dengine.internal.render.Mine2DEffectCompositeRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DMaterialRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Vector2f
import org.joml.Vector2fc
import kotlin.math.ceil
import kotlin.math.floor

/** Composites one full-frame GUI layer through an analytic rounded-rectangle mask. */
internal class RoundedClipRenderState(
    private val effect: Mine2DEffect,
    corners: List<Vector2fc>,
    private val bindings: Mine2DRenderBindings,
    private val scissor: ScreenRectangle?,
) : GuiElementRenderState,
    Mine2DMaterialRenderState,
    Mine2DEffectCompositeRenderState {
    private val pose = Matrix3x2f()
    private val corners = corners.map(::Vector2f)
    private val bounds = calculateBounds(this.corners, scissor)

    init {
        require(this.corners.size == 4) { "A rounded clip requires four transformed corners" }
    }

    override fun buildVertices(vertexConsumer: VertexConsumer) {
        addVertex(vertexConsumer, corners[0])
        addVertex(vertexConsumer, corners[1])
        addVertex(vertexConsumer, corners[2])
        addVertex(vertexConsumer, corners[0])
        addVertex(vertexConsumer, corners[2])
        addVertex(vertexConsumer, corners[3])
    }

    override fun pipeline(): RenderPipeline = Mine2DShaders.ROUNDED_CLIP.pipeline

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? = bounds

    override fun mine2dengineBindings(): Mine2DRenderBindings = bindings

    override fun mine2dengineEffect(): Mine2DEffect = effect

    private fun addVertex(vertexConsumer: VertexConsumer, point: Vector2fc) {
        vertexConsumer.addVertexWith2DPose(pose, point.x(), point.y()).setColor(-1)
    }

    private companion object {
        fun calculateBounds(
            corners: List<Vector2f>,
            scissor: ScreenRectangle?,
        ): ScreenRectangle? {
            if (corners.isEmpty()) return null
            val left = floor(corners.minOf { point -> point.x() }).toInt()
            val top = floor(corners.minOf { point -> point.y() }).toInt()
            val right = ceil(corners.maxOf { point -> point.x() }).toInt()
            val bottom = ceil(corners.maxOf { point -> point.y() }).toInt()
            if (right <= left || bottom <= top) return null
            val rectangle = ScreenRectangle(left, top, right - left, bottom - top)
            return scissor?.intersection(rectangle) ?: rectangle
        }
    }
}
