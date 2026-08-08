package io.github.aiwao.mine2dengine

import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.joml.Vector4fc
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

/** The value type and std140 representation of a [Mine2DUniform]. */
enum class Mine2DUniformKind {
    INT,
    FLOAT,
    VEC2,
    VEC3,
    VEC4,
    MAT4,
}

internal enum class Mine2DUniformSource {
    MATERIAL,
    ELEMENT_BOUNDS,
    CONTENT_BOUNDS,
    VIEWPORT_SIZE,
    TIME_SECONDS,
}

/**
 * A typed field in a shader's material uniform block.
 *
 * Keys use identity semantics and belong to the [Mine2DUniformBlock] in which they are declared.
 * Material fields are supplied through [Mine2DMaterialBuilder.set]. Semantic fields are populated
 * automatically for every draw.
 */
class Mine2DUniform<T : Any> private constructor(
    val name: String,
    val kind: Mine2DUniformKind,
    internal val source: Mine2DUniformSource,
    internal val hasDefault: Boolean,
    defaultValue: T?,
) {
    internal val defaultValue: Any? = defaultValue?.let(::copyAndValidate)

    init {
        validateBindingName(name, "Uniform")
        require(source == Mine2DUniformSource.MATERIAL || !hasDefault) {
            "Automatic uniform $name cannot have a material default"
        }
    }

    internal fun copyAndValidate(value: Any): Any = when (kind) {
        Mine2DUniformKind.INT -> requireType<Int>(value)
        Mine2DUniformKind.FLOAT -> requireType<Float>(value).also { float ->
            require(float.isFinite()) { "Uniform $name must be finite: $float" }
        }

        Mine2DUniformKind.VEC2 -> Vector2f(requireType<Vector2fc>(value)).also { vector ->
            require(vector.isFinite) { "Uniform $name must contain finite values: $vector" }
        }

        Mine2DUniformKind.VEC3 -> Vector3f(requireType<Vector3fc>(value)).also { vector ->
            require(vector.isFinite) { "Uniform $name must contain finite values: $vector" }
        }

        Mine2DUniformKind.VEC4 -> Vector4f(requireType<Vector4fc>(value)).also { vector ->
            require(vector.isFinite) { "Uniform $name must contain finite values: $vector" }
        }

        Mine2DUniformKind.MAT4 -> Matrix4f(requireType<Matrix4fc>(value)).also { matrix ->
            require(matrix.isFinite) { "Uniform $name must contain finite values" }
        }
    }

    private inline fun <reified V : Any> requireType(value: Any): V {
        require(value is V) {
            "Uniform $name expects $kind but received ${value.javaClass.simpleName}"
        }
        return value
    }

    internal val std140Alignment: Int
        get() = when (kind) {
            Mine2DUniformKind.INT, Mine2DUniformKind.FLOAT -> 4
            Mine2DUniformKind.VEC2 -> 8
            Mine2DUniformKind.VEC3, Mine2DUniformKind.VEC4, Mine2DUniformKind.MAT4 -> 16
        }

    internal val std140Size: Int
        get() = when (kind) {
            Mine2DUniformKind.INT, Mine2DUniformKind.FLOAT -> 4
            Mine2DUniformKind.VEC2 -> 8
            Mine2DUniformKind.VEC3, Mine2DUniformKind.VEC4 -> 16
            Mine2DUniformKind.MAT4 -> 64
        }

    internal fun write(buffer: ByteBuffer, offset: Int, value: Any) {
        when (kind) {
            Mine2DUniformKind.INT -> buffer.putInt(offset, value as Int)
            Mine2DUniformKind.FLOAT -> buffer.putFloat(offset, value as Float)
            Mine2DUniformKind.VEC2 -> (value as Vector2fc).let { vector ->
                buffer.putFloat(offset, vector.x())
                buffer.putFloat(offset + 4, vector.y())
            }

            Mine2DUniformKind.VEC3 -> (value as Vector3fc).let { vector ->
                buffer.putFloat(offset, vector.x())
                buffer.putFloat(offset + 4, vector.y())
                buffer.putFloat(offset + 8, vector.z())
            }

            Mine2DUniformKind.VEC4 -> (value as Vector4fc).let { vector ->
                buffer.putFloat(offset, vector.x())
                buffer.putFloat(offset + 4, vector.y())
                buffer.putFloat(offset + 8, vector.z())
                buffer.putFloat(offset + 12, vector.w())
            }

            Mine2DUniformKind.MAT4 -> writeMatrix(buffer, offset, value as Matrix4fc)
        }
    }

    private fun writeMatrix(buffer: ByteBuffer, offset: Int, matrix: Matrix4fc) {
        buffer.putFloat(offset, matrix.m00())
        buffer.putFloat(offset + 4, matrix.m01())
        buffer.putFloat(offset + 8, matrix.m02())
        buffer.putFloat(offset + 12, matrix.m03())
        buffer.putFloat(offset + 16, matrix.m10())
        buffer.putFloat(offset + 20, matrix.m11())
        buffer.putFloat(offset + 24, matrix.m12())
        buffer.putFloat(offset + 28, matrix.m13())
        buffer.putFloat(offset + 32, matrix.m20())
        buffer.putFloat(offset + 36, matrix.m21())
        buffer.putFloat(offset + 40, matrix.m22())
        buffer.putFloat(offset + 44, matrix.m23())
        buffer.putFloat(offset + 48, matrix.m30())
        buffer.putFloat(offset + 52, matrix.m31())
        buffer.putFloat(offset + 56, matrix.m32())
        buffer.putFloat(offset + 60, matrix.m33())
    }

    companion object {
        @JvmStatic
        fun int(name: String): Mine2DUniform<Int> = material(name, Mine2DUniformKind.INT)

        @JvmStatic
        fun int(name: String, defaultValue: Int): Mine2DUniform<Int> =
            material(name, Mine2DUniformKind.INT, defaultValue)

        @JvmStatic
        fun float(name: String): Mine2DUniform<Float> = material(name, Mine2DUniformKind.FLOAT)

        @JvmStatic
        fun float(name: String, defaultValue: Float): Mine2DUniform<Float> =
            material(name, Mine2DUniformKind.FLOAT, defaultValue)

        @JvmStatic
        fun vec2(name: String): Mine2DUniform<Vector2fc> = material(name, Mine2DUniformKind.VEC2)

        @JvmStatic
        fun vec2(name: String, defaultValue: Vector2fc): Mine2DUniform<Vector2fc> =
            material(name, Mine2DUniformKind.VEC2, defaultValue)

        @JvmStatic
        fun vec3(name: String): Mine2DUniform<Vector3fc> = material(name, Mine2DUniformKind.VEC3)

        @JvmStatic
        fun vec3(name: String, defaultValue: Vector3fc): Mine2DUniform<Vector3fc> =
            material(name, Mine2DUniformKind.VEC3, defaultValue)

        @JvmStatic
        fun vec4(name: String): Mine2DUniform<Vector4fc> = material(name, Mine2DUniformKind.VEC4)

        @JvmStatic
        fun vec4(name: String, defaultValue: Vector4fc): Mine2DUniform<Vector4fc> =
            material(name, Mine2DUniformKind.VEC4, defaultValue)

        @JvmStatic
        fun mat4(name: String): Mine2DUniform<Matrix4fc> = material(name, Mine2DUniformKind.MAT4)

        @JvmStatic
        fun mat4(name: String, defaultValue: Matrix4fc): Mine2DUniform<Matrix4fc> =
            material(name, Mine2DUniformKind.MAT4, defaultValue)

        /** The painted element bounds as `(left, top, width, height)` in GUI coordinates. */
        @JvmStatic
        @JvmOverloads
        fun elementBounds(name: String = "ElementBounds"): Mine2DUniform<Vector4fc> =
            automatic(name, Mine2DUniformKind.VEC4, Mine2DUniformSource.ELEMENT_BOUNDS)

        /** The element's content bounds as `(left, top, width, height)` in GUI coordinates. */
        @JvmStatic
        @JvmOverloads
        fun contentBounds(name: String = "ContentBounds"): Mine2DUniform<Vector4fc> =
            automatic(name, Mine2DUniformKind.VEC4, Mine2DUniformSource.CONTENT_BOUNDS)

        /** The current GUI viewport size as `(width, height)`. */
        @JvmStatic
        @JvmOverloads
        fun viewportSize(name: String = "ViewportSize"): Mine2DUniform<Vector2fc> =
            automatic(name, Mine2DUniformKind.VEC2, Mine2DUniformSource.VIEWPORT_SIZE)

        /** Monotonic seconds since Mine2D's renderer clock was initialized. */
        @JvmStatic
        @JvmOverloads
        fun timeSeconds(name: String = "TimeSeconds"): Mine2DUniform<Float> =
            automatic(name, Mine2DUniformKind.FLOAT, Mine2DUniformSource.TIME_SECONDS)

        private fun <T : Any> material(
            name: String,
            kind: Mine2DUniformKind,
        ): Mine2DUniform<T> = Mine2DUniform(
            name = name,
            kind = kind,
            source = Mine2DUniformSource.MATERIAL,
            hasDefault = false,
            defaultValue = null,
        )

        private fun <T : Any> material(
            name: String,
            kind: Mine2DUniformKind,
            defaultValue: T,
        ): Mine2DUniform<T> = Mine2DUniform(
            name = name,
            kind = kind,
            source = Mine2DUniformSource.MATERIAL,
            hasDefault = true,
            defaultValue = defaultValue,
        )

        private fun <T : Any> automatic(
            name: String,
            kind: Mine2DUniformKind,
            source: Mine2DUniformSource,
        ): Mine2DUniform<T> = Mine2DUniform(
            name = name,
            kind = kind,
            source = source,
            hasDefault = false,
            defaultValue = null,
        )
    }
}

/** Ordered std140 fields bound to one named uniform block. */
class Mine2DUniformBlock(
    val name: String,
    uniforms: Iterable<Mine2DUniform<*>>,
) {
    constructor(name: String, vararg uniforms: Mine2DUniform<*>) : this(name, uniforms.asIterable())

    val uniforms: List<Mine2DUniform<*>> = uniforms.toList()

    val byteSize: Int

    private val fieldOffsets: IntArray

    init {
        validateBindingName(name, "Uniform block")
        require(this.uniforms.isNotEmpty()) { "Uniform block $name must contain at least one field" }

        val identities = java.util.Collections.newSetFromMap(
            IdentityHashMap<Mine2DUniform<*>, Boolean>(),
        )
        require(this.uniforms.all(identities::add)) {
            "Uniform block $name contains the same key more than once"
        }
        val duplicateNames = this.uniforms
            .groupingBy(Mine2DUniform<*>::name)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Uniform block $name contains duplicate field names: ${duplicateNames.joinToString()}"
        }

        var size = 0
        fieldOffsets = IntArray(this.uniforms.size) { index ->
            val uniform = this.uniforms[index]
            size = size.roundUpTo(uniform.std140Alignment)
            val offset = size
            size += uniform.std140Size
            offset
        }
        byteSize = size
    }

    internal fun contains(uniform: Mine2DUniform<*>): Boolean =
        uniforms.any { candidate -> candidate === uniform }

    internal fun pack(
        materialValues: Map<Mine2DUniform<*>, Any>,
        context: Mine2DUniformContext,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(byteSize).order(ByteOrder.nativeOrder())
        uniforms.forEachIndexed { index, uniform ->
            val value = when (uniform.source) {
                Mine2DUniformSource.MATERIAL -> checkNotNull(materialValues[uniform]) {
                    "Material value for ${uniform.name} was not resolved"
                }

                Mine2DUniformSource.ELEMENT_BOUNDS -> context.elementBounds.toVector()
                Mine2DUniformSource.CONTENT_BOUNDS -> context.contentBounds.toVector()
                Mine2DUniformSource.VIEWPORT_SIZE -> Vector2f(context.viewportWidth, context.viewportHeight)
                Mine2DUniformSource.TIME_SECONDS -> context.timeSeconds
            }
            uniform.write(buffer, fieldOffsets[index], value)
        }
        return buffer.array()
    }
}

internal data class Mine2DUniformRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    fun toVector(): Vector4f = Vector4f(left, top, width, height)
}

internal data class Mine2DUniformContext(
    val elementBounds: Mine2DUniformRect,
    val contentBounds: Mine2DUniformRect,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val timeSeconds: Float,
)

internal object Mine2DClock {
    private val originNanos = System.nanoTime()

    fun seconds(): Float = ((System.nanoTime() - originNanos) / 1_000_000_000.0).toFloat()
}

internal fun validateBindingName(name: String, label: String) {
    require(BINDING_NAME.matches(name)) {
        "$label name must be a valid shader identifier: $name"
    }
}

private val BINDING_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

private fun Int.roundUpTo(alignment: Int): Int = (this + alignment - 1) / alignment * alignment
