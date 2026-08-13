package io.github.aiwao.mine2dengine.layout

/** Whether an available size is a concrete CSS definite size or an intrinsic constraint. */
internal sealed interface AvailableSize {
    data class Definite(
        val value: Float,
    ) : AvailableSize {
        init {
            require(value.isFinite() && value >= 0f) { "Available size must be non-negative: $value" }
        }
    }

    data object Indefinite : AvailableSize

    data object MinContent : AvailableSize

    data object MaxContent : AvailableSize
}

/** Input constraints for one CSS formatting context. */
internal data class ConstraintSpace(
    val availableWidth: AvailableSize,
    val availableHeight: AvailableSize,
    val percentageWidth: Float?,
    val percentageHeight: Float?,
    val forcedContentWidth: Float? = null,
    val forcedContentHeight: Float? = null,
    val isFlexItem: Boolean = false,
    val shrinkToFit: Boolean = false,
) {
    init {
        require(percentageWidth == null || percentageWidth.isFinite() && percentageWidth >= 0f)
        require(percentageHeight == null || percentageHeight.isFinite() && percentageHeight >= 0f)
        require(forcedContentWidth == null || forcedContentWidth.isFinite() && forcedContentWidth >= 0f)
        require(forcedContentHeight == null || forcedContentHeight.isFinite() && forcedContentHeight >= 0f)
    }
}

internal enum class CssBoxKind {
    PRINCIPAL,
    PSEUDO,
    ANONYMOUS,
}

/** One generated CSS box before layout. This is deliberately separate from [UiElement]. */
internal data class CssBox(
    val kind: CssBoxKind,
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
    val generatedContent: UiGeneratedContent? = null,
    val pseudoStyleProvider: (() -> UiPseudoStyle)? = null,
    val style: ResolvedUiStyle,
    val styleProvider: () -> ResolvedUiStyle,
    val textStyle: ResolvedUiTextStyle,
    val children: List<CssBox> = emptyList(),
    val text: String? = null,
    val sourceIndex: Int = 0,
    val suppressed: Boolean = false,
) {
    val isAbsolutelyPositioned: Boolean
        get() = style.position == UiPosition.ABSOLUTE

    val displayBox: UiDisplay.Box
        get() = requireNotNull(style.display.box) {
            "A generated CSS box must have an outer and inner display type: ${style.display}"
        }

    fun blockified(): CssBox {
        val display = style.display.box ?: return this
        if (display.outside == UiDisplayOutside.BLOCK) return this
        return copy(
            style = style.copy(
                display = UiDisplay.Box(UiDisplayOutside.BLOCK, display.inside),
            ),
        )
    }
}

/** Builds the CSS box tree after cascade, inheritance, pseudo generation, and display suppression. */
internal class CssBoxTreeBuilder(
    private val root: UiElement,
    styleSheets: Iterable<StyleSheet>,
    private val displayStates: MutableList<UiDisplayState>,
    private val evaluatedDisplays: Map<UiDisplayKey, Boolean>,
) {
    private val globalSheets = styleSheets.toList()

    fun build(): CssBox {
        val globalScope = StyleSheetScopeContext(
            styleSheets = globalSheets,
            context = StyleSheetElementContext(root),
        )
        val boxes = buildElement(
            element = root,
            globalStyleSheetScope = globalScope,
            scopedStyleSheetScopes = emptyList(),
            inheritedTextStyle = ResolvedUiTextStyle(),
            sourceIndex = 0,
            isRoot = true,
        )
        if (boxes.isEmpty()) {
            val declarationProvider = declarationProvider(root, globalScope, emptyList())
            val styleProvider = {
                declarationProvider().resolveDefaults(initialDisplay = userAgentDisplay(root))
            }
            val style = styleProvider().copy(display = UiDisplay.BLOCK)
            return CssBox(
                kind = CssBoxKind.PRINCIPAL,
                element = root,
                style = style,
                styleProvider = styleProvider,
                textStyle = style.resolveTextStyle(ResolvedUiTextStyle()),
                suppressed = true,
            )
        }
        // The root principal box is blockified by CSS Display. A display:contents root is wrapped
        // in an anonymous root box so the layout still has an initial containing block participant.
        return if (boxes.size == 1 && boxes.single().kind == CssBoxKind.PRINCIPAL) {
            boxes.single().blockified()
        } else {
            val declarationProvider = declarationProvider(root, globalScope, emptyList())
            val styleProvider = {
                declarationProvider().resolveDefaults(initialDisplay = UiDisplay.BLOCK)
                    .copy(display = UiDisplay.BLOCK)
            }
            val style = styleProvider()
            CssBox(
                kind = CssBoxKind.ANONYMOUS,
                element = root,
                style = style,
                styleProvider = styleProvider,
                textStyle = style.resolveTextStyle(ResolvedUiTextStyle()),
                children = boxes,
            )
        }
    }

    private fun buildElement(
        element: UiElement,
        globalStyleSheetScope: StyleSheetScopeContext,
        scopedStyleSheetScopes: List<StyleSheetScopeContext>,
        inheritedTextStyle: ResolvedUiTextStyle,
        sourceIndex: Int,
        isRoot: Boolean = false,
    ): List<CssBox> {
        val ownContainerScope = (element as? UiContainer)
            ?.styleSheets
            ?.takeIf(List<StyleSheet>::isNotEmpty)
            ?.let { styleSheets ->
                StyleSheetScopeContext(
                    styleSheets = styleSheets.toList(),
                    context = StyleSheetElementContext(element),
                )
            }
        val ownComponentScope = element.componentStyleSheets?.let { styleSheets ->
            StyleSheetScopeContext(
                styleSheets = styleSheets,
                context = StyleSheetElementContext(element),
            )
        }
        val ownScopes = listOfNotNull(ownContainerScope, ownComponentScope)
        val elementScopedScopes = scopedStyleSheetScopes + ownScopes
        val elementScopes = listOf(globalStyleSheetScope) + elementScopedScopes
        val declarationProvider = declarationProvider(element, globalStyleSheetScope, elementScopedScopes)
        val styleProvider = {
            declarationProvider().resolveDefaults(initialDisplay = userAgentDisplay(element))
        }
        var style = styleProvider()
        if (isRoot && style.display is UiDisplay.Box) {
            style = style.copy(
                display = UiDisplay.Box(UiDisplayOutside.BLOCK, style.display.inside),
            )
        }
        val displayKey = UiDisplayKey(element)
        val suppressesBox = { styleProvider().display == UiDisplay.NONE }
        val suppressed = evaluatedDisplays[displayKey] ?: suppressesBox()
        displayStates += UiDisplayState(displayKey, suppressesBox, suppressed)
        if (suppressed || style.display == UiDisplay.NONE) return emptyList()

        val textStyle = style.resolveTextStyle(inheritedTextStyle)
        val before = buildPseudo(
            element = element,
            pseudoElement = UiPseudoElement.BEFORE,
            elementScopes = elementScopes,
            inheritedTextStyle = textStyle,
            sourceIndex = -1,
        )

        val children = when (element) {
            is Paragraph -> emptyList()
            is InputControl -> emptyList()
            is UiContainer -> {
                val scopesForChildren = if (ownComponentScope == null) {
                    elementScopedScopes
                } else {
                    ownScopes
                }
                buildChildren(
                    element = element,
                    globalStyleSheetScope = globalStyleSheetScope,
                    scopedStyleSheetScopes = scopesForChildren,
                    inheritedTextStyle = textStyle,
                )
            }
        }
        val after = buildPseudo(
            element = element,
            pseudoElement = UiPseudoElement.AFTER,
            elementScopes = elementScopes,
            inheritedTextStyle = textStyle,
            sourceIndex = Int.MAX_VALUE,
        )
        val generatedChildren = listOfNotNull(before) + children + listOfNotNull(after)

        if (style.display == UiDisplay.CONTENTS) {
            val ownText = (element as? Paragraph)?.text
                ?.takeIf(String::isNotEmpty)
                ?.let { text ->
                    val anonymousStyle = style.copy(
                        border = UiBorders.NONE,
                        display = UiDisplay.INLINE,
                    )
                    CssBox(
                        kind = CssBoxKind.ANONYMOUS,
                        element = element,
                        style = anonymousStyle,
                        styleProvider = {
                            styleProvider().copy(
                                border = UiBorders.NONE,
                                display = UiDisplay.INLINE,
                            )
                        },
                        textStyle = textStyle,
                        text = text,
                        sourceIndex = sourceIndex,
                    )
                }
            return listOfNotNull(before, ownText) + children + listOfNotNull(after)
        }

        return listOf(
            CssBox(
                kind = CssBoxKind.PRINCIPAL,
                element = element,
                style = style,
                styleProvider = styleProvider,
                textStyle = textStyle,
                children = generatedChildren,
                text = (element as? Paragraph)?.text,
                sourceIndex = sourceIndex,
            ),
        )
    }

    private fun buildChildren(
        element: UiContainer,
        globalStyleSheetScope: StyleSheetScopeContext,
        scopedStyleSheetScopes: List<StyleSheetScopeContext>,
        inheritedTextStyle: ResolvedUiTextStyle,
    ): List<CssBox> {
        var previousGlobalSibling: StyleSheetElementContext? = null
        val previousScopedSiblings = MutableList<StyleSheetElementContext?>(
            scopedStyleSheetScopes.size,
        ) { null }
        return element.children.flatMapIndexed { index, child ->
            val childGlobalContext = StyleSheetElementContext(
                element = child,
                parent = globalStyleSheetScope.context,
                previousSibling = previousGlobalSibling,
            ).also { previousGlobalSibling = it }
            val childScopedScopes = scopedStyleSheetScopes.mapIndexed { scopeIndex, scope ->
                StyleSheetScopeContext(
                    styleSheets = scope.styleSheets,
                    context = StyleSheetElementContext(
                        element = child,
                        parent = scope.context,
                        previousSibling = previousScopedSiblings[scopeIndex],
                    ).also { previousScopedSiblings[scopeIndex] = it },
                )
            }
            buildElement(
                element = child,
                globalStyleSheetScope = StyleSheetScopeContext(
                    styleSheets = globalStyleSheetScope.styleSheets,
                    context = childGlobalContext,
                ),
                scopedStyleSheetScopes = childScopedScopes,
                inheritedTextStyle = inheritedTextStyle,
                sourceIndex = index,
            )
        }
    }

    private fun buildPseudo(
        element: UiElement,
        pseudoElement: UiPseudoElement,
        elementScopes: List<StyleSheetScopeContext>,
        inheritedTextStyle: ResolvedUiTextStyle,
        sourceIndex: Int,
    ): CssBox? {
        val pseudoProvider = elementScopes.scopedPseudoStyleFor(pseudoElement) ?: return null
        val styleProvider = {
            pseudoProvider().style.resolveDefaults(initialDisplay = UiDisplay.INLINE)
        }
        val style = styleProvider()
        val displayKey = UiDisplayKey(element, pseudoElement)
        val suppressesBox = { styleProvider().display == UiDisplay.NONE }
        val suppressed = evaluatedDisplays[displayKey] ?: suppressesBox()
        displayStates += UiDisplayState(displayKey, suppressesBox, suppressed)
        if (suppressed || style.display == UiDisplay.NONE) return null
        val pseudoStyle = pseudoProvider()
        if (style.display == UiDisplay.CONTENTS) {
            val text = (pseudoStyle.content as? UiGeneratedContent.Text)?.value ?: return null
            val anonymousStyle = UiStyle(display = UiDisplay.INLINE).resolveDefaults()
            return CssBox(
                kind = CssBoxKind.ANONYMOUS,
                element = element,
                pseudoElement = pseudoElement,
                generatedContent = pseudoStyle.content,
                style = anonymousStyle,
                styleProvider = { anonymousStyle },
                textStyle = style.resolveTextStyle(inheritedTextStyle),
                text = text,
                sourceIndex = sourceIndex,
            )
        }
        return CssBox(
            kind = CssBoxKind.PSEUDO,
            element = element,
            pseudoElement = pseudoElement,
            generatedContent = pseudoStyle.content,
            pseudoStyleProvider = pseudoProvider,
            style = style,
            styleProvider = styleProvider,
            textStyle = style.resolveTextStyle(inheritedTextStyle),
            text = (pseudoStyle.content as? UiGeneratedContent.Text)?.value,
            sourceIndex = sourceIndex,
        )
    }

    private fun declarationProvider(
        element: UiElement,
        globalStyleSheetScope: StyleSheetScopeContext,
        scopedStyleSheetScopes: List<StyleSheetScopeContext>,
    ): () -> UiStyle {
        val scopes = listOf(globalStyleSheetScope) + scopedStyleSheetScopes
        return {
            val userAgent = userAgentStyleFor(element)
            val author = scopes.scopedStyleFor()
            val cascaded = author?.let(userAgent::withOverrides) ?: userAgent
            cascaded.withOverrides(element.style)
        }
    }

    private fun userAgentDisplay(element: UiElement): UiDisplay =
        userAgentStyleFor(element).display ?: UiDisplay.INLINE

}
