package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextureBinding
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class Mine2DMaterialTest {
    @Test
    fun `material uniforms and automatic draw semantics are packed as std140`() {
        val elementBounds = Mine2DUniform.elementBounds()
        val radius = Mine2DUniform.float("Radius")
        val viewportSize = Mine2DUniform.viewportSize()
        val contentBounds = Mine2DUniform.contentBounds()
        val timeSeconds = Mine2DUniform.timeSeconds()
        val transform = Mine2DUniform.mat4(
            "EffectTransform",
            Matrix4f().translation(2f, 3f, 0f),
        )
        val block = Mine2DUniformBlock(
            "Mine2DMaterial",
            elementBounds,
            radius,
            viewportSize,
            contentBounds,
            timeSeconds,
            transform,
        )
        val material = shader(block).material {
            set(radius, 7.5f)
        }

        val data = material.resolveBindings(
            Mine2DUniformContext(
                elementBounds = Mine2DUniformRect(10f, 20f, 80f, 30f),
                contentBounds = Mine2DUniformRect(14f, 25f, 72f, 20f),
                viewportWidth = 320f,
                viewportHeight = 180f,
                timeSeconds = 12.25f,
            ),
        ).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertEquals(128, block.byteSize)
        assertFloatSequence(buffer, 0, 10f, 20f, 80f, 30f)
        assertEquals(7.5f, buffer.getFloat(16))
        assertFloatSequence(buffer, 24, 320f, 180f)
        assertFloatSequence(buffer, 32, 14f, 25f, 72f, 20f)
        assertEquals(12.25f, buffer.getFloat(48))
        assertEquals(1f, buffer.getFloat(64))
        assertEquals(2f, buffer.getFloat(112))
        assertEquals(3f, buffer.getFloat(116))
    }

    @Test
    fun `materials require fields without defaults and fill fields with defaults`() {
        val required = Mine2DUniform.float("Required")
        val defaulted = Mine2DUniform.float("Defaulted", 3f)
        val shader = shader(Mine2DUniformBlock("Mine2DMaterial", required, defaulted))

        assertFailsWith<IllegalArgumentException> { shader.material() }

        val material = shader.material { set(required, 2f) }
        assertEquals(2f, material.values[required])
        assertEquals(3f, material.values[defaulted])
    }

    @Test
    fun `std140 vec3 occupies a complete sixteen byte slot`() {
        val direction = Mine2DUniform.vec3("Direction")
        val strength = Mine2DUniform.float("Strength")
        val block = Mine2DUniformBlock("Mine2DMaterial", direction, strength)
        val material = shader(block).material {
            set(direction, Vector3f(1f, 2f, 3f))
            set(strength, 4f)
        }

        val data = material.resolveBindings(defaultContext()).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertEquals(20, block.byteSize)
        assertFloatSequence(buffer, 0, 1f, 2f, 3f)
        assertEquals(4f, buffer.getFloat(16))
    }

    @Test
    fun `materials reject automatic and foreign uniform keys`() {
        val radius = Mine2DUniform.float("Radius")
        val automatic = Mine2DUniform.elementBounds()
        val foreign = Mine2DUniform.float("Foreign")
        val shader = shader(Mine2DUniformBlock("Mine2DMaterial", radius, automatic))

        assertFailsWith<IllegalArgumentException> {
            shader.material { set(automatic, Vector4f()) }
        }
        assertFailsWith<IllegalArgumentException> {
            shader.material { set(foreign, 1f) }
        }
    }

    @Test
    fun `material copies mutable vector values and supports immutable derivation`() {
        val color = Mine2DUniform.vec4("EffectColor")
        val shader = shader(Mine2DUniformBlock("Mine2DMaterial", color))
        val input = Vector4f(1f, 2f, 3f, 4f)
        val original = shader.material { set(color, input) }

        input.set(9f, 9f, 9f, 9f)
        val stored = original.values[color] as Vector4f
        assertFloatSequence(stored, 1f, 2f, 3f, 4f)
        assertNotSame(input, stored)

        val derived = original.with { set(color, Vector4f(5f, 6f, 7f, 8f)) }
        assertFloatSequence(original.values[color] as Vector4f, 1f, 2f, 3f, 4f)
        assertFloatSequence(derived.values[color] as Vector4f, 5f, 6f, 7f, 8f)
    }

    @Test
    fun `uniform schemas validate identifiers and duplicate fields`() {
        val first = Mine2DUniform.float("Value")
        val sameName = Mine2DUniform.int("Value")

        assertFailsWith<IllegalArgumentException> { Mine2DUniform.float("not-valid") }
        assertFailsWith<IllegalArgumentException> { Mine2DUniformBlock("", first) }
        assertFailsWith<IllegalArgumentException> {
            Mine2DUniformBlock("Mine2DMaterial", first, first)
        }
        assertFailsWith<IllegalArgumentException> {
            Mine2DUniformBlock("Mine2DMaterial", first, sameName)
        }
        assertFailsWith<IllegalArgumentException> {
            Mine2DUniform.float("NotFinite", Float.NaN)
        }
    }

    @Test
    fun `shader requires its described uniform block in the pipeline`() {
        val block = Mine2DUniformBlock("Mine2DMaterial", Mine2DUniform.float("Value", 0f))
        val pipeline = basePipelineBuilder().build()

        assertFailsWith<IllegalArgumentException> {
            Mine2DShader.from(pipeline, block)
        }
    }

    @Test
    fun `material requires every declared sampler`() {
        val sampler = Mine2DSampler("NoiseSampler")
        val shader = shader(uniformBlock = null, samplers = listOf(sampler))

        assertFailsWith<IllegalArgumentException> { shader.material() }
    }

    @Test
    fun `GUI background binding satisfies a sampler without an extraction-time texture`() {
        val sampler = Mine2DSampler("BackgroundSampler")
        val shader = shader(uniformBlock = null, samplers = listOf(sampler))

        val material = shader.material {
            bindGuiBackground(sampler)
        }
        val binding = material.resolveBindings(defaultContext()).textures().single()

        assertEquals(Mine2DTextureBinding.Kind.GUI_BACKGROUND, binding.kind())
        assertEquals(FilterMode.LINEAR, binding.filterMode())
        assertEquals(null, binding.texture())
        assertEquals(null, binding.sampler())
    }

    @Test
    fun `built-in box shadow material packs ARGB color and shape parameters`() {
        val material = Mine2DMaterials.boxShadow(
            color = 0x80402010.toInt(),
            width = 104f,
            height = 44f,
            blurRadius = 6f,
            cornerRadii = Mine2DRoundedRectRadii(
                topLeft = Mine2DCornerRadius(10f, 11f),
                topRight = Mine2DCornerRadius(12f, 13f),
                bottomRight = Mine2DCornerRadius(14f, 15f),
                bottomLeft = Mine2DCornerRadius(16f, 17f),
            ),
        )
        val data = material.resolveBindings(defaultContext()).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertFloatSequence(buffer, 0, 64f / 255f, 32f / 255f, 16f / 255f, 128f / 255f)
        assertFloatSequence(buffer, 16, 104f, 44f)
        assertEquals(6f, buffer.getFloat(24))
        assertFloatSequence(buffer, 32, 10f, 12f, 14f, 16f)
        assertFloatSequence(buffer, 48, 11f, 13f, 15f, 17f)
    }

    @Test
    fun `built-in drop shadow material packs color transform and viewport parameters`() {
        val material = Mine2DMaterials.dropShadow(
            color = 0x80402010.toInt(),
            offsetX = 3f,
            offsetY = -2f,
            viewportWidth = 320f,
            viewportHeight = 180f,
            blurAxisXx = 6f,
            blurAxisXy = 1f,
            blurAxisYx = 2f,
            blurAxisYy = 7f,
            blurRadius = 6f,
        )
        val data = material.resolveBindings(defaultContext()).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertFloatSequence(buffer, 0, 64f / 255f, 32f / 255f, 16f / 255f, 128f / 255f)
        assertFloatSequence(buffer, 16, 3f, -2f, 320f, 180f)
        assertFloatSequence(buffer, 32, 6f, 1f, 2f, 7f)
        assertFloatSequence(buffer, 48, 6f, 0f, 0f, 0f)
    }

    @Test
    fun `rounded clip material packs bounds radii transform and viewport`() {
        val material = Mine2DMaterials.roundedClip(
            left = 10f,
            top = 20f,
            width = 80f,
            height = 40f,
            radii = Mine2DRoundedRectRadii(
                topLeft = Mine2DCornerRadius(1f, 2f),
                topRight = Mine2DCornerRadius(3f, 4f),
                bottomRight = Mine2DCornerRadius(5f, 6f),
                bottomLeft = Mine2DCornerRadius(7f, 8f),
            ),
            screenToLocalX = Vector4f(1f, 2f, 3f, 0f),
            screenToLocalY = Vector4f(4f, 5f, 6f, 0f),
            viewportWidth = 320f,
            viewportHeight = 180f,
        )
        val data = material.resolveBindings(defaultContext()).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertFloatSequence(buffer, 0, 10f, 20f, 80f, 40f)
        assertFloatSequence(buffer, 16, 1f, 3f, 5f, 7f)
        assertFloatSequence(buffer, 32, 2f, 4f, 6f, 8f)
        assertFloatSequence(buffer, 48, 1f, 2f, 3f, 0f)
        assertFloatSequence(buffer, 64, 4f, 5f, 6f, 0f)
        assertFloatSequence(buffer, 80, 320f, 180f, 0f, 0f)
    }

    @Test
    fun `built-in text shadow material packs glyph atlas and blur parameters`() {
        val material = Mine2DMaterials.textShadow(
            minU = 0.1f,
            minV = 0.2f,
            maxU = 0.3f,
            maxV = 0.4f,
            uPerGuiX = 0.01f,
            vPerGuiX = 0.02f,
            uPerGuiY = 0.03f,
            vPerGuiY = 0.04f,
            blurRadius = 6f,
            grayscale = true,
        )
        val data = material.resolveBindings(defaultContext()).uniforms().single().dataUnsafe()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder())

        assertFloatSequence(buffer, 0, 0.1f, 0.2f, 0.3f, 0.4f)
        assertFloatSequence(buffer, 16, 0.01f, 0.02f, 0.03f, 0.04f)
        assertEquals(6f, buffer.getFloat(32))
        assertEquals(1, buffer.getInt(36))
    }

    private fun shader(
        uniformBlock: Mine2DUniformBlock?,
        samplers: List<Mine2DSampler> = emptyList(),
    ): Mine2DShader {
        val builder = basePipelineBuilder()
        if (uniformBlock != null || samplers.isNotEmpty()) {
            val bindings = BindGroupLayout.builder()
            uniformBlock?.let { block ->
                bindings.withUniform(block.name, UniformType.UNIFORM_BUFFER)
            }
            samplers.forEach { sampler -> bindings.withSampler(sampler.name) }
            builder.withBindGroupLayout(bindings.build())
        }
        return Mine2DShader.from(builder.build(), uniformBlock, samplers)
    }

    private fun basePipelineBuilder(): RenderPipeline.Builder =
        RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("mine2dengine", "test/material"))
            .withVertexShader(Identifier.withDefaultNamespace("core/gui"))
            .withFragmentShader(Identifier.withDefaultNamespace("core/gui"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)

    private fun defaultContext(): Mine2DUniformContext = Mine2DUniformContext(
        elementBounds = Mine2DUniformRect(0f, 0f, 1f, 1f),
        contentBounds = Mine2DUniformRect(0f, 0f, 1f, 1f),
        viewportWidth = 1f,
        viewportHeight = 1f,
        timeSeconds = 0f,
    )

    private fun assertFloatSequence(buffer: ByteBuffer, offset: Int, vararg expected: Float) {
        expected.forEachIndexed { index, value ->
            assertEquals(value, buffer.getFloat(offset + index * Float.SIZE_BYTES))
        }
    }

    private fun assertFloatSequence(actual: Vector4f, vararg expected: Float) {
        assertEquals(expected.toList(), listOf(actual.x, actual.y, actual.z, actual.w))
    }
}
