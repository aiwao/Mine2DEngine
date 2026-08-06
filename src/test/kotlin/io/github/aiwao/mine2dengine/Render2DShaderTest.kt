package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class Render2DShaderTest {
    @Test
    fun `built-in shader has the polygon pipeline contract`() {
        val pipeline = Render2DShaders.COLOR.pipeline

        assertSame(DefaultVertexFormat.POSITION_COLOR, pipeline.getVertexFormatBinding(0))
        assertEquals(PrimitiveTopology.TRIANGLES, pipeline.primitiveTopology)
        assertFalse(pipeline.isCull)
    }

    @Test
    fun `rejects a pipeline with an incompatible topology`() {
        assertFailsWith<IllegalArgumentException> {
            Render2DShader.from(RenderPipelines.GUI)
        }
    }
}
