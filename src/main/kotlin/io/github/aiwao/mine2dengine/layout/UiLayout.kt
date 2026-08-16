package io.github.aiwao.mine2dengine.layout

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DFont
import io.github.aiwao.mine2dengine.Mine2DMaterial
import io.github.aiwao.mine2dengine.Mine2DMaterials
import io.github.aiwao.mine2dengine.Mine2DRoundedRectRadii
import io.github.aiwao.mine2dengine.Mine2DUniformRect
import io.github.aiwao.mine2dengine.Mine2DVertex
import io.github.aiwao.mine2dengine.inset
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal data class ColorPickerGeometry(
    val bounds: UiRect,
    val saturationValueBounds: UiRect,
    val hueBounds: UiRect,
)

internal data class RangeInputGeometry(
    val trackBounds: UiRect,
    val activeTrackBounds: UiRect,
    val thumbCenterX: Float,
    val thumbCenterY: Float,
    val thumbRadius: Float,
)

internal fun rangeInputGeometry(
    input: RangeInput<*>,
    bounds: UiRect,
): RangeInputGeometry {
    val thumbRadius = min(
        RangeInput.THUMB_RADIUS,
        min(bounds.width, bounds.height) / 2f,
    ).coerceAtLeast(0f)
    val fraction = input.fraction().toFloat()
    return when (input.orientation) {
        RangeOrientation.HORIZONTAL -> {
            val thumbStart = bounds.left + thumbRadius
            val thumbTravel = (bounds.width - thumbRadius * 2f).coerceAtLeast(0f)
            val centerX = thumbStart + thumbTravel * fraction
            val centerY = bounds.top + bounds.height / 2f
            val trackThickness = min(RangeInput.TRACK_THICKNESS, bounds.height)
            val trackTop = centerY - trackThickness / 2f
            RangeInputGeometry(
                trackBounds = UiRect(bounds.left, trackTop, bounds.width, trackThickness),
                activeTrackBounds = UiRect(
                    bounds.left,
                    trackTop,
                    centerX - bounds.left,
                    trackThickness,
                ),
                thumbCenterX = centerX,
                thumbCenterY = centerY,
                thumbRadius = thumbRadius,
            )
        }

        RangeOrientation.VERTICAL -> {
            val thumbStart = bounds.top + thumbRadius
            val thumbTravel = (bounds.height - thumbRadius * 2f).coerceAtLeast(0f)
            val centerX = bounds.left + bounds.width / 2f
            val thumbEnd = thumbStart + thumbTravel
            val centerY = thumbEnd - thumbTravel * fraction
            val trackThickness = min(RangeInput.TRACK_THICKNESS, bounds.width)
            val trackLeft = centerX - trackThickness / 2f
            RangeInputGeometry(
                trackBounds = UiRect(trackLeft, bounds.top, trackThickness, bounds.height),
                activeTrackBounds = UiRect(
                    trackLeft,
                    centerY,
                    trackThickness,
                    bounds.bottom - centerY,
                ),
                thumbCenterX = centerX,
                thumbCenterY = centerY,
                thumbRadius = thumbRadius,
            )
        }
    }
}

internal fun rangeInputFractionAt(
    input: RangeInput<*>,
    bounds: UiRect,
    pointerX: Float,
    pointerY: Float,
): Double {
    val geometry = rangeInputGeometry(input, bounds)
    val fraction = when (input.orientation) {
        RangeOrientation.HORIZONTAL -> {
            val travel = (geometry.trackBounds.width - geometry.thumbRadius * 2f)
                .coerceAtLeast(0f)
            if (travel == 0f) return input.fraction()
            val start = geometry.trackBounds.left + geometry.thumbRadius
            ((pointerX - start) / travel).coerceIn(0f, 1f)
        }

        RangeOrientation.VERTICAL -> {
            val travel = (geometry.trackBounds.height - geometry.thumbRadius * 2f)
                .coerceAtLeast(0f)
            if (travel == 0f) return input.fraction()
            val end = geometry.trackBounds.bottom - geometry.thumbRadius
            ((end - pointerY) / travel).coerceIn(0f, 1f)
        }
    }
    return fraction.toDouble()
}

private enum class ColorPickerDragTarget {
    SATURATION_VALUE,
    HUE,
}

private const val COLOR_PICKER_PADDING = 6f
private const val COLOR_PICKER_GAP = 6f
private const val COLOR_PICKER_ANCHOR_GAP = 2f
private const val COLOR_PICKER_SATURATION_VALUE_WIDTH = 96f
private const val COLOR_PICKER_HUE_WIDTH = 12f
private const val COLOR_PICKER_HEIGHT = 76f
private const val SCROLL_WHEEL_STEP = 10f

internal data class UiDisplayKey(
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
)

internal data class UiDisplayState(
    val key: UiDisplayKey,
    val predicate: () -> Boolean,
    val suppressed: Boolean,
)

private data class UiScrollKey(
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
)

private data class UiScrollTarget(
    val key: UiScrollKey,
    val overflow: ResolvedUiOverflow,
    val maximumX: Float,
    val maximumY: Float,
)

/** A rectangular clip whose axes may be constrained independently. */
private data class UiAxisClip(
    val left: Float? = null,
    val right: Float? = null,
    val top: Float? = null,
    val bottom: Float? = null,
) {
    fun intersect(other: UiAxisClip): UiAxisClip? {
        val result = UiAxisClip(
            left = maxNullable(left, other.left),
            right = minNullable(right, other.right),
            top = maxNullable(top, other.top),
            bottom = minNullable(bottom, other.bottom),
        )
        if (result.left != null && result.right != null && result.right <= result.left) return null
        if (result.top != null && result.bottom != null && result.bottom <= result.top) return null
        return result
    }

    fun clip(bounds: UiRect): UiRect? {
        val clippedLeft = left?.let { maxOf(bounds.left, it) } ?: bounds.left
        val clippedRight = right?.let { minOf(bounds.right, it) } ?: bounds.right
        val clippedTop = top?.let { maxOf(bounds.top, it) } ?: bounds.top
        val clippedBottom = bottom?.let { minOf(bounds.bottom, it) } ?: bounds.bottom
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
        return UiRect(
            clippedLeft,
            clippedTop,
            clippedRight - clippedLeft,
            clippedBottom - clippedTop,
        )
    }

    fun contains(x: Float, y: Float): Boolean =
        (left == null || x >= left) &&
            (right == null || x < right) &&
            (top == null || y >= top) &&
            (bottom == null || y < bottom)

    private fun maxNullable(first: Float?, second: Float?): Float? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private fun minNullable(first: Float?, second: Float?): Float? = when {
        first == null -> second
        second == null -> first
        else -> minOf(first, second)
    }
}

private data class UiClipStack(
    val axisClip: UiAxisClip,
    val roundedClips: List<UiRoundedBox> = emptyList(),
) {
    fun intersect(other: UiClipStack): UiClipStack? {
        val intersection = axisClip.intersect(other.axisClip) ?: return null
        return UiClipStack(
            axisClip = intersection,
            roundedClips = roundedClips + other.roundedClips,
        )
    }

    fun clip(bounds: UiRect): UiRect? = axisClip.clip(bounds)

    fun contains(x: Float, y: Float): Boolean =
        axisClip.contains(x, y) && roundedClips.all { clip -> clip.contains(x, y) }
}

internal data class UiLayoutSnapshot(
    val root: UiLayoutNode,
    val rootFragment: UiBoxFragment,
    val displayStates: List<UiDisplayState>,
)

/** One generated CSS box fragment in the final layout tree. */
data class UiBoxFragment(
    val element: UiElement,
    val pseudoElement: UiPseudoElement? = null,
    val marginBox: UiRect,
    val borderBox: UiRect,
    val paddingBox: UiRect,
    val contentBox: UiRect,
    val children: List<UiBoxFragment>,
    /** False for an anonymous box or a root retained only as a `display: none` handle. */
    val generatesBox: Boolean = true,
    /** Layout overflow before a mutable scroll offset is applied. */
    val scrollableOverflow: UiRect = paddingBox,
)

/** One line fragment produced while laying out text. */
data class UiTextLayoutFragment(
    val text: String,
    val bounds: UiRect,
)

/** The current physical scroll offset of one generated principal box. */
data class UiScrollOffset(
    val x: Float = 0f,
    val y: Float = 0f,
)

internal data class UiStyledTextLayoutFragment(
    val fragment: UiTextLayoutFragment,
    /** Null means that the element's current inherited text style should be used at paint time. */
    val textStyle: ResolvedUiTextStyle?,
)

/** The calculated geometry for one UI element. */
data class UiLayoutNode(
    val element: UiElement,
    /** Includes the element's margin and begins at its layout coordinate. */
    val outerBounds: UiRect,
    /** The rectangle painted when the element has a background color. */
    val bounds: UiRect,
    /** The area available to text or children after padding. */
    val contentBounds: UiRect,
    val children: List<UiLayoutNode>,
    /** The font resolved from this element's style and its ancestors. */
    val font: Mine2DFont? = null,
    /** The text color resolved from this element's style and its ancestors. */
    val color: Int = UiStyle.DEFAULT_COLOR,
    /** The configurable text shadow resolved from this element's style and its ancestors. */
    val textShadow: UiTextShadow? = null,
    /** The text alignment resolved from this element's style and its ancestors. */
    val textAlign: UiTextAlign = UiTextAlign.START,
    /** Whether this node generated a layout box. */
    internal val displayed: Boolean = true,
    internal val beforePseudo: UiPseudoLayoutNode? = null,
    internal val afterPseudo: UiPseudoLayoutNode? = null,
    internal val textBounds: UiRect? = null,
    internal val textFragments: List<UiTextLayoutFragment> = emptyList(),
    /** The box's padding edge, which is also its overflow clip edge. */
    val paddingBounds: UiRect = bounds,
    /** The complete layout overflow before scrolling and clipping. */
    val scrollableOverflowBounds: UiRect = paddingBounds,
    internal val overflow: ResolvedUiOverflow = ResolvedUiOverflow(
        UiOverflowValue.VISIBLE,
        UiOverflowValue.VISIBLE,
    ),
) {
    internal var styleProvider: () -> ResolvedUiStyle = { element.style.resolveDefaults() }
    internal var styledTextFragments: List<UiStyledTextLayoutFragment> = emptyList()

    /** Largest supported positive horizontal scroll offset for this layout snapshot. */
    val maximumScrollX: Float
        get() = (scrollableOverflowBounds.right - paddingBounds.right).coerceAtLeast(0f)

    /** Largest supported positive vertical scroll offset for this layout snapshot. */
    val maximumScrollY: Float
        get() = (scrollableOverflowBounds.bottom - paddingBounds.bottom).coerceAtLeast(0f)
}

/** Calculated geometry and content for one generated pseudo-element box. */
data class UiPseudoLayoutNode(
    /** The author-created element which generated this box. */
    val element: UiElement,
    val pseudoElement: UiPseudoElement,
    /** Includes the generated box's margin. */
    val outerBounds: UiRect,
    /** The rectangle painted by the generated box's background. */
    val bounds: UiRect,
    /** The generated text area after padding. */
    val contentBounds: UiRect,
    /** The content captured by the layout pass. */
    val content: UiGeneratedContent,
    /** The font resolved from the pseudo-element style and its originating element. */
    val font: Mine2DFont?,
    val displayed: Boolean = true,
    internal val textFragments: List<UiTextLayoutFragment> = emptyList(),
    /** The generated box's overflow clip edge. */
    val paddingBounds: UiRect = bounds,
    /** The complete generated layout overflow before scrolling and clipping. */
    val scrollableOverflowBounds: UiRect = paddingBounds,
    internal val overflow: ResolvedUiOverflow = ResolvedUiOverflow(
        UiOverflowValue.VISIBLE,
        UiOverflowValue.VISIBLE,
    ),
) {
    internal var pseudoStyleProvider: () -> UiPseudoStyle = { UiPseudoStyle(content) }

    internal val maximumScrollX: Float
        get() = (scrollableOverflowBounds.right - paddingBounds.right).coerceAtLeast(0f)

    internal val maximumScrollY: Float
        get() = (scrollableOverflowBounds.bottom - paddingBounds.bottom).coerceAtLeast(0f)
}

/**
 * A layout result that renders and dispatches pointer, keyboard, character, and IME input.
 *
 * It is also a Minecraft renderable widget and may be registered directly with a Screen.
 */
class UiLayout internal constructor(
    snapshot: UiLayoutSnapshot,
    viewport: UiRect,
    private val snapshotCalculator: (
        UiRect,
        Map<UiDisplayKey, Boolean>,
    ) -> UiLayoutSnapshot,
    private val textMeasurer: (UiElement, Mine2DFont?) -> UiTextMeasurer,
    private val componentRuntime: UiComponentRuntime,
) : Renderable, GuiEventListener, NarratableEntry {
    /** The current initial containing block used by CSS layout. */
    var viewport: UiRect = viewport
        private set

    var root: UiLayoutNode = snapshot.root
        private set

    /** The CSS fragment tree, including pseudo-elements and anonymous layout boxes. */
    var rootFragment: UiBoxFragment = snapshot.rootFragment
        private set

    private var displayStates: List<UiDisplayState> = snapshot.displayStates
    private val scrollOffsets = mutableMapOf<UiScrollKey, UiScrollOffset>()

    private var dragButtonInfo: MouseButtonInfo? = null
    private var screenFocused: Boolean = false
    private var pendingNavigationFocus: UiElement? = null
    private var colorPickerDragTarget: ColorPickerDragTarget? = null
    private var lastPointerX: Double? = null
    private var lastPointerY: Double? = null
    private var pointerGeometryDirty: Boolean = true
    internal var textInputFocusNotifier: (Boolean) -> Unit = { focused ->
        Minecraft.getInstance().onTextInputFocusChange(this, focused)
    }

    /** The element that currently receives keyboard input, or null when nothing is focused. */
    var focusedElement: UiElement? = null
        private set

    /** The viewport's left coordinate. Changing it translates the complete layout. */
    var left: Float
        get() = viewport.left
        set(value) {
            updateViewport(viewport.copy(left = value))
        }

    /** The viewport's top coordinate. Changing it translates the complete layout. */
    var top: Float
        get() = viewport.top
        set(value) {
            updateViewport(viewport.copy(top = value))
        }

    val size: UiSize
        get() {
            refreshDisplay()
            return UiSize(root.outerBounds.width, root.outerBounds.height)
        }

    /**
     * Changes the initial containing block and updates this layout synchronously.
     *
     * An origin-only change translates the existing geometry. A width or height change rebuilds
     * the CSS box and fragment trees so percentages, wrapping, flex sizing, and positioned boxes
     * use the new available size. The previous viewport and snapshot remain installed if layout
     * calculation fails. Previously obtained node and fragment objects are snapshots and must be
     * queried again after this method returns.
     *
     * This method does not dispatch pointer callbacks. Existing drag and hover state is retained
     * for elements that still generate boxes and cleared for elements removed by the new layout.
     */
    fun updateViewport(viewport: UiRect) {
        componentRuntime.checkNotRendering("updateViewport")
        componentRuntime.flushUpdates()
        val previous = this.viewport
        if (viewport == previous) return

        if (viewport.width == previous.width && viewport.height == previous.height) {
            val deltaX = viewport.left - previous.left
            val deltaY = viewport.top - previous.top
            val translatedRoot = root.translated(deltaX, deltaY)
            val translatedRootFragment = rootFragment.translated(deltaX, deltaY)
            root = translatedRoot
            rootFragment = translatedRootFragment
            this.viewport = viewport
            pointerGeometryDirty = true
            return
        }

        val snapshot = snapshotCalculator(viewport, emptyMap())
        applySnapshot(snapshot)
        this.viewport = viewport
    }

    /**
     * Rebuilds this layout synchronously using the current [viewport].
     *
     * Use this after changing styles other than `display`, text, style sheets, or children.
     * Dynamic transitions to or from `display: none` continue to refresh automatically before
     * rendering and pointer operations.
     */
    fun relayout() {
        componentRuntime.checkNotRendering("relayout")
        if (componentRuntime.flushUpdates()) return
        val snapshot = snapshotCalculator(viewport, emptyMap())
        applySnapshot(snapshot)
    }

    /** Commits all pending state updates and performs at most one CSS layout calculation. */
    fun flushUpdates() {
        componentRuntime.checkNotRendering("flushUpdates")
        componentRuntime.flushUpdates()
    }

    internal fun recalculateForStateUpdate() {
        val snapshot = snapshotCalculator(viewport, emptyMap())
        applySnapshot(snapshot)
    }

    /** Gives keyboard focus to [element]. Passing null clears the current focus. */
    fun focus(element: UiElement?): Boolean = dispatchEvent {
        refreshDisplay()
        focusInternal(element)
    }

    /** Clears keyboard focus and commits the focused input's current change, if any. */
    fun clearFocus() {
        focus(null)
    }

    private fun focusInternal(
        element: UiElement?,
        notifyPlatform: Boolean = true,
    ): Boolean {
        val next = element
        if (
            next != null &&
            (next.tabIndex == null || next.disabled || nodesInPaintOrder().none { node ->
                node.displayed && node.element === next
            })
        ) {
            return false
        }

        val previous = focusedElement
        if (previous === next) return true

        val previouslyUsedPlatformTextInput = previous?.usesPlatformTextInput == true
        val nextUsesPlatformTextInput = next?.usesPlatformTextInput == true
        focusedElement = next
        previous?.focusLost()
        next?.focusGained()
        colorPickerDragTarget = null

        if (
            notifyPlatform &&
            screenFocused &&
            previouslyUsedPlatformTextInput != nextUsesPlatformTextInput
        ) {
            notifyPlatformTextInputFocus(nextUsesPlatformTextInput)
        }
        return true
    }

    private fun notifyPlatformTextInputFocus(focused: Boolean) {
        textInputFocusNotifier(focused)
    }

    private fun refreshFocusValidity() {
        val focused = focusedElement ?: return
        if (
            focused.tabIndex == null ||
            focused.disabled ||
            nodesInPaintOrder().none { node -> node.displayed && node.element === focused }
        ) {
            focusInternal(null)
        }
    }

    private fun focusableElements(): List<UiElement> {
        val elements = nodesInPaintOrder()
            .asSequence()
            .filter(UiLayoutNode::displayed)
            .map(UiLayoutNode::element)
            .filter { element ->
                val tabIndex = element.tabIndex
                tabIndex != null && tabIndex >= 0 && !element.disabled
            }
            .distinct()
            .toList()
        return elements
            .filter { element -> checkNotNull(element.tabIndex) > 0 }
            .sortedBy { element -> checkNotNull(element.tabIndex) } +
            elements.filter { element -> element.tabIndex == 0 }
    }

    /** Allows the complete layout to be registered with `Screen.addRenderableWidget`. */
    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (
            pointerGeometryDirty ||
            lastPointerX != mouseX.toDouble() ||
            lastPointerY != mouseY.toDouble()
        ) {
            mouseMove(mouseX.toDouble(), mouseY.toDouble())
        }
        render(Mine2DEngine(graphics))
    }

    override fun setFocused(focused: Boolean): Unit = dispatchEvent {
        if (!focused) {
            val hadTextFocus = focusedElement?.usesPlatformTextInput == true
            val wasScreenFocused = screenFocused
            screenFocused = false
            focusInternal(null, notifyPlatform = false)
            if (wasScreenFocused && hadTextFocus) notifyPlatformTextInputFocus(false)
            return
        }

        if (screenFocused && pendingNavigationFocus == null) return
        screenFocused = true
        pendingNavigationFocus?.let { target ->
            focusInternal(target, notifyPlatform = false)
        }
        pendingNavigationFocus = null
        if (focusedElement?.usesPlatformTextInput == true) {
            notifyPlatformTextInputFocus(true)
        }
    }

    override fun isFocused(): Boolean = screenFocused

    override fun isMouseOver(x: Double, y: Double): Boolean {
        refreshDisplay()
        val layoutX = x.toFloat()
        val layoutY = y.toFloat()
        return openColorPickerGeometry()?.bounds?.contains(layoutX, layoutY) == true ||
            hitRegionsInPaintOrder().any { region -> region.contains(layoutX, layoutY) }
    }

    override fun getRectangle(): ScreenRectangle {
        refreshDisplay()
        val bounds = root.bounds
        val left = floor(bounds.left.toDouble()).toInt()
        val top = floor(bounds.top.toDouble()).toInt()
        return ScreenRectangle(
            left,
            top,
            (ceil(bounds.right.toDouble()).toInt() - left).coerceAtLeast(0),
            (ceil(bounds.bottom.toDouble()).toInt() - top).coerceAtLeast(0),
        )
    }

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
        refreshDisplay()
        refreshFocusValidity()
        val elements = focusableElements()
        if (elements.isEmpty()) return null

        val forward = when (event) {
            is FocusNavigationEvent.TabNavigation -> event.forward()
            is FocusNavigationEvent.ArrowNavigation -> event.direction().isPositive
            else -> true
        }
        val currentIndex = elements.indexOfFirst { element -> element === focusedElement }
        val nextIndex = when {
            currentIndex < 0 -> if (forward) 0 else elements.lastIndex
            forward -> currentIndex + 1
            else -> currentIndex - 1
        }
        if (nextIndex !in elements.indices) return null

        pendingNavigationFocus = elements[nextIndex]
        return ComponentPath.leaf(this)
    }

    override fun narrationPriority(): NarratableEntry.NarrationPriority = when {
        focusedElement is InputControl -> NarratableEntry.NarrationPriority.FOCUSED
        nodesInPaintOrder().any { node -> node.element is InputControl && node.element.hovering } ->
            NarratableEntry.NarrationPriority.HOVERED

        else -> NarratableEntry.NarrationPriority.NONE
    }

    override fun updateNarration(output: NarrationElementOutput) {
        val input = (focusedElement as? InputControl) ?: nodesInPaintOrder()
            .asReversed()
            .map(UiLayoutNode::element)
            .filterIsInstance<InputControl>()
            .firstOrNull(UiElement::hovering)
            ?: return
        output.add(NarratedElementType.TITLE, Component.literal(input.narration()))
    }

    /** Renders this layout, recalculating geometry when a none-display value changes. */
    fun render(renderer: Mine2DEngine) {
        refreshDisplay()
        refreshFocusValidity()
        draw(root, renderer, ResolvedUiTextStyle(), renderer.uniformTimeSeconds())
        drawOpenColorPicker(renderer)
    }

    /** Moves this layout to [left], [top], then renders it without recalculating its size. */
    fun render(renderer: Mine2DEngine, left: Float, top: Float) {
        updateViewport(viewport.copy(left = left, top = top))
        render(renderer)
    }

    /** Finds the deepest element at the given GUI coordinate. */
    fun elementAt(x: Float, y: Float): UiElement? {
        refreshDisplay()
        val openColorInput = openColorInput()
        if (openColorInput != null && openColorPickerGeometry()?.bounds?.contains(x, y) == true) {
            return openColorInput
        }
        return hitRegionsInPaintOrder()
            .asReversed()
            .firstOrNull { region -> region.contains(x, y) }
            ?.element
    }

    /**
     * Invokes the topmost clickable element at the GUI coordinate in [event].
     * Elements with an [UiElement.onClick] or [UiElement.onDrag] callback and focusable elements
     * are clickable. The hit element starts dragging until [mouseRelease] is called. Its
     * [UiElement.onClick] callback is not invoked while [UiElement.disabled] is true. Returns true
     * when one was hit.
     */
    fun mouseClick(event: MouseButtonEvent): Boolean = mouseClick(event, doubleClick = false)

    /** Dispatches a pointer press, including the standard text-input focus and selection behavior. */
    fun mouseClick(event: MouseButtonEvent, doubleClick: Boolean): Boolean = dispatchEvent {
        refreshDisplay()
        refreshFocusValidity()
        val x = event.x().toFloat()
        val y = event.y().toFloat()
        val nodes = nodesInPaintOrder()
        val openColorInput = openColorInput()
        val pickerGeometry = openColorPickerGeometry()
        if (openColorInput != null && pickerGeometry != null) {
            if (pickerGeometry.bounds.contains(x, y)) {
                nodes.forEach { node -> node.element.dragging = false }
                dragButtonInfo = null
                colorPickerDragTarget = null
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    colorPickerDragTarget = when {
                        pickerGeometry.saturationValueBounds.contains(x, y) ->
                            ColorPickerDragTarget.SATURATION_VALUE

                        pickerGeometry.hueBounds.contains(x, y) -> ColorPickerDragTarget.HUE
                        else -> null
                    }
                    updateColorPickerFromPointer(openColorInput, pickerGeometry, x, y)
                }
                return true
            }
            openColorInput.commitPicker()
            colorPickerDragTarget = null
        }
        val hitRegions = hitRegionsInPaintOrder()
        val hitRegion = hitRegions.asReversed()
            .firstOrNull { region ->
                (region.element.onClick != null ||
                    region.element.onDrag != null ||
                    region.element.tabIndex != null) &&
                    region.contains(x, y)
            }
        val element = hitRegion?.element
        val focusTarget = element?.takeIf { candidate ->
            candidate.tabIndex != null && !candidate.disabled
        }
        focusInternal(focusTarget)
        element ?: return false

        nodes.forEach { node -> node.element.dragging = false }
        dragButtonInfo = null
        if (element.disabled && element.tabIndex != null) return true

        element.dragging = true
        dragButtonInfo = event.buttonInfo()
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            when (element) {
                is TextInput -> hitRegion
                    .takeIf { region -> region.node.element === element }
                    ?.let { region ->
                        val index = textInputIndexAt(
                            region.node,
                            event.x().toFloat() - region.visualOffsetX,
                        )
                        if (doubleClick) {
                            element.selectWordAt(index)
                        } else {
                            element.moveTo(index, extendSelection = event.hasShiftDown())
                        }
                    }

                is ColorInput -> element.openPicker()
                is RangeInput<*> -> hitRegion
                    .takeIf { region -> region.node.element === element }
                    ?.let { region ->
                        element.beginUserEdit()
                        updateRangeInputFromPointer(
                            input = element,
                            node = region.node,
                            pointerX = x - region.visualOffsetX,
                            pointerY = y - region.visualOffsetY,
                        )
                    }

                else -> Unit
            }
        }
        if (!element.disabled) {
            element.onClick?.invoke(event)
        }
        true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        mouseClick(event, doubleClick)

    /**
     * Updates [UiElement.hovering] and invokes mouse-over or mouse-out callbacks, invokes the
     * topmost [UiElement.onMouseMove] callback at [x], [y], then invokes [UiElement.onDrag] on the
     * dragging element with a [MouseButtonEvent] containing the current coordinates and the
     * button information from [mouseClick], even when the pointer is outside its bounds. Returns
     * true when at least one callback was invoked.
     */
    fun mouseMove(x: Double, y: Double): Boolean = dispatchEvent {
        refreshDisplay()
        refreshFocusValidity()
        lastPointerX = x
        lastPointerY = y
        pointerGeometryDirty = false
        val layoutX = x.toFloat()
        val layoutY = y.toFloat()
        val nodes = nodesInPaintOrder()
        val hitRegions = hitRegionsInPaintOrder()
        fun contains(element: UiElement): Boolean = hitRegions.any { region ->
            region.element === element && region.contains(layoutX, layoutY)
        }
        var handled = false

        val pickerInput = openColorInput()
        val pickerGeometry = openColorPickerGeometry()
        if (
            pickerInput != null &&
            pickerGeometry != null &&
            colorPickerDragTarget != null
        ) {
            updateColorPickerFromPointer(pickerInput, pickerGeometry, layoutX, layoutY)
            handled = true
        }

        nodes
            .asReversed()
            .filter { node -> node.element.hovering && !contains(node.element) }
            .forEach { node ->
                val onMouseOut = node.element.onMouseOut
                onMouseOut?.invoke()
                handled = handled || onMouseOut != null
                node.element.hovering = false
            }

        nodes
            .filter { node -> !node.element.hovering && contains(node.element) }
            .forEach { node ->
                val onMouseOver = node.element.onMouseOver
                onMouseOver?.invoke()
                handled = handled || onMouseOver != null
                node.element.hovering = true
            }

        hitRegions
            .asReversed()
            .firstOrNull { region ->
                region.element.onMouseMove != null &&
                    region.contains(layoutX, layoutY)
            }
            ?.element
            ?.onMouseMove
            ?.let { onMouseMove ->
                onMouseMove(x, y)
                handled = true
            }

        val draggingNode = nodes
            .asReversed()
            .firstOrNull { node -> node.element.dragging }
        draggingNode?.let { node ->
            val element = node.element
            if (
                element is TextInput &&
                !element.disabled &&
                dragButtonInfo?.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) {
                val visualOffsetX = visualOffsetOf(node)?.first ?: 0f
                val index = textInputIndexAt(
                    node,
                    layoutX - visualOffsetX,
                    allowScroll = true,
                )
                element.moveTo(index, extendSelection = true)
                handled = true
            }
            if (
                element is RangeInput<*> &&
                !element.disabled &&
                dragButtonInfo?.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
            ) {
                val (visualOffsetX, visualOffsetY) = visualOffsetOf(node) ?: (0f to 0f)
                updateRangeInputFromPointer(
                    input = element,
                    node = node,
                    pointerX = layoutX - visualOffsetX,
                    pointerY = layoutY - visualOffsetY,
                )
                handled = true
            }
            element.onDrag?.let { onDrag ->
                val buttonInfo = checkNotNull(dragButtonInfo) {
                    "A dragging element must have mouse button information"
                }
                onDrag(MouseButtonEvent(x, y, buttonInfo))
                handled = true
            }
        }

        handled
    }

    override fun mouseMoved(x: Double, y: Double) {
        mouseMove(x, y)
    }

    override fun mouseDragged(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ): Boolean = mouseMove(event.x(), event.y())

    /** Scrolls the deepest eligible box under the pointer, then chains to its ancestors. */
    override fun mouseScrolled(
        x: Double,
        y: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean = dispatchEvent {
        refreshDisplay()
        if (!horizontalAmount.isFinite() || !verticalAmount.isFinite()) return false
        val pointerX = x.toFloat()
        val pointerY = y.toFloat()
        if (openColorPickerGeometry()?.bounds?.contains(pointerX, pointerY) == true) return true
        val hit = hitRegionsInPaintOrder()
            .asReversed()
            .firstOrNull { region -> region.contains(pointerX, pointerY) }
            ?: return false
        val deltaX = (-horizontalAmount * SCROLL_WHEEL_STEP).toFloat()
        val deltaY = (-verticalAmount * SCROLL_WHEEL_STEP).toFloat()
        hit.scrollChain.asReversed().distinctBy(UiScrollTarget::key).forEach { target ->
            val current = scrollOffsets[target.key] ?: UiScrollOffset()
            if (
                setScrollOffset(
                    target = target,
                    x = current.x + deltaX,
                    y = current.y + deltaY,
                    directUserInput = true,
                )
            ) {
                return true
            }
        }
        return false
    }

    /** Stops the current drag. Returns true when an element was dragging. */
    fun mouseRelease(): Boolean = dispatchEvent {
        refreshDisplay()
        val wasDraggingPicker = colorPickerDragTarget != null
        colorPickerDragTarget = null
        val draggingElements = nodesInPaintOrder()
            .map(UiLayoutNode::element)
            .filter(UiElement::dragging)
            .distinct()
        if (draggingElements.isEmpty()) {
            dragButtonInfo = null
            return wasDraggingPicker
        }

        draggingElements.forEach { element ->
            if (element is RangeInput<*>) element.commitUserEdit()
            element.dragging = false
        }
        dragButtonInfo = null
        true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean = mouseRelease()

    /** Dispatches a key press and then notifies the element that owned focus. */
    override fun keyPressed(event: KeyEvent): Boolean = dispatchEvent {
        refreshDisplay()
        refreshFocusValidity()
        val focused = focusedElement

        val handled = when {
            event.isCycleFocus() -> cycleFocus(event)
            focused is ColorInput -> colorInputKeyPressed(focused, event)
            focused is RangeInput<*> -> rangeInputKeyPressed(focused, event)
            focused is TextInput -> textInputKeyPressed(focused, event)
            else -> false
        }
        if (focused?.disabled == false) focused.onKeyPressed?.invoke(event)
        handled
    }

    private fun cycleFocus(event: KeyEvent): Boolean {
        if (screenFocused) return false
        val elements = focusableElements()
        if (elements.isEmpty()) return false
        val currentIndex = elements.indexOfFirst { element -> element === focusedElement }
        val nextIndex = if (event.hasShiftDown()) {
            if (currentIndex <= 0) elements.lastIndex else currentIndex - 1
        } else {
            if (currentIndex < 0 || currentIndex == elements.lastIndex) 0 else currentIndex + 1
        }
        return focusInternal(elements[nextIndex])
    }

    private fun textInputKeyPressed(input: TextInput, event: KeyEvent): Boolean {
        if (event.isSelectAll()) {
            input.selectAll()
            return true
        }
        if (event.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(input.selectedText)
            return true
        }
        if (event.isPaste()) {
            if (!input.readOnly) {
                input.insertUserText(Minecraft.getInstance().keyboardHandler.clipboard)
            }
            return true
        }
        if (event.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(input.selectedText)
            if (!input.readOnly) input.cutSelection()
            return true
        }

        val extendSelection = event.hasShiftDown()
        val byWord = event.hasControlDownWithQuirk()
        return when (event.key()) {
            GLFW.GLFW_KEY_LEFT -> {
                input.moveLeft(extendSelection, byWord)
                true
            }

            GLFW.GLFW_KEY_RIGHT -> {
                input.moveRight(extendSelection, byWord)
                true
            }

            GLFW.GLFW_KEY_HOME -> {
                input.moveToStart(extendSelection)
                true
            }

            GLFW.GLFW_KEY_END -> {
                input.moveToEnd(extendSelection)
                true
            }

            GLFW.GLFW_KEY_BACKSPACE -> {
                if (!input.readOnly) input.deleteBackward(byWord)
                true
            }

            GLFW.GLFW_KEY_DELETE -> {
                if (!input.readOnly) input.deleteForward(byWord)
                true
            }

            else -> false
        }
    }

    private fun rangeInputKeyPressed(input: RangeInput<*>, event: KeyEvent): Boolean =
        when (event.key()) {
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> {
                input.adjustFromKeyboard(-1)
                true
            }

            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> {
                input.adjustFromKeyboard(1)
                true
            }

            GLFW.GLFW_KEY_HOME -> {
                input.setToMinimumFromKeyboard()
                true
            }

            GLFW.GLFW_KEY_END -> {
                input.setToMaximumFromKeyboard()
                true
            }

            GLFW.GLFW_KEY_PAGE_DOWN -> {
                input.adjustFromKeyboard(-10)
                true
            }

            GLFW.GLFW_KEY_PAGE_UP -> {
                input.adjustFromKeyboard(10)
                true
            }

            else -> false
        }

    /** Inserts a committed Unicode code point into the focused text input. */
    override fun charTyped(event: CharacterEvent): Boolean = dispatchEvent {
        refreshDisplay()
        refreshFocusValidity()
        val input = focusedElement as? TextInput ?: return false
        if (!event.isAllowedChatCharacter()) return false
        input.clearPreedit()
        if (!input.readOnly) input.insertUserText(event.codepointAsString())
        true
    }

    /** Updates the transient IME composition without changing the input's committed value. */
    override fun preeditUpdated(event: PreeditEvent?): Boolean = dispatchEvent {
        refreshDisplay()
        refreshFocusValidity()
        val input = focusedElement as? TextInput ?: return false
        if (event == null) {
            input.clearPreedit()
        } else {
            input.updatePreedit(event.fullText(), event.caretPosition())
        }
        true
    }

    fun nodeOf(element: UiElement): UiLayoutNode? {
        refreshDisplay()
        return nodesInPaintOrder().firstOrNull { it.element === element }
    }

    /** Returns the current scroll offset of [element], or null when it has no principal box. */
    fun scrollOffsetOf(element: UiElement): UiScrollOffset? {
        refreshDisplay()
        val node = nodesInPaintOrder().firstOrNull { node ->
            node.displayed && node.element === element
        } ?: return null
        return scrollOffsets[node.scrollTarget().key] ?: UiScrollOffset()
    }

    /**
     * Programmatically scrolls [element], clamped to its current scrollable overflow area.
     *
     * `hidden`, `auto`, and `scroll` axes can be changed. A `visible` or `clip` axis remains zero.
     * Returns true when the offset changed.
     */
    fun scrollTo(element: UiElement, x: Float, y: Float): Boolean {
        require(x.isFinite() && y.isFinite()) { "Scroll offsets must be finite: ($x, $y)" }
        refreshDisplay()
        val node = nodesInPaintOrder().firstOrNull { node ->
            node.displayed && node.element === element
        } ?: return false
        return setScrollOffset(node.scrollTarget(), x, y, directUserInput = false)
    }

    /** Programmatically scrolls [element] relative to its current offset. */
    fun scrollBy(element: UiElement, deltaX: Float, deltaY: Float): Boolean {
        require(deltaX.isFinite() && deltaY.isFinite()) {
            "Scroll deltas must be finite: ($deltaX, $deltaY)"
        }
        val current = scrollOffsetOf(element) ?: return false
        return scrollTo(element, current.x + deltaX, current.y + deltaY)
    }

    /** Returns every generated CSS box fragment associated with [element]. */
    fun fragmentsOf(element: UiElement): List<UiBoxFragment> {
        refreshDisplay()
        return buildList {
            fun visit(fragment: UiBoxFragment) {
                if (fragment.generatesBox && fragment.element === element) add(fragment)
                fragment.children.forEach(::visit)
            }
            visit(rootFragment)
        }
    }

    /**
     * Returns the generated pseudo-element layout box owned by [element].
     *
     * Returns null when no rule matches or the generated box has `display: none`.
     */
    fun pseudoNodeOf(
        element: UiElement,
        pseudoElement: UiPseudoElement,
    ): UiPseudoLayoutNode? {
        val node = nodeOf(element) ?: return null
        return when (pseudoElement) {
            UiPseudoElement.BEFORE -> node.beforePseudo
            UiPseudoElement.AFTER -> node.afterPseudo
        }
    }

    internal fun nodesInPaintOrder(): List<UiLayoutNode> = buildList {
        fun addTree(node: UiLayoutNode) {
            add(node)
            node.children.forEach(::addTree)
        }
        addTree(root)
    }

    internal fun colorPickerGeometry(input: ColorInput): ColorPickerGeometry? {
        val node = nodesInPaintOrder().firstOrNull { node ->
            node.displayed && node.element === input
        } ?: return null
        val (visualOffsetX, visualOffsetY) = visualOffsetOf(node) ?: (0f to 0f)
        val inputBounds = node.bounds.translated(visualOffsetX, visualOffsetY)
        val width = COLOR_PICKER_PADDING * 2f +
            COLOR_PICKER_SATURATION_VALUE_WIDTH +
            COLOR_PICKER_GAP +
            COLOR_PICKER_HUE_WIDTH
        val height = COLOR_PICKER_PADDING * 2f + COLOR_PICKER_HEIGHT
        val maximumLeft = max(viewport.left, viewport.right - width)
        val left = inputBounds.left.coerceIn(viewport.left, maximumLeft)
        val below = inputBounds.bottom + COLOR_PICKER_ANCHOR_GAP
        val top = if (below + height <= viewport.bottom) {
            below
        } else {
            max(viewport.top, inputBounds.top - COLOR_PICKER_ANCHOR_GAP - height)
        }
        val saturationValueBounds = UiRect(
            left = left + COLOR_PICKER_PADDING,
            top = top + COLOR_PICKER_PADDING,
            width = COLOR_PICKER_SATURATION_VALUE_WIDTH,
            height = COLOR_PICKER_HEIGHT,
        )
        return ColorPickerGeometry(
            bounds = UiRect(left, top, width, height),
            saturationValueBounds = saturationValueBounds,
            hueBounds = UiRect(
                left = saturationValueBounds.right + COLOR_PICKER_GAP,
                top = saturationValueBounds.top,
                width = COLOR_PICKER_HUE_WIDTH,
                height = COLOR_PICKER_HEIGHT,
            ),
        )
    }

    private fun openColorInput(): ColorInput? =
        (focusedElement as? ColorInput)?.takeIf(ColorInput::pickerOpen)

    private fun openColorPickerGeometry(): ColorPickerGeometry? =
        openColorInput()?.let(::colorPickerGeometry)

    private fun updateColorPickerFromPointer(
        input: ColorInput,
        geometry: ColorPickerGeometry,
        x: Float,
        y: Float,
    ) {
        when (colorPickerDragTarget) {
            ColorPickerDragTarget.SATURATION_VALUE -> {
                val saturation =
                    ((x - geometry.saturationValueBounds.left) /
                        geometry.saturationValueBounds.width).coerceIn(0f, 1f)
                val brightness =
                    (1f - (y - geometry.saturationValueBounds.top) /
                        geometry.saturationValueBounds.height).coerceIn(0f, 1f)
                input.updateSaturationValue(saturation, brightness)
            }

            ColorPickerDragTarget.HUE -> {
                val hue =
                    ((y - geometry.hueBounds.top) / geometry.hueBounds.height)
                        .coerceIn(0f, 0.999_999f) * 360f
                input.updateHue(hue)
            }

            null -> Unit
        }
    }

    private fun updateRangeInputFromPointer(
        input: RangeInput<*>,
        node: UiLayoutNode,
        pointerX: Float,
        pointerY: Float,
    ) {
        input.setFromUserFraction(
            rangeInputFractionAt(
                input = input,
                bounds = node.contentBounds,
                pointerX = pointerX,
                pointerY = pointerY,
            ),
        )
    }

    private fun colorInputKeyPressed(input: ColorInput, event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (input.pickerOpen) input.commitPicker() else input.openPicker()
                return true
            }

            GLFW.GLFW_KEY_ESCAPE -> {
                if (!input.pickerOpen) return false
                input.cancelPicker()
                colorPickerDragTarget = null
                return true
            }
        }
        if (!input.pickerOpen) return false

        val amount = if (event.hasShiftDown()) 0.1f else 0.01f
        val state = input.pickerState()
        return when (event.key()) {
            GLFW.GLFW_KEY_LEFT -> {
                input.updateSaturationValue(state.saturation - amount, state.value)
                true
            }

            GLFW.GLFW_KEY_RIGHT -> {
                input.updateSaturationValue(state.saturation + amount, state.value)
                true
            }

            GLFW.GLFW_KEY_UP -> {
                input.updateSaturationValue(state.saturation, state.value + amount)
                true
            }

            GLFW.GLFW_KEY_DOWN -> {
                input.updateSaturationValue(state.saturation, state.value - amount)
                true
            }

            GLFW.GLFW_KEY_PAGE_UP -> {
                input.adjustHue(if (event.hasShiftDown()) 15f else 1f)
                true
            }

            GLFW.GLFW_KEY_PAGE_DOWN -> {
                input.adjustHue(if (event.hasShiftDown()) -15f else -1f)
                true
            }

            else -> false
        }
    }

    private fun textInputIndexAt(
        node: UiLayoutNode,
        pointerX: Float,
        allowScroll: Boolean = false,
    ): Int {
        val input = node.element as TextInput
        val measurer = textMeasurer(input, node.font)
        val content = node.contentBounds
        val textWidth = measurer.width(input.value)
        val maximumScroll = (textWidth - content.width).coerceAtLeast(0f)
        var scroll = input.horizontalScroll.coerceIn(0f, maximumScroll)
        if (allowScroll) {
            scroll = when {
                pointerX < content.left -> scroll - (content.left - pointerX)
                pointerX > content.right -> scroll + (pointerX - content.right)
                else -> scroll
            }.coerceIn(0f, maximumScroll)
        }
        input.horizontalScroll = scroll
        val alignment = textInputAlignmentOffset(
            availableWidth = content.width,
            textWidth = textWidth,
            alignment = node.textAlign,
            scroll = scroll,
        )
        val textX = pointerX - content.left - alignment + scroll
        return textIndexAtHorizontalPosition(input.value, textX, measurer::width)
    }

    private data class UiHitRegion(
        val element: UiElement,
        val bounds: UiRect,
        val node: UiLayoutNode,
        val visualOffsetX: Float,
        val visualOffsetY: Float,
        val scrollChain: List<UiScrollTarget>,
        val clip: UiClipStack?,
    ) {
        fun contains(x: Float, y: Float): Boolean =
            bounds.contains(x, y) && (clip == null || clip.contains(x, y))
    }

    private sealed interface UiPaintContent {
        val order: Int
        val sourceIndex: Int

        data class Pseudo(
            val node: UiPseudoLayoutNode,
            override val order: Int,
            override val sourceIndex: Int,
        ) : UiPaintContent

        data object Text : UiPaintContent {
            override val order: Int = 0
            override val sourceIndex: Int = 0
        }

        data class Child(
            val node: UiLayoutNode,
            override val order: Int,
            override val sourceIndex: Int,
        ) : UiPaintContent
    }

    private fun paintContents(
        node: UiLayoutNode,
        style: ResolvedUiStyle = node.styleProvider(),
    ): List<UiPaintContent> {
        val contents = buildList {
            node.beforePseudo?.let { pseudo ->
                add(
                    UiPaintContent.Pseudo(
                        node = pseudo,
                        order = pseudo.pseudoStyleProvider().style.resolveDefaults().order,
                        sourceIndex = -1,
                    ),
                )
            }
            if (node.element is Paragraph || node.textFragments.isNotEmpty()) {
                add(UiPaintContent.Text)
            }
            node.children.forEachIndexed { index, child ->
                add(
                    UiPaintContent.Child(
                        node = child,
                        order = child.styleProvider().order,
                        sourceIndex = index,
                    ),
                )
            }
            node.afterPseudo?.let { pseudo ->
                add(
                    UiPaintContent.Pseudo(
                        node = pseudo,
                        order = pseudo.pseudoStyleProvider().style.resolveDefaults().order,
                        sourceIndex = Int.MAX_VALUE,
                    ),
                )
            }
        }
        return if (style.display.box?.inside == UiDisplayInside.FLEX) {
            contents.sortedWith(
                compareBy<UiPaintContent>(UiPaintContent::order)
                    .thenBy(UiPaintContent::sourceIndex),
            )
        } else {
            contents
        }
    }

    private fun hitRegionsInPaintOrder(): List<UiHitRegion> = buildList {
        fun addTree(
            node: UiLayoutNode,
            visualOffsetX: Float,
            visualOffsetY: Float,
            inheritedClip: UiClipStack?,
            ancestorScrollChain: List<UiScrollTarget>,
        ) {
            if (!node.displayed) return
            val rawBounds = node.bounds.translated(visualOffsetX, visualOffsetY)
            val visibleBounds = if (inheritedClip == null) rawBounds else inheritedClip.clip(rawBounds)
            val nodeScrollChain = ancestorScrollChain + node.scrollTarget()
            if (visibleBounds != null && visibleBounds.width > 0f && visibleBounds.height > 0f) {
                add(
                    UiHitRegion(
                        element = node.element,
                        bounds = visibleBounds,
                        node = node,
                        visualOffsetX = visualOffsetX,
                        visualOffsetY = visualOffsetY,
                        scrollChain = nodeScrollChain,
                        clip = inheritedClip,
                    ),
                )
            }

            val ownClip = node.overflowClip(visualOffsetX, visualOffsetY)
            val contentClip = when {
                ownClip == null -> inheritedClip
                inheritedClip == null -> ownClip
                else -> inheritedClip.intersect(ownClip) ?: return
            }
            val offset = scrollOffsets[node.scrollTarget().key] ?: UiScrollOffset()
            val contentOffsetX = visualOffsetX - offset.x
            val contentOffsetY = visualOffsetY - offset.y
            paintContents(node).forEach { content ->
                when (content) {
                    is UiPaintContent.Pseudo -> content.node
                        .takeIf(UiPseudoLayoutNode::displayed)
                        ?.let { pseudo ->
                            val pseudoRawBounds = pseudo.bounds.translated(
                                contentOffsetX,
                                contentOffsetY,
                            )
                            val pseudoVisibleBounds = if (contentClip == null) {
                                pseudoRawBounds
                            } else {
                                contentClip.clip(pseudoRawBounds)
                            }
                            if (
                                pseudoVisibleBounds != null &&
                                pseudoVisibleBounds.width > 0f &&
                                pseudoVisibleBounds.height > 0f
                            ) {
                                add(
                                    UiHitRegion(
                                        element = pseudo.element,
                                        bounds = pseudoVisibleBounds,
                                        node = node,
                                        visualOffsetX = contentOffsetX,
                                        visualOffsetY = contentOffsetY,
                                        scrollChain = nodeScrollChain + pseudo.scrollTarget(),
                                        clip = contentClip,
                                    ),
                                )
                            }
                        }

                    UiPaintContent.Text -> Unit
                    is UiPaintContent.Child -> addTree(
                        node = content.node,
                        visualOffsetX = contentOffsetX,
                        visualOffsetY = contentOffsetY,
                        inheritedClip = contentClip,
                        ancestorScrollChain = nodeScrollChain,
                    )
                }
            }
        }
        addTree(root, 0f, 0f, null, emptyList())
    }

    private fun visualOffsetOf(target: UiLayoutNode): Pair<Float, Float>? {
        fun find(node: UiLayoutNode, offsetX: Float, offsetY: Float): Pair<Float, Float>? {
            if (node === target) return offsetX to offsetY
            val scroll = scrollOffsets[node.scrollTarget().key] ?: UiScrollOffset()
            val contentOffsetX = offsetX - scroll.x
            val contentOffsetY = offsetY - scroll.y
            node.children.forEach { child ->
                find(child, contentOffsetX, contentOffsetY)?.let { return it }
            }
            return null
        }
        return find(root, 0f, 0f)
    }

    private fun refreshDisplay() {
        componentRuntime.flushUpdates()
        val evaluatedDisplays = mutableMapOf<UiDisplayKey, Boolean>()
        var changed = false
        displayStates.forEach { state ->
            val suppressed = state.predicate()
            evaluatedDisplays[state.key] = suppressed
            changed = changed || suppressed != state.suppressed
        }
        if (!changed) return

        val snapshot = snapshotCalculator(viewport, evaluatedDisplays)
        applySnapshot(snapshot)
    }

    private inline fun <T> dispatchEvent(block: () -> T): T {
        componentRuntime.beginEvent()
        return try {
            block()
        } finally {
            componentRuntime.endEvent()
        }
    }

    private fun applySnapshot(snapshot: UiLayoutSnapshot) {
        val previousNodes = nodesInPaintOrder()
        root = snapshot.root
        rootFragment = snapshot.rootFragment
        displayStates = snapshot.displayStates
        clampScrollOffsets()
        pointerGeometryDirty = true

        val displayedElements = java.util.Collections.newSetFromMap(
            IdentityHashMap<UiElement, Boolean>(),
        )
        nodesInPaintOrder()
            .filter(UiLayoutNode::displayed)
            .mapTo(displayedElements, UiLayoutNode::element)
        val hiddenElements = previousNodes
            .map(UiLayoutNode::element)
            .filterNot(displayedElements::contains)
        if (hiddenElements.any(UiElement::dragging)) {
            dragButtonInfo = null
        }
        focusedElement?.let { focused ->
            if (
                focused.tabIndex == null ||
                focused.disabled ||
                focused !in displayedElements
            ) {
                focusInternal(null)
            }
        }
        hiddenElements.forEach { element ->
            element.dragging = false
            element.hovering = false
        }
    }

    private fun setScrollOffset(
        target: UiScrollTarget,
        x: Float,
        y: Float,
        directUserInput: Boolean,
    ): Boolean {
        val previous = scrollOffsets[target.key] ?: UiScrollOffset()
        val mayScrollX = if (directUserInput) {
            target.overflow.x.acceptsUserScroll
        } else {
            target.overflow.x.isScrollable
        }
        val mayScrollY = if (directUserInput) {
            target.overflow.y.acceptsUserScroll
        } else {
            target.overflow.y.isScrollable
        }
        val next = UiScrollOffset(
            x = if (mayScrollX) x.coerceIn(0f, target.maximumX) else previous.x,
            y = if (mayScrollY) y.coerceIn(0f, target.maximumY) else previous.y,
        )
        if (next == previous) return false
        if (next == UiScrollOffset()) {
            scrollOffsets.remove(target.key)
        } else {
            scrollOffsets[target.key] = next
        }
        pointerGeometryDirty = true
        return true
    }

    private fun clampScrollOffsets() {
        fun clamp(target: UiScrollTarget) {
            val previous = scrollOffsets[target.key] ?: return
            val next = UiScrollOffset(
                x = if (target.overflow.x.isScrollable) {
                    previous.x.coerceIn(0f, target.maximumX)
                } else {
                    0f
                },
                y = if (target.overflow.y.isScrollable) {
                    previous.y.coerceIn(0f, target.maximumY)
                } else {
                    0f
                },
            )
            if (next == UiScrollOffset()) scrollOffsets.remove(target.key)
            else scrollOffsets[target.key] = next
        }

        fun visit(node: UiLayoutNode) {
            clamp(node.scrollTarget())
            node.beforePseudo?.let { pseudo -> clamp(pseudo.scrollTarget()) }
            node.afterPseudo?.let { pseudo -> clamp(pseudo.scrollTarget()) }
            node.children.forEach(::visit)
        }
        visit(root)
    }

    private fun drawOverflowContents(
        target: UiScrollTarget,
        paddingBounds: UiRect,
        clipRadii: Mine2DRoundedRectRadii,
        renderer: Mine2DEngine,
        visualOffsetX: Float,
        visualOffsetY: Float,
        draw: (contentOffsetX: Float, contentOffsetY: Float) -> Unit,
    ) {
        val clip = target.overflow.overflowClip(
            paddingBounds.translated(visualOffsetX, visualOffsetY),
            clipRadii,
        )
        withAxisClip(renderer, clip?.axisClip) {
            fun drawScrolledContents() {
                val scroll = scrollOffsets[target.key] ?: UiScrollOffset()
                val pose = renderer.graphics.pose()
                if (scroll == UiScrollOffset()) {
                    draw(visualOffsetX, visualOffsetY)
                    return
                }
                pose.pushMatrix()
                try {
                    pose.translate(-scroll.x, -scroll.y)
                    draw(visualOffsetX - scroll.x, visualOffsetY - scroll.y)
                } finally {
                    pose.popMatrix()
                }
            }

            if (clip?.roundedClips?.isNotEmpty() == true) {
                renderer.withRoundedClip(
                    x = paddingBounds.left,
                    y = paddingBounds.top,
                    width = paddingBounds.width,
                    height = paddingBounds.height,
                    radii = clipRadii,
                    draw = { drawScrolledContents() },
                )
            } else {
                drawScrolledContents()
            }
        }
    }

    private fun withAxisClip(
        renderer: Mine2DEngine,
        clip: UiAxisClip?,
        draw: () -> Unit,
    ) {
        if (clip == null) {
            draw()
            return
        }
        val graphics = renderer.graphics
        val left = floor((clip.left ?: 0f).toDouble()).toInt().coerceIn(0, graphics.guiWidth())
        val right = ceil((clip.right ?: graphics.guiWidth().toFloat()).toDouble())
            .toInt()
            .coerceIn(0, graphics.guiWidth())
        val top = floor((clip.top ?: 0f).toDouble()).toInt().coerceIn(0, graphics.guiHeight())
        val bottom = ceil((clip.bottom ?: graphics.guiHeight().toFloat()).toDouble())
            .toInt()
            .coerceIn(0, graphics.guiHeight())
        if (right <= left || bottom <= top) return

        graphics.enableScissor(left, top, right, bottom)
        try {
            draw()
        } finally {
            graphics.disableScissor()
        }
    }

    private fun draw(
        node: UiLayoutNode,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
        visualOffsetX: Float = 0f,
        visualOffsetY: Float = 0f,
    ) {
        if (!node.displayed) return

        val style = node.styleProvider()
        val dropShadow = style.dropShadow
        if (dropShadow != null) {
            renderer.withDropShadow(
                x = node.bounds.left,
                y = node.bounds.top,
                width = node.bounds.width,
                height = node.bounds.height,
                color = dropShadow.color,
                offsetX = dropShadow.offsetX,
                offsetY = dropShadow.offsetY,
                blurRadius = dropShadow.blurRadius,
            ) {
                drawContents(
                    node,
                    style,
                    renderer,
                    inheritedTextStyle,
                    timeSeconds,
                    visualOffsetX,
                    visualOffsetY,
                )
            }
        } else {
            drawContents(
                node,
                style,
                renderer,
                inheritedTextStyle,
                timeSeconds,
                visualOffsetX,
                visualOffsetY,
            )
        }
    }

    private fun drawContents(
        node: UiLayoutNode,
        style: ResolvedUiStyle,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
        visualOffsetX: Float,
        visualOffsetY: Float,
    ) {
        val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
        val roundedBox = style.borderRadius.resolve(node.bounds)
        style.boxShadow?.let { shadow ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.boxShadow(
                    x = node.bounds.left,
                    y = node.bounds.top,
                    width = node.bounds.width,
                    height = node.bounds.height,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                    spreadRadius = shadow.spreadRadius,
                    cornerRadii = if (shadow.followBorderRadius) {
                        roundedBox.radii
                    } else {
                        Mine2DRoundedRectRadii(shadow.cornerRadius)
                    },
                )
            }
        }
        style.drawBackground(renderer.material) { color, material ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.roundedRect(
                    node.bounds.left,
                    node.bounds.top,
                    node.bounds.width,
                    node.bounds.height,
                    roundedBox.radii,
                    color,
                    material,
                    renderer.uniformContext(
                        elementBounds = node.bounds.toUniformRect(),
                        contentBounds = node.contentBounds.toUniformRect(),
                        timeSeconds = timeSeconds,
                    ),
                )
            }
        }

        drawBorder(
            renderer = renderer,
            border = style.border,
            currentColor = resolvedTextStyle.color,
            borderBounds = node.bounds,
            paddingBounds = node.paddingBounds,
            outerRadii = roundedBox.radii,
            contentBounds = node.contentBounds,
            timeSeconds = timeSeconds,
        )

        drawOverflowContents(
            target = node.scrollTarget(),
            paddingBounds = node.paddingBounds,
            clipRadii = roundedBox.radii.inset(node.bounds, node.paddingBounds),
            renderer = renderer,
            visualOffsetX = visualOffsetX,
            visualOffsetY = visualOffsetY,
        ) { contentOffsetX, contentOffsetY ->
            when (val element = node.element) {
                is TextInput -> drawTextInput(
                    node,
                    element,
                    resolvedTextStyle,
                    requireFont(node),
                    renderer,
                    visualOffsetX,
                    visualOffsetY,
                )

                is ColorInput -> drawColorInput(node, element, renderer)
                is RangeInput<*> -> drawRangeInput(node, element, renderer)
                else -> Unit
            }

            paintContents(node, style).forEach { content ->
                when (content) {
                    is UiPaintContent.Pseudo ->
                        drawPseudo(
                            content.node,
                            renderer,
                            resolvedTextStyle,
                            timeSeconds,
                            contentOffsetX,
                            contentOffsetY,
                        )

                    UiPaintContent.Text -> {
                        if (node.textFragments.isNotEmpty()) {
                            drawStyledTextFragments(
                                node = node,
                                fallbackTextStyle = resolvedTextStyle,
                                renderer = renderer,
                            )
                        } else if (node.element is Paragraph && node.element.text.isNotEmpty()) {
                            drawText(
                                node.element.text,
                                style,
                                resolvedTextStyle,
                                node.textBounds ?: node.contentBounds,
                                requireFont(node),
                                renderer,
                            )
                        }
                    }

                    is UiPaintContent.Child -> draw(
                        content.node,
                        renderer,
                        resolvedTextStyle,
                        timeSeconds,
                        contentOffsetX,
                        contentOffsetY,
                    )
                }
            }
        }
    }

    private fun drawColorInput(
        node: UiLayoutNode,
        input: ColorInput,
        renderer: Mine2DEngine,
    ) {
        if (input.hovering) {
            renderer.graphics.requestCursor(
                if (input.disabled) CursorTypes.NOT_ALLOWED else CursorTypes.POINTING_HAND,
            )
        }
        val content = node.contentBounds
        if (content.width <= 0f || content.height <= 0f) return

        val borderColor = when {
            input.disabled -> 0xFF555555.toInt()
            input.focused -> 0xFFFFFFFF.toInt()
            else -> 0xFF909090.toInt()
        }
        renderer.quad(
            content.left,
            content.top,
            content.width,
            content.height,
            borderColor,
            Mine2DMaterials.COLOR,
        )
        val border = 2f.coerceAtMost(minOf(content.width, content.height) / 2f)
        val innerWidth = (content.width - border * 2f).coerceAtLeast(0f)
        val innerHeight = (content.height - border * 2f).coerceAtLeast(0f)
        if (innerWidth > 0f && innerHeight > 0f) {
            renderer.quad(
                content.left + border,
                content.top + border,
                innerWidth,
                innerHeight,
                input.value,
                Mine2DMaterials.COLOR,
            )
            if (input.disabled) {
                renderer.quad(
                    content.left + border,
                    content.top + border,
                    innerWidth,
                    innerHeight,
                    0x88000000.toInt(),
                    Mine2DMaterials.COLOR,
                )
            }
        }
    }

    private fun drawRangeInput(
        node: UiLayoutNode,
        input: RangeInput<*>,
        renderer: Mine2DEngine,
    ) {
        if (input.hovering) {
            renderer.graphics.requestCursor(
                if (input.disabled) CursorTypes.NOT_ALLOWED else CursorTypes.POINTING_HAND,
            )
        }
        val content = node.contentBounds
        if (content.width <= 0f || content.height <= 0f) return
        val geometry = rangeInputGeometry(input, content)

        geometry.trackBounds.drawRangePart(renderer, input.trackColor)
        geometry.activeTrackBounds.drawRangePart(renderer, input.activeTrackColor)

        if (geometry.thumbRadius > 0f) {
            val thumbRadius = if (input.focused && geometry.thumbRadius > 1f) {
                renderer.circle(
                    geometry.thumbCenterX,
                    geometry.thumbCenterY,
                    geometry.thumbRadius,
                    input.focusColor,
                    RangeInput.THUMB_SEGMENTS,
                    Mine2DMaterials.COLOR,
                )
                geometry.thumbRadius - 1f
            } else {
                geometry.thumbRadius
            }
            renderer.circle(
                geometry.thumbCenterX,
                geometry.thumbCenterY,
                thumbRadius,
                input.thumbColor,
                RangeInput.THUMB_SEGMENTS,
                Mine2DMaterials.COLOR,
            )
        }

        if (input.disabled) {
            renderer.quad(
                content.left,
                content.top,
                content.width,
                content.height,
                0x66000000,
                Mine2DMaterials.COLOR,
            )
        }
    }

    private fun drawOpenColorPicker(renderer: Mine2DEngine) {
        val input = openColorInput() ?: return
        val geometry = colorPickerGeometry(input) ?: return
        val state = input.pickerState()

        renderer.quad(
            geometry.bounds.left - 1f,
            geometry.bounds.top - 1f,
            geometry.bounds.width + 2f,
            geometry.bounds.height + 2f,
            0xFF000000.toInt(),
            Mine2DMaterials.COLOR,
        )
        renderer.quad(
            geometry.bounds.left,
            geometry.bounds.top,
            geometry.bounds.width,
            geometry.bounds.height,
            0xFF252525.toInt(),
            Mine2DMaterials.COLOR,
        )

        val hueColor = hsvToArgb(state.hue, 1f, 1f)
        drawGradientQuad(
            renderer,
            geometry.saturationValueBounds,
            topLeft = 0xFFFFFFFF.toInt(),
            topRight = hueColor,
            bottomRight = hueColor,
            bottomLeft = 0xFFFFFFFF.toInt(),
        )
        drawGradientQuad(
            renderer,
            geometry.saturationValueBounds,
            topLeft = 0x00000000,
            topRight = 0x00000000,
            bottomRight = 0xFF000000.toInt(),
            bottomLeft = 0xFF000000.toInt(),
        )

        repeat(6) { section ->
            val top = geometry.hueBounds.top + geometry.hueBounds.height * section / 6f
            val bottom = geometry.hueBounds.top + geometry.hueBounds.height * (section + 1) / 6f
            drawGradientQuad(
                renderer,
                UiRect(
                    geometry.hueBounds.left,
                    top,
                    geometry.hueBounds.width,
                    bottom - top,
                ),
                topLeft = hsvToArgb(section * 60f, 1f, 1f),
                topRight = hsvToArgb(section * 60f, 1f, 1f),
                bottomRight = hsvToArgb((section + 1) * 60f, 1f, 1f),
                bottomLeft = hsvToArgb((section + 1) * 60f, 1f, 1f),
            )
        }

        val saturationX = geometry.saturationValueBounds.left +
            state.saturation * geometry.saturationValueBounds.width
        val brightnessY = geometry.saturationValueBounds.top +
            (1f - state.value) * geometry.saturationValueBounds.height
        renderer.circle(
            saturationX,
            brightnessY,
            3.5f,
            0xFF000000.toInt(),
            16,
            Mine2DMaterials.COLOR,
        )
        renderer.circle(
            saturationX,
            brightnessY,
            2f,
            0xFFFFFFFF.toInt(),
            16,
            Mine2DMaterials.COLOR,
        )

        val hueY = geometry.hueBounds.top + state.hue / 360f * geometry.hueBounds.height
        renderer.quad(
            geometry.hueBounds.left - 2f,
            hueY - 1.5f,
            geometry.hueBounds.width + 4f,
            3f,
            0xFF000000.toInt(),
            Mine2DMaterials.COLOR,
        )
        renderer.quad(
            geometry.hueBounds.left - 1f,
            hueY - 0.5f,
            geometry.hueBounds.width + 2f,
            1f,
            0xFFFFFFFF.toInt(),
            Mine2DMaterials.COLOR,
        )

        val pointerX = lastPointerX?.toFloat()
        val pointerY = lastPointerY?.toFloat()
        if (
            pointerX != null &&
            pointerY != null &&
            (
                geometry.saturationValueBounds.contains(pointerX, pointerY) ||
                    geometry.hueBounds.contains(pointerX, pointerY)
                )
        ) {
            renderer.graphics.requestCursor(CursorTypes.CROSSHAIR)
        }
    }

    private fun drawGradientQuad(
        renderer: Mine2DEngine,
        bounds: UiRect,
        topLeft: Int,
        topRight: Int,
        bottomRight: Int,
        bottomLeft: Int,
    ) {
        renderer.polygon(
            listOf(
                Mine2DVertex(bounds.left, bounds.top, topLeft),
                Mine2DVertex(bounds.right, bounds.top, topRight),
                Mine2DVertex(bounds.right, bounds.bottom, bottomRight),
                Mine2DVertex(bounds.left, bounds.bottom, bottomLeft),
            ),
            Mine2DMaterials.COLOR,
        )
    }

    private fun drawTextInput(
        node: UiLayoutNode,
        input: TextInput,
        textStyle: ResolvedUiTextStyle,
        font: Mine2DFont,
        renderer: Mine2DEngine,
        visualOffsetX: Float,
        visualOffsetY: Float,
    ) {
        if (input.hovering) {
            renderer.graphics.requestCursor(
                if (input.disabled) CursorTypes.NOT_ALLOWED else CursorTypes.IBEAM,
            )
        }

        val content = node.contentBounds
        if (content.width <= 0f || content.height <= 0f) return

        val preedit = input.preedit.takeIf { input.focused }
        val preeditStart = input.selectionStart
        val displayText: String
        val caretPrefix: String
        if (preedit == null) {
            displayText = input.value
            caretPrefix = input.value.substring(0, input.caretPosition)
        } else {
            val before = input.value.substring(0, preeditStart)
            displayText = before + preedit.text + input.value.substring(input.selectionEnd)
            caretPrefix = before + preedit.text.substring(0, preedit.caretPosition)
        }

        val displayWidth = font.width(displayText)
        val caretAdvance = font.width(caretPrefix)
        val rightInset = 1f.coerceAtMost(content.width)
        val maximumScroll = maxOf(
            displayWidth - content.width,
            caretAdvance - (content.width - rightInset),
        ).coerceAtLeast(0f)
        var scroll = input.horizontalScroll.coerceIn(0f, maximumScroll)
        if (caretAdvance < scroll) {
            scroll = caretAdvance
        } else if (caretAdvance - scroll > content.width - rightInset) {
            scroll = caretAdvance - (content.width - rightInset)
        }
        scroll = scroll.coerceIn(0f, maximumScroll)
        input.horizontalScroll = scroll

        val alignmentOffset = textInputAlignmentOffset(
            availableWidth = content.width,
            textWidth = displayWidth,
            alignment = textStyle.textAlign,
            scroll = scroll,
        )
        val textX = content.left + alignmentOffset - scroll
        val lineTop = content.top + (content.height - font.lineHeight) / 2f
        val rendererY = textRendererY(
            lineBoxTop = lineTop,
            lineIndex = 0,
            lineHeight = font.lineHeight,
            rendererOffsetFromLineTop = font.rendererOffsetFromLineTop,
        )

        val scissorLeft = floor((content.left + visualOffsetX).toDouble()).toInt()
        val scissorTop = floor((content.top + visualOffsetY).toDouble()).toInt()
        val scissorRight = ceil((content.right + visualOffsetX).toDouble()).toInt()
        val scissorBottom = ceil((content.bottom + visualOffsetY).toDouble()).toInt()
        if (scissorRight <= scissorLeft || scissorBottom <= scissorTop) return

        renderer.graphics.enableScissor(scissorLeft, scissorTop, scissorRight, scissorBottom)
        try {
            if (preedit == null && input.focused && input.selectionStart != input.selectionEnd) {
                val selectionLeft = textX + font.width(input.value.substring(0, input.selectionStart))
                val selectionRight = textX + font.width(input.value.substring(0, input.selectionEnd))
                if (selectionRight > selectionLeft) {
                    renderer.quad(
                        selectionLeft,
                        lineTop,
                        selectionRight - selectionLeft,
                        font.lineHeight,
                        input.selectionColor,
                        Mine2DMaterials.COLOR,
                    )
                }
            }

            val paintedText = if (displayText.isEmpty()) input.placeholder else displayText
            if (paintedText.isNotEmpty()) {
                val paintedWidth = if (displayText.isEmpty()) font.width(paintedText) else displayWidth
                val paintedAlignment = if (displayText.isEmpty()) {
                    textInputAlignmentOffset(
                        availableWidth = content.width,
                        textWidth = paintedWidth,
                        alignment = textStyle.textAlign,
                        scroll = 0f,
                    )
                } else {
                    alignmentOffset
                }
                val paintedX = if (displayText.isEmpty()) {
                    content.left + paintedAlignment
                } else {
                    textX
                }
                val textOrigin = renderer.pixelAlignedTextOriginY(paintedX, rendererY)
                textStyle.textShadow?.let { shadow ->
                    renderer.textShadow(
                        font = font,
                        text = paintedText,
                        x = textOrigin.x,
                        y = textOrigin.y,
                        color = shadow.color,
                        offsetX = shadow.offsetX,
                        offsetY = shadow.offsetY,
                        blurRadius = shadow.blurRadius,
                    )
                }
                renderer.text(
                    font = font,
                    text = paintedText,
                    x = textOrigin.x,
                    y = textOrigin.y,
                    color = if (displayText.isEmpty()) input.placeholderColor else textStyle.color,
                )
            }

            if (preedit != null) {
                val compositionX = textX + font.width(input.value.substring(0, preeditStart))
                val compositionWidth = font.width(preedit.text)
                if (compositionWidth > 0f) {
                    renderer.quad(
                        compositionX,
                        lineTop + font.lineHeight - 1f,
                        compositionWidth,
                        1f,
                        input.caretColor ?: textStyle.color,
                        Mine2DMaterials.COLOR,
                    )
                }
            }

            if (input.focused && (preedit != null || input.isCaretVisible())) {
                val caretX = textX + caretAdvance
                renderer.quad(
                    caretX,
                    lineTop,
                    1f,
                    font.lineHeight,
                    input.caretColor ?: textStyle.color,
                    Mine2DMaterials.COLOR,
                )
            }
        } finally {
            renderer.graphics.disableScissor()
        }

        if (screenFocused && focusedElement === input) {
            val cssScroll = scrollOffsets[node.scrollTarget().key] ?: UiScrollOffset()
            val caretX = floor(
                (textX + caretAdvance + visualOffsetX - cssScroll.x).toDouble(),
            ).toInt()
            val caretTop = floor((lineTop + visualOffsetY - cssScroll.y).toDouble()).toInt()
            Minecraft.getInstance().textInputManager().setTextInputArea(
                caretX,
                caretTop,
                caretX + 1,
                ceil(
                    (lineTop + font.lineHeight + visualOffsetY - cssScroll.y).toDouble(),
                ).toInt(),
            )
        }
    }

    private fun drawPseudo(
        node: UiPseudoLayoutNode,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
        visualOffsetX: Float,
        visualOffsetY: Float,
    ) {
        if (!node.displayed) return

        val pseudoStyle = node.pseudoStyleProvider()
        val style = pseudoStyle.style.resolveDefaults()
        val dropShadow = style.dropShadow
        if (dropShadow != null) {
            renderer.withDropShadow(
                x = node.bounds.left,
                y = node.bounds.top,
                width = node.bounds.width,
                height = node.bounds.height,
                color = dropShadow.color,
                offsetX = dropShadow.offsetX,
                offsetY = dropShadow.offsetY,
                blurRadius = dropShadow.blurRadius,
            ) {
                drawPseudoContents(
                    node,
                    pseudoStyle.content,
                    style,
                    renderer,
                    inheritedTextStyle,
                    timeSeconds,
                    visualOffsetX,
                    visualOffsetY,
                )
            }
        } else {
            drawPseudoContents(
                node,
                pseudoStyle.content,
                style,
                renderer,
                inheritedTextStyle,
                timeSeconds,
                visualOffsetX,
                visualOffsetY,
            )
        }
    }

    private fun drawPseudoContents(
        node: UiPseudoLayoutNode,
        content: UiGeneratedContent,
        style: ResolvedUiStyle,
        renderer: Mine2DEngine,
        inheritedTextStyle: ResolvedUiTextStyle,
        timeSeconds: Float,
        visualOffsetX: Float,
        visualOffsetY: Float,
    ) {
        val resolvedTextStyle = style.resolveTextStyle(inheritedTextStyle)
        val roundedBox = style.borderRadius.resolve(node.bounds)
        style.boxShadow?.let { shadow ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.boxShadow(
                    x = node.bounds.left,
                    y = node.bounds.top,
                    width = node.bounds.width,
                    height = node.bounds.height,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                    spreadRadius = shadow.spreadRadius,
                    cornerRadii = if (shadow.followBorderRadius) {
                        roundedBox.radii
                    } else {
                        Mine2DRoundedRectRadii(shadow.cornerRadius)
                    },
                )
            }
        }
        style.drawBackground(renderer.material) { color, material ->
            if (node.bounds.width > 0f && node.bounds.height > 0f) {
                renderer.roundedRect(
                    node.bounds.left,
                    node.bounds.top,
                    node.bounds.width,
                    node.bounds.height,
                    roundedBox.radii,
                    color,
                    material,
                    renderer.uniformContext(
                        elementBounds = node.bounds.toUniformRect(),
                        contentBounds = node.contentBounds.toUniformRect(),
                        timeSeconds = timeSeconds,
                    ),
                )
            }
        }
        drawBorder(
            renderer = renderer,
            border = style.border,
            currentColor = resolvedTextStyle.color,
            borderBounds = node.bounds,
            paddingBounds = node.paddingBounds,
            outerRadii = roundedBox.radii,
            contentBounds = node.contentBounds,
            timeSeconds = timeSeconds,
        )
        drawOverflowContents(
            target = node.scrollTarget(),
            paddingBounds = node.paddingBounds,
            clipRadii = roundedBox.radii.inset(node.bounds, node.paddingBounds),
            renderer = renderer,
            visualOffsetX = visualOffsetX,
            visualOffsetY = visualOffsetY,
        ) { _, _ ->
            if (content is UiGeneratedContent.Text) {
                val font = requireNotNull(node.font) {
                    "${node.pseudoElement.cssName} on ${node.element.javaClass.simpleName} " +
                        "requires a font in its style or originating element"
                }
                if (node.textFragments.isNotEmpty()) {
                    drawTextFragments(node.textFragments, resolvedTextStyle, font, renderer)
                } else {
                    drawText(
                        content.value,
                        style,
                        resolvedTextStyle,
                        node.contentBounds,
                        font,
                        renderer,
                    )
                }
            }
        }
    }

    private fun drawTextFragments(
        fragments: List<UiTextLayoutFragment>,
        resolvedTextStyle: ResolvedUiTextStyle,
        font: Mine2DFont,
        renderer: Mine2DEngine,
    ) {
        fragments.forEach { fragment ->
            val y = textRendererY(
                lineBoxTop = fragment.bounds.top,
                lineIndex = 0,
                lineHeight = fragment.bounds.height,
                rendererOffsetFromLineTop = font.rendererOffsetFromLineTop,
            )
            val textOrigin = renderer.pixelAlignedTextOriginY(fragment.bounds.left, y)
            resolvedTextStyle.textShadow?.let { shadow ->
                renderer.textShadow(
                    font = font,
                    text = fragment.text,
                    x = textOrigin.x,
                    y = textOrigin.y,
                    color = shadow.color,
                    offsetX = shadow.offsetX,
                    offsetY = shadow.offsetY,
                    blurRadius = shadow.blurRadius,
                )
            }
            renderer.text(
                font,
                fragment.text,
                textOrigin.x,
                textOrigin.y,
                resolvedTextStyle.color,
            )
        }
    }

    private fun drawStyledTextFragments(
        node: UiLayoutNode,
        fallbackTextStyle: ResolvedUiTextStyle,
        renderer: Mine2DEngine,
    ) {
        node.styledTextFragments.forEach { styled ->
            val textStyle = styled.textStyle ?: fallbackTextStyle
            val font = requireNotNull(textStyle.font) {
                "${node.element.javaClass.simpleName} requires a font in its style or an " +
                    "ancestor style"
            }
            drawTextFragments(listOf(styled.fragment), textStyle, font, renderer)
        }
    }

    private fun drawText(
        text: String,
        style: ResolvedUiStyle,
        resolvedTextStyle: ResolvedUiTextStyle,
        contentBounds: UiRect,
        font: Mine2DFont,
        renderer: Mine2DEngine,
    ) {
        val textMeasurer = Mine2DTextMeasurer(font)
        val lines = textLines(text)
        val textTop = contentBounds.top
        lines.forEachIndexed { index, line ->
            val lineWidth = textMeasurer.width(line)
            val x = contentBounds.left + when (resolvedTextStyle.textAlign) {
                UiTextAlign.START, UiTextAlign.LEFT -> 0f
                UiTextAlign.END, UiTextAlign.RIGHT -> contentBounds.width - lineWidth
                UiTextAlign.CENTER -> (contentBounds.width - lineWidth) / 2f
            }
            val y = textRendererY(
                lineBoxTop = textTop,
                lineIndex = index,
                lineHeight = textMeasurer.lineHeight,
                rendererOffsetFromLineTop = font.rendererOffsetFromLineTop,
            )
            val textOrigin = renderer.pixelAlignedTextOriginY(x, y)
            resolvedTextStyle.textShadow
                ?.let { shadow ->
                    renderer.textShadow(
                        font = font,
                        text = line,
                        x = textOrigin.x,
                        y = textOrigin.y,
                        color = shadow.color,
                        offsetX = shadow.offsetX,
                        offsetY = shadow.offsetY,
                        blurRadius = shadow.blurRadius,
                    )
                }
            renderer.text(
                font,
                line,
                textOrigin.x,
                textOrigin.y,
                resolvedTextStyle.color,
            )
        }
    }

    private fun requireFont(node: UiLayoutNode): Mine2DFont =
        requireNotNull(node.font) {
            "${node.element.javaClass.simpleName} requires a font in its style or an ancestor style"
        }
}

internal fun textRendererY(
    lineBoxTop: Float,
    lineIndex: Int,
    lineHeight: Float,
    rendererOffsetFromLineTop: Float,
): Float = lineBoxTop + rendererOffsetFromLineTop + lineIndex * lineHeight

internal fun textIndexAtHorizontalPosition(
    text: String,
    x: Float,
    measure: (String) -> Float,
): Int {
    if (text.isEmpty() || x <= 0f) return 0
    var index = 0
    var previousWidth = 0f
    while (index < text.length) {
        val next = text.offsetByCodePoints(index, 1)
        val nextWidth = measure(text.substring(0, next))
        if (x < (previousWidth + nextWidth) / 2f) return index
        index = next
        previousWidth = nextWidth
    }
    return text.length
}

private fun UiRect.drawRangePart(renderer: Mine2DEngine, color: Int) {
    if (width <= 0f || height <= 0f) return
    renderer.roundedRect(
        left,
        top,
        width,
        height,
        min(width, height) / 2f,
        color,
        Mine2DMaterials.COLOR,
    )
}

internal fun textInputAlignmentOffset(
    availableWidth: Float,
    textWidth: Float,
    alignment: UiTextAlign,
    scroll: Float,
): Float {
    if (scroll > 0f || textWidth > availableWidth) return 0f
    return when (alignment) {
        UiTextAlign.START, UiTextAlign.LEFT -> 0f
        UiTextAlign.END, UiTextAlign.RIGHT -> availableWidth - textWidth
        UiTextAlign.CENTER -> (availableWidth - textWidth) / 2f
    }
}

internal fun UiStyle.drawBackground(
    rendererMaterial: Mine2DMaterial,
    draw: (color: Int, material: Mine2DMaterial) -> Unit,
) {
    backgroundColor?.let { color ->
        draw(color, backgroundMaterial ?: rendererMaterial)
    }
}

private fun drawBorder(
    renderer: Mine2DEngine,
    border: UiBorders,
    currentColor: Int,
    borderBounds: UiRect,
    paddingBounds: UiRect,
    outerRadii: Mine2DRoundedRectRadii,
    contentBounds: UiRect,
    timeSeconds: Float,
) {
    fun UiBorderSide.paintColor(): Int =
        if (style == UiBorderStyle.SOLID) color ?: currentColor else 0

    renderer.roundedBorder(
        x = borderBounds.left,
        y = borderBounds.top,
        width = borderBounds.width,
        height = borderBounds.height,
        radii = outerRadii,
        topWidth = (paddingBounds.top - borderBounds.top).coerceAtLeast(0f),
        rightWidth = (borderBounds.right - paddingBounds.right).coerceAtLeast(0f),
        bottomWidth = (borderBounds.bottom - paddingBounds.bottom).coerceAtLeast(0f),
        leftWidth = (paddingBounds.left - borderBounds.left).coerceAtLeast(0f),
        topColor = border.top.paintColor(),
        rightColor = border.right.paintColor(),
        bottomColor = border.bottom.paintColor(),
        leftColor = border.left.paintColor(),
        material = renderer.material,
        uniformContext = renderer.uniformContext(
            elementBounds = borderBounds.toUniformRect(),
            contentBounds = contentBounds.toUniformRect(),
            timeSeconds = timeSeconds,
        ),
    )
}

internal fun ResolvedUiStyle.drawBackground(
    rendererMaterial: Mine2DMaterial,
    draw: (color: Int, material: Mine2DMaterial) -> Unit,
) {
    backgroundColor?.let { color ->
        draw(color, backgroundMaterial ?: rendererMaterial)
    }
}

private fun UiLayoutNode.translated(deltaX: Float, deltaY: Float): UiLayoutNode = copy(
    outerBounds = outerBounds.translated(deltaX, deltaY),
    bounds = bounds.translated(deltaX, deltaY),
    paddingBounds = paddingBounds.translated(deltaX, deltaY),
    contentBounds = contentBounds.translated(deltaX, deltaY),
    scrollableOverflowBounds = scrollableOverflowBounds.translated(deltaX, deltaY),
    children = children.map { child -> child.translated(deltaX, deltaY) },
    beforePseudo = beforePseudo?.translated(deltaX, deltaY),
    afterPseudo = afterPseudo?.translated(deltaX, deltaY),
    textBounds = textBounds?.translated(deltaX, deltaY),
    textFragments = textFragments.map { fragment ->
        fragment.copy(bounds = fragment.bounds.translated(deltaX, deltaY))
    },
).also { translated ->
    translated.styleProvider = styleProvider
    translated.styledTextFragments = styledTextFragments.map { styled ->
        styled.copy(
            fragment = styled.fragment.copy(
                bounds = styled.fragment.bounds.translated(deltaX, deltaY),
            ),
        )
    }
}

private fun UiPseudoLayoutNode.translated(
    deltaX: Float,
    deltaY: Float,
): UiPseudoLayoutNode = copy(
    outerBounds = outerBounds.translated(deltaX, deltaY),
    bounds = bounds.translated(deltaX, deltaY),
    paddingBounds = paddingBounds.translated(deltaX, deltaY),
    contentBounds = contentBounds.translated(deltaX, deltaY),
    scrollableOverflowBounds = scrollableOverflowBounds.translated(deltaX, deltaY),
    textFragments = textFragments.map { fragment ->
        fragment.copy(bounds = fragment.bounds.translated(deltaX, deltaY))
    },
).also { translated ->
    translated.pseudoStyleProvider = pseudoStyleProvider
}

private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect = copy(
    left = left + deltaX,
    top = top + deltaY,
)

private fun UiBoxFragment.translated(deltaX: Float, deltaY: Float): UiBoxFragment = copy(
    marginBox = marginBox.translated(deltaX, deltaY),
    borderBox = borderBox.translated(deltaX, deltaY),
    paddingBox = paddingBox.translated(deltaX, deltaY),
    contentBox = contentBox.translated(deltaX, deltaY),
    scrollableOverflow = scrollableOverflow.translated(deltaX, deltaY),
    children = children.map { child -> child.translated(deltaX, deltaY) },
)

private fun UiLayoutNode.scrollTarget(): UiScrollTarget = UiScrollTarget(
    key = UiScrollKey(element),
    overflow = overflow,
    maximumX = maximumScrollX,
    maximumY = maximumScrollY,
)

private fun UiPseudoLayoutNode.scrollTarget(): UiScrollTarget = UiScrollTarget(
    key = UiScrollKey(element, pseudoElement),
    overflow = overflow,
    maximumX = maximumScrollX,
    maximumY = maximumScrollY,
)

private fun UiLayoutNode.overflowClip(
    visualOffsetX: Float,
    visualOffsetY: Float,
): UiClipStack? {
    val radii = styleProvider().borderRadius.resolve(bounds).radii
        .inset(bounds, paddingBounds)
    return overflow.overflowClip(
        paddingBounds.translated(visualOffsetX, visualOffsetY),
        radii,
    )
}

private fun Mine2DRoundedRectRadii.inset(
    borderBounds: UiRect,
    paddingBounds: UiRect,
): Mine2DRoundedRectRadii = inset(
    top = (paddingBounds.top - borderBounds.top).coerceAtLeast(0f),
    right = (borderBounds.right - paddingBounds.right).coerceAtLeast(0f),
    bottom = (borderBounds.bottom - paddingBounds.bottom).coerceAtLeast(0f),
    left = (paddingBounds.left - borderBounds.left).coerceAtLeast(0f),
    innerWidth = paddingBounds.width,
    innerHeight = paddingBounds.height,
)

private fun ResolvedUiOverflow.overflowClip(
    paddingBounds: UiRect,
    radii: Mine2DRoundedRectRadii,
): UiClipStack? {
    if (!x.clips && !y.clips) return null
    val roundedClip = if (x.clips && y.clips && !radii.isZero) {
        listOf(
            UiRoundedBox(
                paddingBounds,
                radii.normalized(paddingBounds.width, paddingBounds.height),
            ),
        )
    } else {
        emptyList()
    }
    return UiClipStack(
        axisClip = UiAxisClip(
            left = paddingBounds.left.takeIf { x.clips },
            right = paddingBounds.right.takeIf { x.clips },
            top = paddingBounds.top.takeIf { y.clips },
            bottom = paddingBounds.bottom.takeIf { y.clips },
        ),
        roundedClips = roundedClip,
    )
}

private fun UiRect.toUniformRect(): Mine2DUniformRect = Mine2DUniformRect(
    left = left,
    top = top,
    width = width,
    height = height,
)
