package io.github.aiwao.mine2dengine.layout

/** A selector that can decide whether a style-sheet rule applies to a [UiElement]. */
sealed interface StyleSheetTarget

/** Selects elements containing the exact [className], which may include whitespace. */
data class TargetClass(
    val className: String,
) : StyleSheetTarget

/** Selects elements whose tag exactly equals [tag], which may include whitespace. */
data class TargetTag(
    val tag: String,
) : StyleSheetTarget

/** Selects elements whose HTML-compatible ID is [id]. */
data class TargetId(
    val id: String,
) : StyleSheetTarget

/** Selects every element, like the CSS universal selector `*`. */
data object TargetWildcard : StyleSheetTarget

/**
 * Selects the root of the current style-sheet scope, like the CSS `:scope` pseudo-class.
 *
 * For a global sheet this is the layout root. For a sheet attached to a container or component,
 * this is that container or component root.
 */
data object TargetScope : StyleSheetTarget

/**
 * Selects elements that match every selector in [targets], like CSS `button.primary`.
 *
 * Every target is evaluated against the same element. Use [TargetCombinator] for relationships
 * between different elements.
 */
data class TargetAnd(
    val targets: List<StyleSheetTarget>,
) : StyleSheetTarget {
    constructor(vararg targets: StyleSheetTarget) : this(targets.toList())

    init {
        require(targets.isNotEmpty()) { "An AND target requires at least one selector" }
    }
}

/** Selects elements that match any selector in [targets], like a CSS selector list. */
data class TargetOr(
    val targets: List<StyleSheetTarget>,
) : StyleSheetTarget {
    constructor(vararg targets: StyleSheetTarget) : this(targets.toList())

    init {
        require(targets.isNotEmpty()) { "An OR target requires at least one selector" }
    }
}

/** Combines two selectors as conditions on the same element. */
infix fun StyleSheetTarget.and(target: StyleSheetTarget): TargetAnd =
    TargetAnd(andTargets() + target.andTargets())

/** Combines two selectors as alternatives in a selector list. */
infix fun StyleSheetTarget.or(target: StyleSheetTarget): TargetOr =
    TargetOr(orTargets() + target.orTargets())

private fun StyleSheetTarget.andTargets(): List<StyleSheetTarget> =
    if (this is TargetAnd) targets else listOf(this)

private fun StyleSheetTarget.orTargets(): List<StyleSheetTarget> =
    if (this is TargetOr) targets else listOf(this)

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

/**
 * One CSS-like rule whose declaration is resolved for each matching element.
 *
 * [resolveStyle] may be evaluated more than once during layout and rendering. It should not rely
 * on invocation count or perform side effects.
 */
class StyleSheetObject(
    val target: StyleSheetTarget,
    private val resolveStyle: (UiElement) -> UiStyle,
) {
    /** Creates a rule with a declaration that is shared by every matching element. */
    constructor(target: StyleSheetTarget, style: UiStyle) : this(target, { style })

    internal fun styleFor(element: UiElement): UiStyle = resolveStyle(element)
}

/**
 * A collection of CSS-like rules consumed by [LayoutEngine].
 *
 * ID, class/pseudo-class, and tag selectors contribute CSS specificity in that order. Rules with
 * the same specificity are applied in insertion order, so later rules win. An element's directly
 * supplied [UiElement.style] takes precedence over style-sheet declarations.
 */
interface StyleSheet {
    val styles: MutableList<StyleSheetObject>

    /** Adds a rule and returns the object stored in [styles]. */
    fun newStyle(
        target: StyleSheetTarget,
        style: UiStyle,
    ): StyleSheetObject = StyleSheetObject(target, style).also(styles::add)

    /** Adds a rule resolved from each matching element and returns the stored object. */
    fun newStyle(
        target: StyleSheetTarget,
        resolveStyle: (UiElement) -> UiStyle,
    ): StyleSheetObject = StyleSheetObject(target, resolveStyle).also(styles::add)
}

internal class StyleSheetElementContext(
    val element: UiElement,
    val parent: StyleSheetElementContext? = null,
    val previousSibling: StyleSheetElementContext? = null,
)

/** Style sheets paired with the selector context visible from their scope. */
internal data class StyleSheetScopeContext(
    val styleSheets: List<StyleSheet>,
    val context: StyleSheetElementContext,
)

/** Resolves all declarations matching [context] according to selector specificity and order. */
internal fun Iterable<StyleSheet>.styleFor(context: StyleSheetElementContext): UiStyle? =
    listOf(StyleSheetScopeContext(toList(), context)).scopedStyleFor()

/** Resolves declarations from multiple selector scopes as one ordered cascade. */
internal fun Iterable<StyleSheetScopeContext>.scopedStyleFor(): UiStyle? =
    flatMap { scope ->
        scope.styleSheets.flatMap(StyleSheet::styles).map { rule -> rule to scope.context }
    }
        .mapIndexedNotNull { sourceOrder, (rule, context) ->
            rule.target.matchSpecificity(context)?.let { specificity ->
                MatchedStyleSheetRule(
                    specificity = specificity,
                    sourceOrder = sourceOrder,
                    rule = rule,
                    element = context.element,
                )
            }
        }
        .sortedWith(
            compareBy<MatchedStyleSheetRule> { it.specificity }
                .thenBy(MatchedStyleSheetRule::sourceOrder),
        )
        .fold(null as UiStyle?) { resolved, matched ->
            val style = matched.rule.styleFor(matched.element)
            resolved?.withOverrides(style) ?: style
        }

private data class MatchedStyleSheetRule(
    val specificity: StyleSheetSpecificity,
    val sourceOrder: Int,
    val rule: StyleSheetObject,
    val element: UiElement,
)

private data class StyleSheetSpecificity(
    val idCount: Int = 0,
    val classCount: Int = 0,
    val tagCount: Int = 0,
) : Comparable<StyleSheetSpecificity> {
    override fun compareTo(other: StyleSheetSpecificity): Int {
        val idComparison = idCount.compareTo(other.idCount)
        if (idComparison != 0) return idComparison
        val classComparison = classCount.compareTo(other.classCount)
        return if (classComparison != 0) classComparison else tagCount.compareTo(other.tagCount)
    }

    operator fun plus(other: StyleSheetSpecificity): StyleSheetSpecificity =
        StyleSheetSpecificity(
            idCount = idCount + other.idCount,
            classCount = classCount + other.classCount,
            tagCount = tagCount + other.tagCount,
        )
}

private fun StyleSheetTarget.matchSpecificity(
    context: StyleSheetElementContext,
): StyleSheetSpecificity? = when (this) {
    is TargetClass -> StyleSheetSpecificity(classCount = 1)
        .takeIf { className in context.element.className }

    is TargetTag -> StyleSheetSpecificity(tagCount = 1)
        .takeIf { context.element.tag == tag }

    is TargetId -> StyleSheetSpecificity(idCount = 1)
        .takeIf { context.element.id == id }

    TargetWildcard -> StyleSheetSpecificity()
    TargetScope -> StyleSheetSpecificity(classCount = 1)
        .takeIf { context.parent == null }

    is TargetAnd -> matchAllSpecificity(context)
    is TargetOr -> targets.mapNotNull { it.matchSpecificity(context) }.maxOrNull()
    is TargetCombinator -> matchCombinatorSpecificity(context)
}

private fun TargetAnd.matchAllSpecificity(
    context: StyleSheetElementContext,
): StyleSheetSpecificity? {
    var specificity = StyleSheetSpecificity()
    targets.forEach { target ->
        specificity += target.matchSpecificity(context) ?: return null
    }
    return specificity
}

private fun TargetCombinator.matchCombinatorSpecificity(
    context: StyleSheetElementContext,
): StyleSheetSpecificity? {
    val rightSpecificity = right.matchSpecificity(context) ?: return null
    val leftSpecificity = relatedContexts(context)
        .mapNotNull(left::matchSpecificity)
        .maxOrNull()
        ?: return null
    return leftSpecificity + rightSpecificity
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
