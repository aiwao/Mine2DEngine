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

/** A supported CSS relationship between the left and right sides of a selector. */
enum class StyleSheetCombinator(
    val symbol: String,
) {
    DESCENDANT(" "),
    CHILD(">"),
    ADJACENT_SIBLING("+"),
    GENERAL_SIBLING("~"),
}

/**
 * Selects [right] elements related to a matching [left] element by [combinator].
 *
 * Combinators can be nested to represent a selector chain.
 */
data class TargetCombinator(
    val left: StyleSheetTarget,
    val combinator: StyleSheetCombinator,
    val right: StyleSheetTarget,
) : StyleSheetTarget

/** Combines this selector with [target] using a CSS [combinator]. */
fun StyleSheetTarget.combine(
    combinator: StyleSheetCombinator,
    target: StyleSheetTarget,
): TargetCombinator = TargetCombinator(this, combinator, target)

/** Selects matching descendants of this selector. */
infix fun StyleSheetTarget.descendant(target: StyleSheetTarget): TargetCombinator =
    combine(StyleSheetCombinator.DESCENDANT, target)

/** Selects matching direct children of this selector. */
infix fun StyleSheetTarget.child(target: StyleSheetTarget): TargetCombinator =
    combine(StyleSheetCombinator.CHILD, target)

/** Selects a matching sibling immediately following this selector. */
infix fun StyleSheetTarget.adjacentSibling(target: StyleSheetTarget): TargetCombinator =
    combine(StyleSheetCombinator.ADJACENT_SIBLING, target)

/** Selects matching siblings following this selector. */
infix fun StyleSheetTarget.generalSibling(target: StyleSheetTarget): TargetCombinator =
    combine(StyleSheetCombinator.GENERAL_SIBLING, target)

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

internal class StyleSheetElementContext(
    val element: UiElement,
    val parent: StyleSheetElementContext? = null,
    val previousSibling: StyleSheetElementContext? = null,
)

/** Resolves all declarations matching [context] according to selector specificity and order. */
internal fun Iterable<StyleSheet>.styleFor(context: StyleSheetElementContext): UiStyle? =
    flatMap(StyleSheet::styles)
        .mapNotNull { rule ->
            rule.target
                .filter { target -> target.matches(context) }
                .maxOfOrNull(StyleSheetTarget::specificity)
                ?.let { specificity -> specificity to rule.style }
        }
        .sortedBy { (specificity, _) -> specificity }
        .fold(null as UiStyle?) { resolved, (_, style) ->
            resolved?.withOverrides(style) ?: style
        }

private data class StyleSheetSpecificity(
    val classCount: Int = 0,
    val tagCount: Int = 0,
) : Comparable<StyleSheetSpecificity> {
    override fun compareTo(other: StyleSheetSpecificity): Int {
        val classComparison = classCount.compareTo(other.classCount)
        return if (classComparison != 0) classComparison else tagCount.compareTo(other.tagCount)
    }

    operator fun plus(other: StyleSheetSpecificity): StyleSheetSpecificity =
        StyleSheetSpecificity(
            classCount = classCount + other.classCount,
            tagCount = tagCount + other.tagCount,
        )
}

private val StyleSheetTarget.specificity: StyleSheetSpecificity
    get() = when (this) {
        is TargetClass -> StyleSheetSpecificity(classCount = 1)
        is TargetTag -> StyleSheetSpecificity(tagCount = 1)
        is TargetCombinator -> left.specificity + right.specificity
    }

private fun StyleSheetTarget.matches(context: StyleSheetElementContext): Boolean = when (this) {
    is TargetClass -> className in context.element.classes
    is TargetTag -> context.element.tag == tag
    is TargetCombinator -> right.matches(context) && relatedContexts(context).any(left::matches)
}

private fun TargetCombinator.relatedContexts(
    context: StyleSheetElementContext,
): Sequence<StyleSheetElementContext> = when (combinator) {
    StyleSheetCombinator.DESCENDANT ->
        generateSequence(context.parent, StyleSheetElementContext::parent)

    StyleSheetCombinator.CHILD -> listOfNotNull(context.parent).asSequence()
    StyleSheetCombinator.ADJACENT_SIBLING ->
        listOfNotNull(context.previousSibling).asSequence()

    StyleSheetCombinator.GENERAL_SIBLING ->
        generateSequence(context.previousSibling, StyleSheetElementContext::previousSibling)
}
