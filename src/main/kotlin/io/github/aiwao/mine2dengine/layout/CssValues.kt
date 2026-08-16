package io.github.aiwao.mine2dengine.layout

/** The outer role of a generated CSS box in its parent's flow formatting context. */
enum class UiDisplayOutside {
    BLOCK,
    INLINE,
}

/** The formatting context established for the contents of a generated CSS box. */
enum class UiDisplayInside {
    FLOW,
    FLOW_ROOT,
    FLEX,
}

/**
 * CSS `display` as either a pair of outer/inner display types or a box-suppression value.
 *
 * The constants mirror the commonly used single-keyword CSS values. [Box] remains public so a
 * caller can spell multi-keyword values such as `inline flow-root` without adding another enum
 * constant.
 */
sealed interface UiDisplay {
    data class Box(
        val outside: UiDisplayOutside,
        val inside: UiDisplayInside,
    ) : UiDisplay

    data object None : UiDisplay

    data object Contents : UiDisplay

    companion object {
        @JvmField
        val BLOCK: UiDisplay = Box(UiDisplayOutside.BLOCK, UiDisplayInside.FLOW)

        @JvmField
        val INLINE: UiDisplay = Box(UiDisplayOutside.INLINE, UiDisplayInside.FLOW)

        @JvmField
        val FLOW_ROOT: UiDisplay = Box(UiDisplayOutside.BLOCK, UiDisplayInside.FLOW_ROOT)

        @JvmField
        val FLEX: UiDisplay = Box(UiDisplayOutside.BLOCK, UiDisplayInside.FLEX)

        @JvmField
        val INLINE_FLEX: UiDisplay = Box(UiDisplayOutside.INLINE, UiDisplayInside.FLEX)

        @JvmField
        val NONE: UiDisplay = None

        @JvmField
        val CONTENTS: UiDisplay = Contents
    }
}

internal val UiDisplay.box: UiDisplay.Box?
    get() = this as? UiDisplay.Box

/** A CSS preferred/minimum/maximum size value. */
sealed interface UiSizeValue {
    data object Auto : UiSizeValue

    /** Used as the initial value of max-width and max-height. */
    data object None : UiSizeValue

    data object MinContent : UiSizeValue

    data object MaxContent : UiSizeValue

    data class FitContent(
        val limit: UiLength? = null,
    ) : UiSizeValue {
        init {
            require(limit == null || limit.value >= 0f) {
                "A fit-content limit must be non-negative: $limit"
            }
        }
    }

    companion object {
        @JvmField
        val AUTO: UiSizeValue = Auto

        @JvmField
        val NONE: UiSizeValue = None

        @JvmField
        val MIN_CONTENT: UiSizeValue = MinContent

        @JvmField
        val MAX_CONTENT: UiSizeValue = MaxContent
    }
}

/** A CSS margin value. */
sealed interface UiMarginValue {
    data object Auto : UiMarginValue

    companion object {
        @JvmField
        val AUTO: UiMarginValue = Auto
    }
}

/** A CSS inset (`top`, `right`, `bottom`, or `left`) value. */
sealed interface UiInsetValue {
    data object Auto : UiInsetValue

    companion object {
        @JvmField
        val AUTO: UiInsetValue = Auto
    }
}

/** A CSS flex-basis value. */
sealed interface UiFlexBasis {
    data object Auto : UiFlexBasis

    data object Content : UiFlexBasis

    data object MinContent : UiFlexBasis

    data object MaxContent : UiFlexBasis

    companion object {
        @JvmField
        val AUTO: UiFlexBasis = Auto

        @JvmField
        val CONTENT: UiFlexBasis = Content

        @JvmField
        val MIN_CONTENT: UiFlexBasis = MinContent

        @JvmField
        val MAX_CONTENT: UiFlexBasis = MaxContent
    }
}

/** Unit used by CSS length-percentage values. */
enum class UiLengthUnit {
    PX,
    PERCENT,
    VW,
    VH,
}

/**
 * A finite CSS length-percentage.
 *
 * Negative values are represented because CSS margins and insets permit them. Properties that do
 * not accept negative values validate the value when [UiStyle] is constructed.
 */
data class UiLength(
    val value: Float,
    val unit: UiLengthUnit,
) : UiSizeValue, UiMarginValue, UiInsetValue, UiFlexBasis {
    init {
        require(value.isFinite()) { "Length must be finite: $value" }
    }
}

/** Treats this value as a CSS pixel length. */
val Float.px: UiLength
    get() = UiLength(this, UiLengthUnit.PX)

/** Treats this value as a CSS percentage. */
val Float.percent: UiLength
    get() = UiLength(this, UiLengthUnit.PERCENT)

/** Treats this value as a percentage of the layout viewport's width. */
val Float.vw: UiLength
    get() = UiLength(this, UiLengthUnit.VW)

/** Treats this value as a percentage of the layout viewport's height. */
val Float.vh: UiLength
    get() = UiLength(this, UiLengthUnit.VH)

/** Resolves CSS lengths against the viewport shared by one layout or rendering pass. */
internal class UiLengthResolver(
    private val viewport: UiSize,
) {
    fun resolve(length: UiLength, percentageBase: Float?): Float? {
        val resolved = when (length.unit) {
            UiLengthUnit.PX -> length.value
            UiLengthUnit.PERCENT -> percentageBase?.let { it * length.value / 100f }
            UiLengthUnit.VW -> viewport.width * length.value / 100f
            UiLengthUnit.VH -> viewport.height * length.value / 100f
        }
        require(resolved == null || resolved.isFinite()) {
            "Resolved length must be finite: $resolved"
        }
        return resolved
    }
}

/** Declaration accepted by the CSS margin longhands/shorthand. */
sealed interface UiMarginDeclaration

/** Declaration accepted by the CSS padding longhands/shorthand. */
sealed interface UiPaddingDeclaration

/** Physical CSS margin values in top, right, bottom, left order. */
data class UiMargins(
    val top: UiMarginValue = 0f.px,
    val right: UiMarginValue = 0f.px,
    val bottom: UiMarginValue = 0f.px,
    val left: UiMarginValue = 0f.px,
) : UiMarginDeclaration {
    constructor(all: UiMarginValue) : this(all, all, all, all)

    constructor(vertical: UiMarginValue, horizontal: UiMarginValue) : this(
        top = vertical,
        right = horizontal,
        bottom = vertical,
        left = horizontal,
    )

    constructor(all: Float) : this(all.px)

    constructor(vertical: Float, horizontal: Float) : this(vertical.px, horizontal.px)
}

/** Physical CSS padding values in top, right, bottom, left order. */
data class UiPaddings(
    val top: UiLength = 0f.px,
    val right: UiLength = 0f.px,
    val bottom: UiLength = 0f.px,
    val left: UiLength = 0f.px,
) : UiPaddingDeclaration {
    constructor(all: UiLength) : this(all, all, all, all)

    constructor(vertical: UiLength, horizontal: UiLength) : this(
        top = vertical,
        right = horizontal,
        bottom = vertical,
        left = horizontal,
    )

    constructor(all: Float) : this(all.px)

    constructor(vertical: Float, horizontal: Float) : this(vertical.px, horizontal.px)

    init {
        require(top.value >= 0f) { "Padding top must be non-negative: $top" }
        require(right.value >= 0f) { "Padding right must be non-negative: $right" }
        require(bottom.value >= 0f) { "Padding bottom must be non-negative: $bottom" }
        require(left.value >= 0f) { "Padding left must be non-negative: $left" }
    }
}

/** A border line style supported by the CSS layout renderer. */
enum class UiBorderStyle {
    NONE,
    SOLID,
}

/** The width, style, and color of one physical CSS border side. */
data class UiBorderSide(
    val width: UiLength,
    val style: UiBorderStyle = UiBorderStyle.SOLID,
    /** Null uses the element's computed `color`, matching CSS `currentColor`. */
    val color: Int? = null,
) {
    /** Creates one border side with a width in CSS pixels. */
    constructor(
        width: Float,
        style: UiBorderStyle = UiBorderStyle.SOLID,
        color: Int? = null,
    ) : this(width.px, style, color)

    init {
        require(width.unit != UiLengthUnit.PERCENT) {
            "Border width does not accept percentages: $width"
        }
        require(width.value >= 0f) { "Border width must be non-negative: $width" }
    }

    /** The layout width after `border-style: none` has been applied. */
    internal fun usedWidth(lengthResolver: UiLengthResolver): Float =
        if (style == UiBorderStyle.NONE) {
            0f
        } else {
            checkNotNull(lengthResolver.resolve(width, percentageBase = null))
        }

    companion object {
        @JvmField
        val NONE = UiBorderSide(0f.px, UiBorderStyle.NONE)
    }
}

/** Physical CSS borders in top, right, bottom, left order. */
data class UiBorders(
    val top: UiBorderSide = UiBorderSide.NONE,
    val right: UiBorderSide = UiBorderSide.NONE,
    val bottom: UiBorderSide = UiBorderSide.NONE,
    val left: UiBorderSide = UiBorderSide.NONE,
) {
    constructor(all: UiBorderSide) : this(all, all, all, all)

    /** Creates four equal solid borders. A null [color] means CSS `currentColor`. */
    constructor(width: UiLength, color: Int? = null) : this(
        UiBorderSide(width = width, color = color),
    )

    /** Creates four equal solid borders in CSS pixels. */
    constructor(width: Float, color: Int? = null) : this(width.px, color)

    companion object {
        @JvmField
        val NONE = UiBorders()
    }
}

/** Horizontal and vertical CSS length-percentage radii of one physical corner. */
data class UiCornerRadius(
    val horizontal: UiLength,
    val vertical: UiLength = horizontal,
) {
    /** Creates a circular radius in CSS pixels. */
    constructor(all: Float) : this(all.px)

    /** Creates an elliptical radius in CSS pixels. */
    constructor(horizontal: Float, vertical: Float) : this(horizontal.px, vertical.px)

    init {
        require(horizontal.value >= 0f) {
            "A horizontal border radius must be non-negative: $horizontal"
        }
        require(vertical.value >= 0f) {
            "A vertical border radius must be non-negative: $vertical"
        }
    }

    companion object {
        @JvmField
        val ZERO = UiCornerRadius(0f.px)
    }
}

/** Physical `border-radius` values in top-left, top-right, bottom-right, bottom-left order. */
data class UiBorderRadii(
    val topLeft: UiCornerRadius = UiCornerRadius.ZERO,
    val topRight: UiCornerRadius = UiCornerRadius.ZERO,
    val bottomRight: UiCornerRadius = UiCornerRadius.ZERO,
    val bottomLeft: UiCornerRadius = UiCornerRadius.ZERO,
) {
    /** Creates four equal circular radii. */
    constructor(all: UiLength) : this(UiCornerRadius(all))

    /** Creates four equal circular radii in CSS pixels. */
    constructor(all: Float) : this(all.px)

    /** Creates four equal elliptical radii. */
    constructor(horizontal: UiLength, vertical: UiLength) : this(
        UiCornerRadius(horizontal, vertical),
    )

    /** Creates four equal elliptical radii in CSS pixels. */
    constructor(horizontal: Float, vertical: Float) : this(horizontal.px, vertical.px)

    /** Creates four equal corner radii. */
    constructor(all: UiCornerRadius) : this(all, all, all, all)

    companion object {
        @JvmField
        val ZERO = UiBorderRadii()
    }
}

enum class UiFlexDirection {
    ROW,
    ROW_REVERSE,
    COLUMN,
    COLUMN_REVERSE,
}

enum class UiFlexWrap {
    NOWRAP,
    WRAP,
    WRAP_REVERSE,
}

enum class UiJustifyContent {
    NORMAL,
    START,
    END,
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

enum class UiAlignItems {
    NORMAL,
    STRETCH,
    START,
    END,
    FLEX_START,
    FLEX_END,
    CENTER,
    BASELINE,
}

enum class UiAlignSelf {
    AUTO,
    NORMAL,
    STRETCH,
    START,
    END,
    FLEX_START,
    FLEX_END,
    CENTER,
    BASELINE,
}

enum class UiAlignContent {
    NORMAL,
    STRETCH,
    START,
    END,
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

/** Alignment of inline content within a line box. */
enum class UiTextAlign {
    START,
    END,
    LEFT,
    RIGHT,
    CENTER,
}

/** The supported subset of CSS white-space processing. */
enum class UiWhiteSpace {
    NORMAL,
    NOWRAP,
    PRE,
}

/** A value accepted by the CSS overflow shorthand and its physical-axis longhands. */
enum class UiOverflowValue {
    VISIBLE,
    HIDDEN,
    CLIP,
    SCROLL,
    AUTO;

    /** Whether this value creates a programmatically scrollable axis. */
    internal val isScrollable: Boolean
        get() = this == HIDDEN || this == SCROLL || this == AUTO

    /** Whether content outside this axis of the overflow clip edge is clipped. */
    internal val clips: Boolean
        get() = this != VISIBLE

    /** Whether direct user input, such as a mouse wheel, may scroll this axis. */
    internal val acceptsUserScroll: Boolean
        get() = this == SCROLL || this == AUTO
}

/** One- or two-value declaration for the CSS `overflow` shorthand. */
data class UiOverflow(
    val x: UiOverflowValue,
    val y: UiOverflowValue,
) {
    constructor(all: UiOverflowValue) : this(all, all)
}

/** A resolved physical edge set used by layout geometry. */
internal data class UsedEdges(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
) {
    val horizontal: Float
        get() = left + right

    val vertical: Float
        get() = top + bottom

    operator fun plus(other: UsedEdges): UsedEdges = UsedEdges(
        top = top + other.top,
        right = right + other.right,
        bottom = bottom + other.bottom,
        left = left + other.left,
    )
}
