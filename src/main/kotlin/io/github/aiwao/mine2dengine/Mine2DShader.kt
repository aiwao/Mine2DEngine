package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
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
        ): Mine2DShader = fromCompatiblePipeline(
            pipeline = pipeline,
            uniformBlock = uniformBlock,
            samplers = samplers,
            vertexFormat = DefaultVertexFormat.POSITION_COLOR,
            topology = PrimitiveTopology.TRIANGLES,
            contract = "Mine2D polygon shaders",
        )

        internal fun fromTextPipeline(
            pipeline: RenderPipeline,
            uniformBlock: Mine2DUniformBlock,
        ): Mine2DShader = fromCompatiblePipeline(
            pipeline = pipeline,
            uniformBlock = uniformBlock,
            samplers = emptyList(),
            vertexFormat = DefaultVertexFormat.POSITION_TEX_COLOR,
            topology = PrimitiveTopology.QUADS,
            contract = "Mine2D text-shadow shader",
        )

        private fun fromCompatiblePipeline(
            pipeline: RenderPipeline,
            uniformBlock: Mine2DUniformBlock?,
            samplers: List<Mine2DSampler>,
            vertexFormat: VertexFormat,
            topology: PrimitiveTopology,
            contract: String,
        ): Mine2DShader {
            require(pipeline.getVertexFormatBinding(0) == vertexFormat) {
                "$contract must use $vertexFormat at binding 0"
            }
            require(pipeline.primitiveTopology == topology) {
                "$contract must use $topology"
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
    internal val SHADOW_BLUR_RADIUS = Mine2DUniform.float("BlurRadius", 0f)
    internal val BOX_SHADOW_CORNER_RADII_HORIZONTAL = Mine2DUniform.vec4("CornerRadiiHorizontal")
    internal val BOX_SHADOW_CORNER_RADII_VERTICAL = Mine2DUniform.vec4("CornerRadiiVertical")

    /** Analytic rounded-box shadow used by [Mine2DEngine.boxShadow]. */
    internal val BOX_SHADOW: Mine2DShader = Mine2DShader.register(
        location = Identifier.fromNamespaceAndPath("mine2dengine", "pipeline/mine2d_box_shadow"),
        vertexShader = Identifier.fromNamespaceAndPath("mine2dengine", "core/box_shadow"),
        fragmentShader = Identifier.fromNamespaceAndPath("mine2dengine", "core/box_shadow"),
        uniformBlock = Mine2DUniformBlock(
            "Mine2DBoxShadow",
            BOX_SHADOW_COLOR,
            BOX_SHADOW_SIZE,
            SHADOW_BLUR_RADIUS,
            BOX_SHADOW_CORNER_RADII_HORIZONTAL,
            BOX_SHADOW_CORNER_RADII_VERTICAL,
        ),
    )

    internal const val DROP_SHADOW_SAMPLER_NAME = "DropShadowSampler"
    internal val DROP_SHADOW_COLOR = Mine2DUniform.vec4(
        "ShadowColor",
        Vector4f(0f, 0f, 0f, 0.5f),
    )
    internal val DROP_SHADOW_OFFSET_VIEWPORT = Mine2DUniform.vec4("OffsetAndViewport")
    internal val DROP_SHADOW_BLUR_AXES = Mine2DUniform.vec4("BlurAxes")
    internal val DROP_SHADOW_PARAMETERS = Mine2DUniform.vec4("ShadowParameters")
    private val DROP_SHADOW_UNIFORM_BLOCK = Mine2DUniformBlock(
        "Mine2DDropShadow",
        DROP_SHADOW_COLOR,
        DROP_SHADOW_OFFSET_VIEWPORT,
        DROP_SHADOW_BLUR_AXES,
        DROP_SHADOW_PARAMETERS,
    )

    /** Composites a Gaussian-blurred alpha mask for CSS-compatible drop shadows. */
    internal val DROP_SHADOW: Mine2DShader = Mine2DShader.from(
        pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "mine2dengine",
                        "pipeline/mine2d_drop_shadow",
                    ),
                )
                .withVertexShader(
                    Identifier.fromNamespaceAndPath("mine2dengine", "core/drop_shadow"),
                )
                .withFragmentShader(
                    Identifier.fromNamespaceAndPath("mine2dengine", "core/drop_shadow"),
                )
                .withBindGroupLayout(
                    BindGroupLayout.builder()
                        .withUniform("Mine2DDropShadow", UniformType.UNIFORM_BUFFER)
                        .withSampler(DROP_SHADOW_SAMPLER_NAME)
                        .build(),
                )
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build(),
        ),
        uniformBlock = DROP_SHADOW_UNIFORM_BLOCK,
    )

    internal val TEXT_SHADOW_UV_BOUNDS = Mine2DUniform.vec4("UvBounds")
    internal val TEXT_SHADOW_UV_PER_GUI_UNIT = Mine2DUniform.vec4("UvPerGuiUnit")
    internal val TEXT_SHADOW_GRAYSCALE = Mine2DUniform.int("Grayscale")
    private val TEXT_SHADOW_UNIFORM_BLOCK = Mine2DUniformBlock(
        "Mine2DTextShadow",
        TEXT_SHADOW_UV_BOUNDS,
        TEXT_SHADOW_UV_PER_GUI_UNIT,
        SHADOW_BLUR_RADIUS,
        TEXT_SHADOW_GRAYSCALE,
    )

    /** Glyph-alpha Gaussian shadow used by [Mine2DEngine.textShadow]. */
    internal val TEXT_SHADOW: Mine2DShader = Mine2DShader.fromTextPipeline(
        pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXT_SNIPPET)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "mine2dengine",
                        "pipeline/mine2d_text_shadow",
                    ),
                )
                .withVertexShader(
                    Identifier.fromNamespaceAndPath("mine2dengine", "core/text_shadow"),
                )
                .withFragmentShader(
                    Identifier.fromNamespaceAndPath("mine2dengine", "core/text_shadow"),
                )
                .withBindGroupLayout(
                    BindGroupLayout.builder()
                        .withUniform("Mine2DTextShadow", UniformType.UNIFORM_BUFFER)
                        .build(),
                )
                .build(),
        ),
        uniformBlock = TEXT_SHADOW_UNIFORM_BLOCK,
    )

    /** Forces object initialization during mod initialization. */
    internal fun initialize() = Unit
}
