package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.aiwao.mine2dengine.internal.LinearTextTextures
import io.github.aiwao.mine2dengine.internal.render.Mine2DMaterialRenderState
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GlyphRenderState
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2fc
import org.joml.Matrix4f

/** A glyph quad enlarged for a single-pass, atlas-alpha text-shadow blur. */
internal class TextShadowGlyphRenderState private constructor(
    private val pose: Matrix3x2fc,
    private val renderable: TextRenderable,
    private val scissor: ScreenRectangle?,
    private val geometry: Mine2DTextShadowGlyphGeometry,
    private val bindings: Mine2DRenderBindings,
) : GuiElementRenderState, Mine2DMaterialRenderState {
    override fun buildVertices(vertexConsumer: VertexConsumer) {
        val poseMatrix = Matrix4f().mul(pose)
        geometry.vertices.forEach { vertex ->
            vertexConsumer
                .addVertex(poseMatrix, vertex.x, vertex.y, vertex.z)
                .setUv(vertex.u, vertex.v)
                .setColor(vertex.color)
        }
    }

    override fun pipeline(): RenderPipeline = Mine2DShaders.TEXT_SHADOW.pipeline

    override fun textureSetup(): TextureSetup {
        val textureView = renderable.textureView()
        val filterMode = if (LinearTextTextures.contains(textureView)) {
            FilterMode.LINEAR
        } else {
            FilterMode.NEAREST
        }
        return TextureSetup.singleTextureWithLightmap(
            textureView,
            RenderSystem.getSamplerCache().getClampToEdge(filterMode),
        )
    }

    override fun scissorArea(): ScreenRectangle? = scissor

    override fun bounds(): ScreenRectangle? = null

    override fun mine2dengineBindings(): Mine2DRenderBindings = bindings

    companion object {
        private const val FULL_BRIGHT_LIGHT = 0x00F000F0

        @JvmStatic
        fun create(
            original: GlyphRenderState,
            blurRadius: Float,
        ): GuiElementRenderState {
            val renderable = original.renderable()
            val recorder = GlyphVertexRecorder()
            renderable.render(Matrix4f(), recorder, FULL_BRIGHT_LIGHT, true)
            val geometry = calculateTextShadowGlyphGeometry(recorder.vertices, blurRadius)
                ?: return original
            val material = Mine2DMaterials.textShadow(
                minU = geometry.minU,
                minV = geometry.minV,
                maxU = geometry.maxU,
                maxV = geometry.maxV,
                uPerGuiUnit = geometry.uPerGuiUnit,
                vPerGuiUnit = geometry.vPerGuiUnit,
                blurRadius = blurRadius,
                grayscale = renderable.guiPipeline() === RenderPipelines.GUI_TEXT_GRAYSCALE,
            )
            val context = Mine2DUniformContext(
                elementBounds = Mine2DUniformRect(0f, 0f, 0f, 0f),
                contentBounds = Mine2DUniformRect(0f, 0f, 0f, 0f),
                viewportWidth = 0f,
                viewportHeight = 0f,
                timeSeconds = 0f,
            )
            return TextShadowGlyphRenderState(
                pose = original.pose(),
                renderable = renderable,
                scissor = original.scissorArea(),
                geometry = geometry,
                bindings = material.resolveBindings(context),
            )
        }
    }
}

private class GlyphVertexRecorder : VertexConsumer {
    private data class MutableVertex(
        var x: Float,
        var y: Float,
        var z: Float,
        var color: Int = -1,
        var u: Float = 0f,
        var v: Float = 0f,
        var light: Int = 0,
    )

    private val recordedVertices = mutableListOf<MutableVertex>()
    private val current: MutableVertex
        get() = checkNotNull(recordedVertices.lastOrNull()) {
            "A glyph vertex attribute was supplied before its position"
        }

    val vertices: List<Mine2DTextShadowVertex>
        get() = recordedVertices.map { vertex ->
            Mine2DTextShadowVertex(
                x = vertex.x,
                y = vertex.y,
                z = vertex.z,
                color = vertex.color,
                u = vertex.u,
                v = vertex.v,
                light = vertex.light,
            )
        }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        recordedVertices += MutableVertex(x, y, z)
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply {
        current.color =
            (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }

    override fun setColor(color: Int): VertexConsumer = apply {
        current.color = color
    }

    override fun setUv(u: Float, v: Float): VertexConsumer = apply {
        current.u = u
        current.v = v
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer = this

    override fun setUv2(u: Int, v: Int): VertexConsumer = apply {
        current.light = (v shl 16) or (u and 0xFFFF)
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = this

    override fun setLineWidth(width: Float): VertexConsumer = this
}
