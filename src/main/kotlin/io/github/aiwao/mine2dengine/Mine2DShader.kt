package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * A shader pipeline compatible with [Mine2DEngine]'s polygon vertex format.
 *
 * Custom pipelines must consume `Position` and `Color` attributes and use the
 * triangle primitive topology. Prefer [register] when creating one so it is
 * included in Minecraft's pipeline registry.
 */
class Mine2DShader private constructor(
    val pipeline: RenderPipeline,
) {
    companion object {
        /**
         * Wraps an already-created compatible pipeline.
         */
        @JvmStatic
        fun from(pipeline: RenderPipeline): Mine2DShader {
            require(pipeline.getVertexFormatBinding(0) == DefaultVertexFormat.POSITION_COLOR) {
                "Mine2D shaders must use DefaultVertexFormat.POSITION_COLOR at binding 0"
            }
            require(pipeline.primitiveTopology == PrimitiveTopology.TRIANGLES) {
                "Mine2D shaders must use PrimitiveTopology.TRIANGLES"
            }
            return Mine2DShader(pipeline)
        }

        /**
         * Creates and registers a Mine2D-compatible pipeline.
         *
         * Call this during mod initialization. Shader identifiers are relative
         * to `assets/<namespace>/shaders/` and omit the `.vsh` / `.fsh` suffix.
         * [configure] can add shader defines or alter blend/depth state; the
         * vertex format, primitive topology, and culling mode are enforced by
         * this method.
         */
        @JvmStatic
        @JvmOverloads
        fun register(
            location: Identifier,
            vertexShader: Identifier,
            fragmentShader: Identifier,
            configure: RenderPipeline.Builder.() -> Unit = {},
        ): Mine2DShader {
            val pipeline = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                .withLocation(location)
                .withVertexShader(vertexShader)
                .withFragmentShader(fragmentShader)
                .apply(configure)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build()

            return from(RenderPipelines.register(pipeline))
        }
    }
}

/** Built-in pipelines supplied by the Mine2D engine. */
object Mine2DShaders {
    /** The standard Minecraft GUI color shader with alpha blending. */
    @JvmField
    val COLOR: Mine2DShader = Mine2DShader.register(
        location = Identifier.fromNamespaceAndPath("mine2dengine", "pipeline/mine2d_color"),
        vertexShader = Identifier.withDefaultNamespace("core/gui"),
        fragmentShader = Identifier.withDefaultNamespace("core/gui"),
    )

    /** Forces object initialization during the mod initialization phase. */
    internal fun initialize() = Unit
}
