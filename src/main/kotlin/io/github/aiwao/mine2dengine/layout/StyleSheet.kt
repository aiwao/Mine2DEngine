package io.github.aiwao.mine2dengine.layout

/** A selector that can decide whether a style-sheet rule applies to a [UiElement]. */
sealed interface StyleSheetTarget

/** Selects elements containing [className] in their HTML-compatible class list. */
data class TargetClass(
    val className: String,
) : StyleSheetTarget

/** Selects elements whose HTML-compatible tag is [tag]. */
data class TargetTag(
    val tag: String,
) : StyleSheetTarget

/** One CSS-like rule. Any selector in [target] can make [style] apply. */
data class StyleSheetObject(
    val target: Array<out StyleSheetTarget>,
    val style: UiStyle,
)

/**
 * A collection of CSS-like rules consumed by [LayoutEngine].
 *
 * Rules with class selectors take precedence over rules containing only matching tag selectors.
 * Rules with the same specificity are applied in insertion order, so later rules win. An
 * element's directly supplied [UiElement.style] takes precedence over style-sheet declarations.
 */
interface StyleSheet {
    val styles: MutableList<StyleSheetObject>

    /** Adds a rule and returns the object stored in [styles]. */
    fun newStyle(
        target: Array<out StyleSheetTarget>,
        style: UiStyle,
    ): StyleSheetObject = StyleSheetObject(target, style).also(styles::add)
}

/** Resolves all declarations matching [element] according to selector specificity and order. */
internal fun Iterable<StyleSheet>.styleFor(element: UiElement): UiStyle? =
    flatMap(StyleSheet::styles)
        .mapNotNull { rule ->
            rule.target
                .filter { target -> target.matches(element) }
                .maxOfOrNull(StyleSheetTarget::specificity)
                ?.let { specificity -> specificity to rule.style }
        }
        .sortedBy { (specificity, _) -> specificity }
        .fold(null as UiStyle?) { resolved, (_, style) ->
            resolved?.withOverrides(style) ?: style
        }

private val StyleSheetTarget.specificity: Int
    get() = when (this) {
        is TargetClass -> 1
        is TargetTag -> 0
    }

private fun StyleSheetTarget.matches(element: UiElement): Boolean = when (this) {
    is TargetClass -> className in element.classes
    is TargetTag -> element.tag == tag
}
