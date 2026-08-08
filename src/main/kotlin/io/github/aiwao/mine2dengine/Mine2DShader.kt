package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import org.joml.Vector2f
import org.joml.Vector4f

/**
 * An immutable polygon pipeline and its typed material binding schema.
 *
 * Custom pipelines consume `Position` and `Color` attributes and use triangle topology. A shader
 * may declare one arbitrary std140 [uniformBlock] and any number of typed [samplers]. Values are
 * supplied by immutable [Mine2DMaterial] instances.
 */
class Mine2DShader private constructor(
    val pipeline: RenderPipeline,
    val uniformBlock: Mine2DUniformBlock?,
    samplers: Iterable<Mine2DSampler>,
) {
    val samplers: List<Mine2DSampler> = samplers.toList()

    init {
        val duplicateSamplerNames = this.samplers
            .groupingBy(Mine2DSampler::name)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateSamplerNames.isEmpty()) {
            "Shader contains duplicate sampler names: ${duplicateSamplerNames.joinToString()}"
        }
        require(uniformBlock == null || this.samplers.none { it.name == uniformBlock.name }) {
            "A uniform block and sampler cannot share the name ${uniformBlock?.name}"
        }
    }

    /** Creates a validated immutable material for this shader. */
    @JvmOverloads
    fun material(configure: Mine2DMaterialBuilder.() -> Unit = {}): Mine2DMaterial =
        material(base = null, configure)

    internal fun material(
        base: Mine2DMaterial?,
        configure: Mine2DMaterialBuilder.() -> Unit,
    ): Mine2DMaterial = Mine2DMaterialBuilder(this, base).apply(configure).build()

    companion object {
        /** Wraps an already-created compatible pipeline and describes its Mine2D bindings. */
        @JvmStatic
        @JvmOverloads
        fun from(
            pipeline: RenderPipeline,
            uniformBlock: Mine2DUniformBlock? = null,
            samplers: List<Mine2DSampler> = emptyList(),
        ): Mine2DShader {
            require(pipeline.getVertexFormatBinding(0) == DefaultVertexFormat.POSITION_COLOR) {
                "Mine2D shaders must use DefaultVertexFormat.POSITION_COLOR at binding 0"
            }
            require(pipeline.primitiveTopology == PrimitiveTopology.TRIANGLES) {
                "Mine2D shaders must use PrimitiveTopology.TRIANGLES"
            }

            val uniformDescriptions = BindGroupLayout.flattenUniforms(pipeline.bindGroupLayouts)
            if (uniformBlock != null) {
                require(
                    uniformDescriptions.any { description ->
                        description.name() == uniformBlock.name &&
                            description.type() == UniformType.UNIFORM_BUFFER
                    },
                ) {
                    "Pipeline ${pipeline.location} does not declare uniform block ${uniformBlock.name}"
                }
            }

            val declaredSamplers = BindGroupLayout.flattenSamplers(pipeline.bindGroupLayouts).toSet()
            val missingSamplers = samplers.filterNot { sampler -> sampler.name in declaredSamplers }
            require(missingSamplers.isEmpty()) {
                "Pipeline ${pipeline.location} does not declare samplers: " +
                    missingSamplers.joinToString { sampler -> sampler.name }
            }

            return Mine2DShader(pipeline, uniformBlock, samplers)
        }

        /**
         * Creates and registers a Mine2D-compatible pipeline.
         *
         * Shader identifiers are relative to `assets/<namespace>/shaders/` and omit the `.vsh` or
         * `.fsh` suffix. The uniform block must match a std140 block in the shader sources exactly.
         * [configure] may alter blend or depth state and add defines or other binding layouts; the
         * polygon vertex format, topology, and culling mode are enforced afterward.
         */
        @JvmStatic
        @JvmOverloads
        fun register(
            location: Identifier,
            vertexShader: Identifier,
            fragmentShader: Identifier,
            uniformBlock: Mine2DUniformBlock? = null,
            samplers: List<Mine2DSampler> = emptyList(),
            configure: RenderPipeline.Builder.() -> Unit = {},
        ): Mine2DShader {
            val pipelineBuilder = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .apply(configure)

            if (uniformBlock != null || samplers.isNotEmpty()) {
                val materialBindings = BindGroupLayout.builder()
                uniformBlock?.let { block ->
                    materialBindings.withUniform(block.name, UniformType.UNIFORM_BUFFER)
                }
                samplers.forEach { sampler -> materialBindings.withSampler(sampler.name) }
                pipelineBuilder.withBindGroupLayout(materialBindings.build())
            }

            val pipeline = pipelineBuilder
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build()

            return from(
                pipeline = RenderPipelines.register(pipeline),
                uniformBlock = uniformBlock,
                samplers = samplers,
            )
        }
    }
}

/** Built-in pipelines supplied by Mine2D. */
object Mine2DShaders {
    /** Standard Minecraft GUI vertex color shader with alpha blending. */
    @JvmField
    val COLOR: Mine2DShader = Mine2DShader.register(
        location = Identifier.fromNamespaceAndPath("mine2dengine", "pipeline/mine2d_color"),
        vertexShader = Identifier.withDefaultNamespace("core/gui"),
        fragmentShader = Identifier.withDefaultNamespace("core/gui"),
    )

    internal val BOX_SHADOW_COLOR = Mine2DUniform.vec4(
        "ShadowColor",
        Vector4f(0f, 0f, 0f, 0.5f),
    )
    internal val BOX_SHADOW_SIZE = Mine2DUniform.vec2(
        "ShadowSize",
        Vector2f(1f, 1f),
    )
    internal val BOX_SHADOW_BLUR_RADIUS = Mine2DUniform.float("BlurRadius", 0f)
    internal val BOX_SHADOW_CORNER_RADIUS = Mine2DUniform.float("CornerRadius", 0f)

    /** Analytic rounded-box shadow used by [Mine2DEngine.boxShadow]. */
    internal val BOX_SHADOW: Mine2DShader = Mine2DShader.register(
        location = Identifier.fromNamespaceAndPath("mine2dengine", "pipeline/mine2d_box_shadow"),
        vertexShader = Identifier.fromNamespaceAndPath("mine2dengine", "core/box_shadow"),
        fragmentShader = Identifier.fromNamespaceAndPath("mine2dengine", "core/box_shadow"),
        uniformBlock = Mine2DUniformBlock(
            "Mine2DBoxShadow",
            BOX_SHADOW_COLOR,
            BOX_SHADOW_SIZE,
            BOX_SHADOW_BLUR_RADIUS,
            BOX_SHADOW_CORNER_RADIUS,
        ),
    )

    /** Forces object initialization during mod initialization. */
    internal fun initialize() = Unit
}
