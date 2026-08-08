package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowCompositeRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DDropShadowMemberRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DMaterialRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f

/** Draws a prepared full-frame alpha mask through the drop-shadow compositor. */
internal class DropShadowRenderState(
    private val groupId: Long,
    private val outerGroups: List<Long>,
    private val bindings: Mine2DRenderBindings,
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float,
    private val scissor: ScreenRectangle?,
) : GuiElementRenderState,
    Mine2DMaterialRenderState,
    Mine2DDropShadowMemberRenderState,
    Mine2DDropShadowCompositeRenderState {
    private val pose = Matrix3x2f()
    private val boundsLeft = kotlin.math.floor(left).toInt()
    private val boundsTop = kotlin.math.floor(top).toInt()
    private val bounds = ScreenRectangle(
        boundsLeft,
        boundsTop,
        kotlin.math.ceil(right).toInt() - boundsLeft,
        kotlin.math.ceil(bottom).toInt() - boundsTop,
    ).let { rectangle -> scissor?.intersection(rectangle) ?: rectangle }

    override fun buildVertices(vertexConsumer: VertexConsumer) {
        addVertex(vertexConsumer, left, top)
        addVertex(vertexConsumer, right, top)
        addVertex(vertexConsumer, right, bottom)
        addVertex(vertexConsumer, left, top)
        addVertex(vertexConsumer, right, bottom)
        addVertex(vertexConsumer, left, bottom)
    }

    override fun pipeline(): RenderPipeline = Mine2DShaders.DROP_SHADOW.pipeline

    override fun textureSetup(): TextureSetup = TextureSetup.noTexture()

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? = bounds

    override fun mine2dengineBindings(): Mine2DRenderBindings = bindings

    override fun mine2dengineDropShadowGroups(): List<Long> = outerGroups

    override fun mine2dengineDropShadowGroup(): Long = groupId

    private fun addVertex(vertexConsumer: VertexConsumer, x: Float, y: Float) {
        vertexConsumer.addVertexWith2DPose(pose, x, y).setColor(-1)
    }
}
