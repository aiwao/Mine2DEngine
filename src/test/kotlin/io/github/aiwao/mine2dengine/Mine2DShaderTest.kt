package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
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
            listOf("ShadowColor", "ShadowSize", "BlurRadius", "CornerRadius"),
            shader.uniformBlock?.uniforms?.map(Mine2DUniform<*>::name),
        )
        assertTrue(shader.samplers.isEmpty())
    }

    @Test
    fun `rejects a pipeline with an incompatible topology`() {
        assertFailsWith<IllegalArgumentException> {
            Mine2DShader.from(RenderPipelines.GUI)
        }
    }
}
