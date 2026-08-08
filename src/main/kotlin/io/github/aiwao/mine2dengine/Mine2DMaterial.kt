package io.github.aiwao.mine2dengine

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.FilterMode
import io.github.aiwao.mine2dengine.internal.render.Mine2DRenderBindings
import io.github.aiwao.mine2dengine.internal.render.Mine2DTextureBinding
import io.github.aiwao.mine2dengine.internal.render.Mine2DUniformBinding
import java.util.Collections
import java.util.IdentityHashMap
import org.joml.Vector2f
import org.joml.Vector4f

/** A typed sampler binding declared by a [Mine2DShader]. */
class Mine2DSampler(
    val name: String,
) {
    init {
        validateBindingName(name, "Sampler")
    }
}

/** A texture view and sampler assigned to a material sampler key. */
data class Mine2DTexture(
    val view: GpuTextureView,
    val sampler: GpuSampler,
)

/**
 * An immutable shader instance containing its material uniform values and textures.
 *
 * Use [Mine2DShader.material] to create one. Mutable vector and matrix inputs are copied while the
 * material is built, so a queued draw never observes later caller mutations.
 */
class Mine2DMaterial internal constructor(
    val shader: Mine2DShader,
    values: IdentityHashMap<Mine2DUniform<*>, Any>,
    samplerBindings: IdentityHashMap<Mine2DSampler, Mine2DTextureBinding>,
) {
    internal val values: Map<Mine2DUniform<*>, Any> =
        Collections.unmodifiableMap(IdentityHashMap(values))
    internal val samplerBindings: Map<Mine2DSampler, Mine2DTextureBinding> =
        Collections.unmodifiableMap(IdentityHashMap(samplerBindings))

    /** Creates a copy with selected values or textures replaced. */
    fun with(configure: Mine2DMaterialBuilder.() -> Unit): Mine2DMaterial =
        shader.material(this, configure)

    internal fun resolveBindings(context: Mine2DUniformContext): Mine2DRenderBindings {
        val uniformBindings = shader.uniformBlock
            ?.let { block ->
                listOf(Mine2DUniformBinding(block.name, block.pack(values, context)))
            }
            .orEmpty()
        val textureBindings = shader.samplers.map { sampler ->
            checkNotNull(samplerBindings[sampler]) {
                "Sampler ${sampler.name} was not resolved for ${shader.pipeline.location}"
            }
        }

        return if (uniformBindings.isEmpty() && textureBindings.isEmpty()) {
            Mine2DRenderBindings.EMPTY
        } else {
            Mine2DRenderBindings(uniformBindings, textureBindings)
        }
    }
}

/** Builds an immutable [Mine2DMaterial] for one shader. */
class Mine2DMaterialBuilder internal constructor(
    private val shader: Mine2DShader,
    base: Mine2DMaterial? = null,
) {
    private val values = IdentityHashMap<Mine2DUniform<*>, Any>()
    private val samplerBindings = IdentityHashMap<Mine2DSampler, Mine2DTextureBinding>()

    init {
        if (base != null) {
            require(base.shader === shader) { "A material can only copy values from the same shader" }
            values.putAll(base.values)
            samplerBindings.putAll(base.samplerBindings)
        }
    }

    /** Sets a typed material uniform. Automatic semantic uniforms cannot be assigned manually. */
    fun <T : Any> set(uniform: Mine2DUniform<T>, value: T): Mine2DMaterialBuilder = apply {
        val block = requireNotNull(shader.uniformBlock) {
            "Shader ${shader.pipeline.location} does not declare a material uniform block"
        }
        require(block.contains(uniform)) {
            "Uniform ${uniform.name} does not belong to shader ${shader.pipeline.location}"
        }
        require(uniform.source == Mine2DUniformSource.MATERIAL) {
            "Automatic uniform ${uniform.name} cannot be assigned by a material"
        }
        values[uniform] = uniform.copyAndValidate(value)
    }

    /** Binds a texture and sampler to a sampler key declared by this shader. */
    fun bind(
        key: Mine2DSampler,
        view: GpuTextureView,
        sampler: GpuSampler,
    ): Mine2DMaterialBuilder = bind(key, Mine2DTexture(view, sampler))

    /** Binds a prepared texture pair to a sampler key declared by this shader. */
    fun bind(key: Mine2DSampler, texture: Mine2DTexture): Mine2DMaterialBuilder = apply {
        requireDeclaredSampler(key)
        samplerBindings[key] = Mine2DTextureBinding.fixed(key.name, texture.view, texture.sampler)
    }

    /**
     * Binds a snapshot of the main color target taken immediately before GUI element rendering.
     *
     * The renderer copies the target to a separate texture once per frame, so shaders can sample
     * the background without reading from the texture currently used as their render attachment.
     */
    @JvmOverloads
    fun bindGuiBackground(
        key: Mine2DSampler,
        filterMode: FilterMode = FilterMode.LINEAR,
    ): Mine2DMaterialBuilder = apply {
        requireDeclaredSampler(key)
        samplerBindings[key] = Mine2DTextureBinding.guiBackground(key.name, filterMode)
    }

    internal fun build(): Mine2DMaterial {
        shader.uniformBlock?.uniforms?.forEach { uniform ->
            if (uniform.source != Mine2DUniformSource.MATERIAL || values.containsKey(uniform)) {
                return@forEach
            }
            require(uniform.hasDefault) {
                "Material for ${shader.pipeline.location} requires uniform ${uniform.name}"
            }
            values[uniform] = uniform.copyAndValidate(checkNotNull(uniform.defaultValue))
        }

        val missingSamplers = shader.samplers.filterNot(samplerBindings::containsKey)
        require(missingSamplers.isEmpty()) {
            "Material for ${shader.pipeline.location} requires samplers: " +
                missingSamplers.joinToString { sampler -> sampler.name }
        }

        return Mine2DMaterial(shader, values, samplerBindings)
    }

    private fun requireDeclaredSampler(key: Mine2DSampler) {
        require(shader.samplers.any { candidate -> candidate === key }) {
            "Sampler ${key.name} does not belong to shader ${shader.pipeline.location}"
        }
    }
}

/** Built-in materials supplied by Mine2D. */
object Mine2DMaterials {
    /** Standard untextured vertex color material with alpha blending. */
    @JvmField
    val COLOR: Mine2DMaterial = Mine2DShaders.COLOR.material()

    internal fun boxShadow(
        color: Int,
        width: Float,
        height: Float,
        blurRadius: Float,
        cornerRadius: Float,
    ): Mine2DMaterial = Mine2DShaders.BOX_SHADOW.material {
        set(Mine2DShaders.BOX_SHADOW_COLOR, color.toRgbaVector())
        set(Mine2DShaders.BOX_SHADOW_SIZE, Vector2f(width, height))
        set(Mine2DShaders.SHADOW_BLUR_RADIUS, blurRadius)
        set(Mine2DShaders.BOX_SHADOW_CORNER_RADIUS, cornerRadius)
    }

    internal fun textShadow(
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
        uPerGuiUnit: Float,
        vPerGuiUnit: Float,
        blurRadius: Float,
        grayscale: Boolean,
    ): Mine2DMaterial = Mine2DShaders.TEXT_SHADOW.material {
        set(Mine2DShaders.TEXT_SHADOW_UV_BOUNDS, Vector4f(minU, minV, maxU, maxV))
        set(
            Mine2DShaders.TEXT_SHADOW_UV_PER_GUI_UNIT,
            Vector2f(uPerGuiUnit, vPerGuiUnit),
        )
        set(Mine2DShaders.SHADOW_BLUR_RADIUS, blurRadius)
        set(Mine2DShaders.TEXT_SHADOW_GRAYSCALE, if (grayscale) 1 else 0)
    }
}

private fun Int.toRgbaVector(): Vector4f = Vector4f(
    (this ushr 16 and 0xFF) / 255f,
    (this ushr 8 and 0xFF) / 255f,
    (this and 0xFF) / 255f,
    (this ushr 24 and 0xFF) / 255f,
)
