package io.github.aiwao.mine2dengine.layout

/**
 * Specified declarations for the internal parts painted by a [RangeInput].
 *
 * A null property is not declared and therefore preserves an earlier cascaded value.
 */
data class UiRangeInputStyle(
    /** Color of the unfilled track. */
    val trackColor: Int? = null,
    /** Color of the track between the minimum and the current value. */
    val activeTrackColor: Int? = null,
    /** Color of the draggable thumb. */
    val thumbColor: Int? = null,
    /** Color drawn around the thumb while the input owns keyboard focus. */
    val focusColor: Int? = null,
)

/** Range-input declarations after initial values have been applied. */
internal data class ResolvedUiRangeInputStyle(
    val trackColor: Int,
    val activeTrackColor: Int,
    val thumbColor: Int,
    val focusColor: Int,
)

internal fun UiRangeInputStyle?.resolveDefaults(): ResolvedUiRangeInputStyle =
    ResolvedUiRangeInputStyle(
        trackColor = this?.trackColor ?: 0xFF555555.toInt(),
        activeTrackColor = this?.activeTrackColor ?: 0xFF4F8CFF.toInt(),
        thumbColor = this?.thumbColor ?: 0xFFE0E0E0.toInt(),
        focusColor = this?.focusColor ?: 0xFFFFFFFF.toInt(),
    )

/** Applies each declared range-input value in [overrides], preserving omitted declarations. */
internal fun UiRangeInputStyle?.withOverrides(
    overrides: UiRangeInputStyle?,
): UiRangeInputStyle? {
    if (overrides == null) return this
    val base = this ?: UiRangeInputStyle()
    return base.copy(
        trackColor = overrides.trackColor ?: base.trackColor,
        activeTrackColor = overrides.activeTrackColor ?: base.activeTrackColor,
        thumbColor = overrides.thumbColor ?: base.thumbColor,
        focusColor = overrides.focusColor ?: base.focusColor,
    )
}
