package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Mine2DShaderTest {
    @Test
    fun `built-in shader has the polygon pipeline contract`() {
        val pipeline = Mine2DShaders.COLOR.pipeline

        assertSame(DefaultVertexFormat.POSITION_COLOR, pipeline.getVertexFormatBinding(0))
        assertEquals(PrimitiveTopology.TRIANGLES, pipeline.primitiveTopology)
        assertFalse(pipeline.isCull)
        assertNull(Mine2DShaders.COLOR.uniformBlock)
        assertSame(Mine2DShaders.COLOR, Mine2DMaterials.COLOR.shader)
    }

    @Test
    fun `built-in box shadow shader has material bindings`() {
        val shader = Mine2DShaders.BOX_SHADOW
        val pipeline = shader.pipeline

        assertSame(DefaultVertexFormat.POSITION_COLOR, pipeline.getVertexFormatBinding(0))
        assertEquals(PrimitiveTopology.TRIANGLES, pipeline.primitiveTopology)
        assertFalse(pipeline.isCull)
        assertEquals("Mine2DBoxShadow", shader.uniformBlock?.name)
        assertEquals(
            listOf(
                "ShadowColor",
                "ShadowSize",
                "BlurRadius",
                "CornerRadiiHorizontal",
                "CornerRadiiVertical",
            ),
            shader.uniformBlock?.uniforms?.map(Mine2DUniform<*>::name),
        )
        assertTrue(shader.samplers.isEmpty())
    }

    @Test
    fun `built-in text shadow shader uses glyph quads and shared blur binding`() {
        val shader = Mine2DShaders.TEXT_SHADOW
        val pipeline = shader.pipeline

        assertSame(DefaultVertexFormat.POSITION_TEX_COLOR, pipeline.getVertexFormatBinding(0))
        assertEquals(PrimitiveTopology.QUADS, pipeline.primitiveTopology)
        assertEquals("Mine2DTextShadow", shader.uniformBlock?.name)
        assertEquals(
            listOf("UvBounds", "UvPerGuiUnit", "BlurRadius", "Grayscale"),
            shader.uniformBlock?.uniforms?.map(Mine2DUniform<*>::name),
        )
        assertSame(
            Mine2DShaders.SHADOW_BLUR_RADIUS,
            shader.uniformBlock?.uniforms?.get(2),
        )
        assertSame(
            Mine2DShaders.SHADOW_BLUR_RADIUS,
            Mine2DShaders.BOX_SHADOW.uniformBlock?.uniforms?.get(2),
        )
    }

    @Test
    fun `built-in drop shadow shader declares its compositor bindings`() {
        val shader = Mine2DShaders.DROP_SHADOW
        val pipeline = shader.pipeline

        assertSame(DefaultVertexFormat.POSITION_COLOR, pipeline.getVertexFormatBinding(0))
        assertEquals(PrimitiveTopology.TRIANGLES, pipeline.primitiveTopology)
        assertFalse(pipeline.isCull)
        assertEquals("Mine2DDropShadow", shader.uniformBlock?.name)
        assertEquals(
            listOf("ShadowColor", "OffsetAndViewport", "BlurAxes", "ShadowParameters"),
            shader.uniformBlock?.uniforms?.map(Mine2DUniform<*>::name),
        )
        assertTrue(
            Mine2DShaders.DROP_SHADOW_SAMPLER_NAME in
                BindGroupLayout.flattenSamplers(pipeline.bindGroupLayouts),
        )
    }

    @Test
    fun `rejects a pipeline with an incompatible topology`() {
        assertFailsWith<IllegalArgumentException> {
            Mine2DShader.from(RenderPipelines.GUI)
        }
    }
}
