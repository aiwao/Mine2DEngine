package io.github.aiwao.mine2dengine.layout

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Numeric operations used by a typed [RangeInput].
 *
 * Built-in instances are available from [RangeNumberTypes]. The class is sealed because range
 * controls require exact validation, interpolation, and step semantics for every supported type.
 */
sealed class RangeNumberType<T : Number>(
    val defaultMin: T,
    val defaultMax: T,
    val defaultStep: T,
) {
    /** Default human-readable representation used by range narration. */
    abstract fun format(value: T): String

    internal abstract fun isFinite(value: T): Boolean

    internal abstract fun toDecimal(value: T): BigDecimal

    internal abstract fun fromDecimal(value: BigDecimal): T

    internal open fun continuousKeyboardStep(minimum: BigDecimal, maximum: BigDecimal): BigDecimal =
        maximum.subtract(minimum).divide(HUNDRED, DECIMAL_CONTEXT)

    internal fun validateConfiguration(min: T, max: T, step: T?) {
        require(isFinite(min)) { "Range minimum must be finite: $min" }
        require(isFinite(max)) { "Range maximum must be finite: $max" }
        require(toDecimal(min) <= toDecimal(max)) {
            "Range minimum must not exceed maximum: $min > $max"
        }
        require(step == null || isFinite(step) && toDecimal(step) > BigDecimal.ZERO) {
            "Range step must be null or finite and positive: $step"
        }
    }

    internal fun sanitize(value: T, min: T, max: T, step: T?): T {
        require(isFinite(value)) { "Range value must be finite: $value" }
        validateConfiguration(min, max, step)
        return sanitizeDecimal(toDecimal(value), min, max, step)
    }

    internal fun midpoint(min: T, max: T, step: T?): T {
        validateConfiguration(min, max, step)
        val midpoint = toDecimal(min).add(toDecimal(max)).divide(TWO)
        return sanitizeDecimal(midpoint, min, max, step)
    }

    internal fun increment(value: T, steps: Int, min: T, max: T, step: T?): T {
        if (steps == 0 || valuesEqual(min, max)) return value
        val minimum = toDecimal(min)
        val maximum = toDecimal(max)
        val amount = step?.let(::toDecimal) ?: continuousKeyboardStep(minimum, maximum)
        val candidate = toDecimal(value).add(amount.multiply(BigDecimal.valueOf(steps.toLong())))
        return sanitizeDecimal(candidate, min, max, step)
    }

    internal fun interpolate(fraction: Double, min: T, max: T, step: T?): T {
        require(fraction.isFinite()) { "Range fraction must be finite: $fraction" }
        val normalized = fraction.coerceIn(0.0, 1.0)
        val minimum = toDecimal(min)
        val maximum = toDecimal(max)
        val candidate = minimum.add(
            maximum.subtract(minimum)
                .multiply(BigDecimal.valueOf(normalized), DECIMAL_CONTEXT),
        )
        return sanitizeDecimal(candidate, min, max, step)
    }

    internal fun fraction(value: T, min: T, max: T): Double {
        if (valuesEqual(min, max)) return 0.0

        val minimum = min.toDouble()
        val maximum = max.toDouble()
        val numericValue = value.toDouble()
        val span = maximum - minimum
        if (span.isFinite() && span > 0.0) {
            return ((numericValue - minimum) / span).coerceIn(0.0, 1.0)
        }

        val decimalMinimum = toDecimal(min)
        return toDecimal(value)
            .subtract(decimalMinimum)
            .divide(toDecimal(max).subtract(decimalMinimum), DECIMAL_CONTEXT)
            .toDouble()
            .coerceIn(0.0, 1.0)
    }

    private fun sanitizeDecimal(candidate: BigDecimal, min: T, max: T, step: T?): T {
        val minimum = toDecimal(min)
        val maximum = toDecimal(max)
        val clamped = candidate.coerceIn(minimum, maximum)
        val snapped = step?.let { fixedStep ->
            val interval = toDecimal(fixedStep)
            val offset = clamped.subtract(minimum)
            val stepIndex = offset.divide(interval, 0, RoundingMode.HALF_UP)
            var result = minimum.add(interval.multiply(stepIndex))
            if (result > maximum) {
                val maximumIndex = maximum.subtract(minimum)
                    .divide(interval, 0, RoundingMode.FLOOR)
                result = minimum.add(interval.multiply(maximumIndex))
            }
            result
        } ?: clamped
        val converted = fromDecimal(snapped)
        return when {
            toDecimal(converted) < minimum -> min
            toDecimal(converted) > maximum -> max
            else -> converted
        }
    }

    private fun valuesEqual(first: T, second: T): Boolean =
        toDecimal(first).compareTo(toDecimal(second)) == 0

    private companion object {
        val TWO: BigDecimal = BigDecimal.valueOf(2L)
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100L)
        val DECIMAL_CONTEXT: MathContext = MathContext.DECIMAL128
    }
}

private object IntRangeNumberType : RangeNumberType<Int>(0, 100, 1) {
    override fun format(value: Int): String = value.toString()

    override fun isFinite(value: Int): Boolean = true

    override fun toDecimal(value: Int): BigDecimal = BigDecimal.valueOf(value.toLong())

    override fun fromDecimal(value: BigDecimal): Int {
        val lower = value.setScale(0, RoundingMode.FLOOR)
        val upper = value.setScale(0, RoundingMode.CEILING)
        val rounded = if (value.subtract(lower) < upper.subtract(value)) lower else upper
        return rounded.intValueExact()
    }

    override fun continuousKeyboardStep(minimum: BigDecimal, maximum: BigDecimal): BigDecimal =
        maximum.subtract(minimum)
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.CEILING)
            .coerceAtLeast(BigDecimal.ONE)
}

private object FloatRangeNumberType : RangeNumberType<Float>(0f, 100f, 1f) {
    override fun format(value: Float): String =
        BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

    override fun isFinite(value: Float): Boolean = value.isFinite()

    override fun toDecimal(value: Float): BigDecimal = BigDecimal(value.toString())

    override fun fromDecimal(value: BigDecimal): Float = value.toFloat().also { converted ->
        check(converted.isFinite()) { "A clamped range value must remain finite: $value" }
    }
}

private object DoubleRangeNumberType : RangeNumberType<Double>(0.0, 100.0, 1.0) {
    override fun format(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    override fun isFinite(value: Double): Boolean = value.isFinite()

    override fun toDecimal(value: Double): BigDecimal = BigDecimal.valueOf(value)

    override fun fromDecimal(value: BigDecimal): Double = value.toDouble().also { converted ->
        check(converted.isFinite()) { "A clamped range value must remain finite: $value" }
    }
}

/** Built-in numeric strategies accepted by [RangeInput]. */
object RangeNumberTypes {
    @JvmField
    val INT: RangeNumberType<Int> = IntRangeNumberType

    @JvmField
    val FLOAT: RangeNumberType<Float> = FloatRangeNumberType

    @JvmField
    val DOUBLE: RangeNumberType<Double> = DoubleRangeNumberType
}

/** Returns the built-in range strategy for [Int], [Float], or [Double]. */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Number> rangeNumberType(): RangeNumberType<T> = when (T::class) {
    Int::class -> RangeNumberTypes.INT as RangeNumberType<T>
    Float::class -> RangeNumberTypes.FLOAT as RangeNumberType<T>
    Double::class -> RangeNumberTypes.DOUBLE as RangeNumberType<T>
    else -> throw IllegalArgumentException(
        "Unsupported range number type: ${T::class.qualifiedName}; expected Int, Float, or Double",
    )
}
