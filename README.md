# Mine2DEngine

[日本語](README.ja.md)

Mine2DEngine is a client-side 2D rendering library for Minecraft Fabric mods.

| Requirement | Version |
| --- | --- |
| Minecraft | `26.2` |
| Java | `25` |
| Fabric Loader | `0.19.3` or later |
| Fabric Language Kotlin | `1.13.13+kotlin.2.4.10` or later |
| Fabric API | `0.156.0+26.2` |

## Installation

The release is available from [Maven Central](https://central.sonatype.com/artifact/io.github.aiwao.mine2dengine/mine2dengine), so make sure your Gradle build uses `mavenCentral()` and add Mine2DEngine as a mod dependency:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    modImplementation("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
}
```

If Mine2DEngine will be installed as a separate mod, declare it in your mod's `fabric.mod.json`:

```json
{
  "depends": {
    "mine2dengine": ">=1.0.0"
  }
}
```

Alternatively, Fabric Loom can package the library inside your mod:

```kotlin
dependencies {
    modImplementation("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
    include("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
}
```

Your mod must also provide Fabric API and Fabric Language Kotlin.

## Quick start

Call `Mine2DEngine.initialize()` once from your client initializer. Then create an engine from the `GuiGraphicsExtractor` supplied by a HUD element or screen and issue drawing calls for that frame.

```kotlin
package com.example.examplemod

import io.github.aiwao.mine2dengine.Mine2DEngine
import io.github.aiwao.mine2dengine.Mine2DVertex
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier

object ExampleModClient : ClientModInitializer {
    private val DEMO_LAYER =
        Identifier.fromNamespaceAndPath("examplemod", "mine2d_demo")

    override fun onInitializeClient() {
        Mine2DEngine.initialize()

        HudElementRegistry.addLast(DEMO_LAYER) { graphics, _ ->
            val draw = Mine2DEngine(graphics)

            draw.quad(
                x = 12f,
                y = 12f,
                width = 100f,
                height = 28f,
                color = 0xCC20242A.toInt(),
            )
            draw.line(12f, 48f, 112f, 72f, width = 3f, color = 0xFFFFCC00.toInt())
            draw.circle(62f, 104f, radius = 24f, color = 0xCC44AAFF.toInt(), segments = 48)

            draw.polygon(
                Mine2DVertex(130f, 12f, 0xFFFF5555.toInt()),
                Mine2DVertex(190f, 12f, 0xFF55FF55.toInt()),
                Mine2DVertex(205f, 55f, 0xFF5555FF.toInt()),
                Mine2DVertex(155f, 75f, 0xFFFFFFFF.toInt()),
                Mine2DVertex(120f, 45f, 0xFFFF55FF.toInt()),
            )
        }
    }
}
```

Coordinates are GUI coordinates, and colors are Minecraft ARGB integers (`0xAARRGGBB`). Drawing methods enqueue immutable render state for the current frame; they do not render immediately.

## Drawing API

| Method | Description |
| --- | --- |
| `polygon(...)` | Draws a simple convex or concave polygon. Use `Mine2DVertex` for per-vertex colors, or pass one color and JOML `Vector2fc` points. |
| `quad(x, y, width, height, color)` | Draws a filled rectangle. |
| `roundedRect(x, y, width, height, ..., color)` | Draws a filled rounded rectangle with one circular radius or independently elliptical corners. |
| `line(startX, startY, endX, endY, width, color)` | Draws a filled line with butt caps. |
| `circle(centerX, centerY, radius, color, segments)` | Draws a filled regular-polygon approximation of a circle. More segments produce a smoother edge. |
| `boxShadow(x, y, width, height, ...)` | Draws a soft rounded-box shadow without drawing the box itself. |
| `textShadow(font, text, x, y, ...)` | Draws a configurable glyph shadow without drawing the foreground text. |
| `text(font, text, x, y, color)` | Draws text using a loaded `Mine2DFont`. |
| `withMaterial(material) { ... }` | Temporarily changes the default polygon material and restores it after the block. |
| `withRoundedClip(x, y, width, height, ...) { ... }` | Clips every deferred GUI draw in the block, including text, to a transformed rounded rectangle. |

Polygon points may use clockwise or counterclockwise order. A polygon must have at least three distinct points, a non-zero area, and no self-intersections. Consecutive duplicate points and redundant collinear points are removed automatically. Lines require different endpoints and a positive width; circles require a positive radius and at least three segments. Rounded rectangles are tessellated automatically according to their curvature, and overlapping radii are reduced with the common scale factor defined by CSS. Use `Mine2DRoundedRectRadii` and `Mine2DCornerRadius` to specify corners independently.

The engine captures `graphics.pose()` and the active scissor rectangle for every call. You can therefore use Minecraft's GUI transforms and clipping normally:

```kotlin
graphics.enableScissor(10, 10, 170, 110)
try {
    Mine2DEngine(graphics).quad(0f, 0f, 240f, 140f, 0xAA336699.toInt())
} finally {
    graphics.disableScissor()
}
```

## TrueType fonts

Place a `.ttf` file under your client resources. For example:

```text
src/main/resources/
└── assets/
    └── examplemod/
        └── font/
            └── ui.ttf
```

Fonts create GPU and native resources. Load them once after client resources are available, use them only while open, and close them on the render thread. The Fabric client lifecycle events are a convenient way to manage this:

```kotlin
import io.github.aiwao.mine2dengine.Mine2DFont
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.resources.Identifier

private var uiFont: Mine2DFont? = null

// Register these callbacks from onInitializeClient().
ClientLifecycleEvents.CLIENT_STARTED.register {
    uiFont = Mine2DFont.load(
        location = Identifier.fromNamespaceAndPath("examplemod", "ui.ttf"),
        size = 14f,
        oversample = 2f,
    )
}

ClientLifecycleEvents.CLIENT_STOPPING.register {
    uiFont?.close()
    uiFont = null
}
```

Draw and measure text with the loaded font:

```kotlin
uiFont?.let { font ->
    draw.text(font, "Mine2DEngine", 16f, 16f, 0xFFFFFFFF.toInt())

    draw.textShadow(
        font,
        "Custom shadow",
        16f,
        36f,
        color = 0xA0000000.toInt(),
        offsetY = 2f,
        blurRadius = 2f,
    )
    draw.text(font, "Custom shadow", 16f, 36f, 0xFFFFFFFF.toInt())

    val width = font.width("Mine2DEngine")
    val lineHeight = font.lineHeight
}
```

Text coordinates, horizontal advances, and line heights use floating-point GUI units.
Layout line boxes use the TrueType font's ascender, descender, and baseline interval, including
when text is vertically centered.
Paragraph layout remains floating-point; its final vertical glyph origin is aligned to the
framebuffer pixel grid so linear filtering does not blur a subpixel baseline.

`Mine2DFont.load`, `width`, and `close` must run on the render thread. Do not load a font once per frame. If you recreate a font during a resource reload, close the previous instance first.

Mine2DEngine applies linear filtering only to glyph atlases created by `Mine2DFont`. Other Minecraft fonts keep their original sampling behavior, while oversampled TrueType glyphs are reduced smoothly.

## Layout engine

The layout package builds an element tree from containers, paragraphs, and typed input controls, cascades styles, generates a CSS box tree, and lays it out into box fragments. The implementation intentionally uses CSS property names and CSS initial values instead of the previous stack-layout behavior.

### Supported CSS layout profile

- `display: block | inline | flow-root | flex | inline-flex | none | contents`
- Block flow, inline text line boxes, normal/pre/nowrap white-space processing, and `text-align`
- Content-box and border-box sizing, `px`/percentage/`vw`/`vh` lengths, intrinsic size keywords, and min/max constraints
- Physical margins and padding, horizontal `auto` margins, and adjacent block margin collapsing
- `position: static | relative | absolute`, length/percentage insets, and automatic stretching between paired insets
- Flexbox rows and columns, reverse directions, wrapping, grow/shrink/basis, order, gaps, auto margins, and the justify/align properties
- Single-line `TextInput`, numeric slider `RangeInput`, and opaque RGB `ColorInput` controls as replaced elements
- Generated `::before` and `::after` boxes
- `display: none` subtree suppression and `display: contents` principal-box suppression

Grid, tables, floats, ruby layout, vertical writing modes, fragmentation, fixed/sticky positioning, borders, and replaced elements other than the built-in input controls are outside this profile.

### Initial containing block

CSS layout needs an available width and height. Therefore, every layout call receives a viewport rectangle:

```kotlin
import io.github.aiwao.mine2dengine.layout.LayoutEngine
import io.github.aiwao.mine2dengine.layout.UiRect

val layout = LayoutEngine.layout(
    root = root,
    viewport = UiRect(
        left = 0f,
        top = 0f,
        width = screenWidth,
        height = screenHeight,
    ),
)
```

`layout.viewport` exposes the current initial containing block. Update it without replacing the
`UiLayout` instance when the available GUI area changes:

```kotlin
layout.updateViewport(
    UiRect(
        left = panelLeft,
        top = panelTop,
        width = newWidth,
        height = newHeight,
    ),
)
```

`updateViewport` is synchronous. Supplying the current rectangle is a no-op; changing only its
origin translates all geometry, while changing its width or height performs a complete CSS
relayout. The old viewport and geometry remain installed if calculation fails. Previously obtained
`UiLayoutNode` and `UiBoxFragment` objects are snapshots, so query them again through `root`,
`nodeOf`, `rootFragment`, or `fragmentsOf` after an update.

A block box whose `width` is `auto` fills the available inline size. An `auto` height fits its in-flow contents. This means an unstyled root `div` normally has the viewport width rather than shrinking around its children.

`div` and `p` receive `display: block` from the built-in user-agent style layer. Other tags use the CSS initial display value, `inline`. Author style sheets override the user-agent layer, and an element's directly supplied style has the highest priority.

### CSS values and the box model

A null property in `UiStyle` means “not declared.” CSS keywords are explicit values, so a later rule can reset a property to `auto`, `none`, or `content`.

```kotlin
import io.github.aiwao.mine2dengine.layout.UiBoxSizing
import io.github.aiwao.mine2dengine.layout.UiBorderRadii
import io.github.aiwao.mine2dengine.layout.UiBorders
import io.github.aiwao.mine2dengine.layout.UiCornerRadius
import io.github.aiwao.mine2dengine.layout.UiMarginValue
import io.github.aiwao.mine2dengine.layout.UiMargins
import io.github.aiwao.mine2dengine.layout.UiPaddings
import io.github.aiwao.mine2dengine.layout.UiSizeValue
import io.github.aiwao.mine2dengine.layout.UiStyle
import io.github.aiwao.mine2dengine.layout.percent
import io.github.aiwao.mine2dengine.layout.px
import io.github.aiwao.mine2dengine.layout.vh
import io.github.aiwao.mine2dengine.layout.vw

val style = UiStyle(
    width = 50f.percent,
    height = 25f.vh,
    minWidth = 80f.px,
    maxWidth = UiSizeValue.MAX_CONTENT,
    margin = UiMargins(
        right = UiMarginValue.AUTO,
        left = UiMarginValue.AUTO,
    ),
    padding = UiPaddings(vertical = 6f, horizontal = 10f),
    border = UiBorders(1f.px, 0xFF808080.toInt()),
    borderRadius = UiBorderRadii(
        topLeft = UiCornerRadius(16f.px),
        bottomRight = UiCornerRadius(50f.percent, 25f.percent),
    ),
    boxSizing = UiBoxSizing.BORDER_BOX,
)
```

`Float.px`, `Float.percent`, `Float.vw`, and `Float.vh` create length-percentage values. `1vw` is one percent of the current layout viewport's width and `1vh` is one percent of its height, independent of the containing block and the property axis. Negative lengths are accepted by margins and insets; sizes, padding, and gaps reject them. Padding percentages and physical margin percentages use the containing block's width, as in CSS. Changing the viewport width or height through `updateViewport` recomputes viewport-relative lengths.

`border` accepts `UiBorders` with physical top/right/bottom/left sides. Each `UiBorderSide`
supports `NONE` and `SOLID`; widths are non-negative `px`, `vw`, or `vh` lengths (not percentages), and a null color means the
element's computed `color` (`currentColor`). `NONE` has zero used width. A `UiBorders` value is one
atomic declaration during cascade, and `UiBorders.NONE` explicitly resets it. Borders participate
in intrinsic, flex, positioned, and `box-sizing` layout.

`borderRadius` does not affect layout dimensions. Horizontal corner percentages use the border-box width and vertical percentages use its height. When both overflow axes clip, the outer radius is inset by the adjacent border widths to shape the padding-box clip. A box shadow follows the resolved outer border radius by default. To use the legacy equal radius, specify `UiBoxShadow(cornerRadius = ..., followBorderRadius = false)`; a positive `cornerRadius` makes `followBorderRadius` default to false.

Supported preferred/minimum/maximum size values are `AUTO`, `MIN_CONTENT`, `MAX_CONTENT`, `FitContent(...)`, and a length-percentage. Maximum sizes additionally accept `NONE`. The minimum wins when it exceeds the maximum. `box-sizing` applies to length-percentage values and the quantitative limit of `FitContent(...)`, but not to `MIN_CONTENT` or `MAX_CONTENT` themselves.

### Overflow

`overflow`, `overflowX`, and `overflowY` accept `VISIBLE`, `HIDDEN`, `CLIP`, `SCROLL`, and `AUTO`:

```kotlin
import io.github.aiwao.mine2dengine.layout.UiOverflow
import io.github.aiwao.mine2dengine.layout.UiOverflowValue

val scrollerStyle = UiStyle(
    width = 160f.px,
    height = 80f.px,
    overflow = UiOverflow(UiOverflowValue.AUTO),
)

// A two-value shorthand maps to the physical x and y axes.
val verticalScrollerStyle = UiStyle(
    overflow = UiOverflow(
        x = UiOverflowValue.CLIP,
        y = UiOverflowValue.AUTO,
    ),
)
```

An `overflowX` or `overflowY` value overrides the corresponding shorthand axis in the same
`UiStyle`. A later shorthand declaration resets earlier longhands during cascade. Cross-axis
computed-value interaction follows CSS: `visible` becomes `auto` when the other axis is
`hidden`, `scroll`, or `auto`; `clip` remains non-scrollable.

`hidden` clips wheel input but remains scrollable through `UiLayout.scrollTo` and `scrollBy`.
`clip` forbids both user and programmatic scrolling. `auto` and `scroll` accept wheel input, with
nested scroll containers chaining at their limits. Scroll offsets survive relayout and are clamped
to the new overflow geometry. Use `scrollOffsetOf` to query an offset; `UiLayoutNode` exposes
`paddingBounds`, `scrollableOverflowBounds`, `maximumScrollX`, and `maximumScrollY`.

Overflow clips at the padding box and does not currently paint scrollbars. When both axes clip,
`borderRadius` shapes the clip and pointer hit testing; a single clipped axis remains rectangular.

### Flexbox

Set the inner display type to flex with `UiDisplay.FLEX` or `UiDisplay.INLINE_FLEX`:

```kotlin
import io.github.aiwao.mine2dengine.layout.UiAlignItems
import io.github.aiwao.mine2dengine.layout.UiDisplay
import io.github.aiwao.mine2dengine.layout.UiFlexDirection
import io.github.aiwao.mine2dengine.layout.UiJustifyContent
import io.github.aiwao.mine2dengine.layout.UiStyle
import io.github.aiwao.mine2dengine.layout.div
import io.github.aiwao.mine2dengine.layout.px

val toolbar = div(
    UiStyle(
        display = UiDisplay.FLEX,
        width = 240f.px,
        flexDirection = UiFlexDirection.ROW,
        justifyContent = UiJustifyContent.SPACE_BETWEEN,
        alignItems = UiAlignItems.CENTER,
        columnGap = 6f.px,
    ),
) {
    div(UiStyle(width = 24f.px, height = 24f.px))
    div(
        UiStyle(
            flexGrow = 1f,
            flexShrink = 1f,
            flexBasis = 0f.px,
            minWidth = 0f.px,
        ),
    )
}
```

The supported container properties are `flexDirection`, `flexWrap`, `justifyContent`, `alignItems`, `alignContent`, `rowGap`, and `columnGap`. Item properties are `flexGrow`, `flexShrink`, `flexBasis`, `order`, and `alignSelf`.

Flexible lengths are resolved from each item's flex base size with scaled shrink factors and repeated min/max clamping. The CSS automatic minimum size is content-based for non-scrollable overflow and zero for a scrollable main axis. Specify `minWidth = 0f.px` (or `minHeight` for a column) when a non-scrollable item must be allowed to shrink below its content.

Absolutely positioned children do not become flex items. Generated pseudo-elements do become flex items. Text directly inside a flex container is wrapped in an anonymous flex item.

### Positioning

Insets use `UiInsetValue.AUTO` or a length-percentage:

```kotlin
val badgeStyle = UiStyle(
    position = UiPosition.ABSOLUTE,
    top = 6f.px,
    right = 8f.px,
    width = 20f.px,
)
```

A relative box keeps its normal-flow space and then receives its visual offset. An absolute box is removed from flow and uses the padding box of its nearest positioned ancestor; otherwise it uses the initial containing block. If both insets in an axis are definite while the corresponding size is `auto`, the box stretches between those insets.

### Cascade, components, and generated content

Existing tag, ID, class, scope, compound, selector-list, and combinator targets continue to participate in CSS specificity and source order. Scoped container sheets and component sheets still establish their existing boundaries.

A pseudo-element rule uses `UiPseudoStyle` and either `UiGeneratedContent.Text` or `UiGeneratedContent.EmptyBox`. Its display, sizing, positioning, and flex-item properties are resolved like those of a regular generated box.

`color`, `font`, `textShadow`, `textAlign`, and `whiteSpace` inherit. Paint properties such as backgrounds and box/drop shadows do not.

### Text input

`input()` creates a single-line `TextInput` corresponding to HTML `input type="text"`. It independently manages its value, caret, selection, horizontal scrolling, clipboard shortcuts, and IME preedit; it does not use Minecraft's `EditBox`.

```kotlin
lateinit var playerName: TextInput

val root = div(UiStyle(font = uiFont)) {
    playerName = input(
        value = "",
        placeholder = "Player name",
        maxLength = 32,
        style = { input ->
            UiStyle(
                width = 180f.px,
                height = 24f.px,
                padding = UiPaddings(vertical = 5f, horizontal = 6f),
                backgroundColor = if (input.focused) {
                    0xFF303840.toInt()
                } else {
                    0xFF202428.toInt()
                },
            )
        },
        onInput = { value -> println("editing: $value") },
        onChange = { value -> println("committed: $value") },
    )
}
```

`onInput` runs whenever a user action changes the committed value. `onChange` runs when an edited control loses focus. Programmatic assignment to `value` invokes neither callback. A `readOnly` input can still be focused, selected, and copied but cannot be edited; a `disabled` input cannot be focused.

With CSS `width: auto`, the intrinsic width is the advance of `size` zero glyphs. An `auto` height uses the font's line height. Changing the value, placeholder, caret, or selection does not affect geometry and requires no `relayout()`. Call `relayout()` after changing `size` or a layout property.

A layout containing input controls can be registered directly as a renderable widget in `Screen.init()`. Registration lets Minecraft automatically dispatch rendering, mouse, keyboard, character, Tab-focus, and IME-preedit events:

```kotlin
private lateinit var layout: UiLayout

override fun init() {
    layout = LayoutEngine.layout(
        root = root,
        viewport = UiRect(0f, 0f, width.toFloat(), height.toFloat()),
    )
    addRenderableWidget(layout)
}

override fun repositionElements() {
    layout.updateViewport(UiRect(0f, 0f, width.toFloat(), height.toFloat()))
}
```

Do not also call `layout.render(...)` for a layout registered as a widget. Manual event forwarding remains available, but Screen registration is recommended to enable IME and receive preedit events correctly. Use `layout.focus(playerName)`, `layout.clearFocus()`, and `layout.focusedElement` to control or inspect focus.

Every `UiElement` can receive keyboard focus by declaring `tabIndex`. Null disables focus, `-1` allows only pointer or `layout.focus()` focus, and `0` joins the natural Tab order. Positive values are ordered numerically before `0`, with layout order breaking ties. Input controls default to `0`; other elements default to null. Disabled elements and elements that generate no box cannot be focused.

```kotlin
div(
    tabIndex = 0,
    onFocus = { println("focused") },
    onBlur = { println("blurred") },
    onKeyPressed = { event ->
        if (event.key() == GLFW.GLFW_KEY_ENTER) activate()
    },
)
```

`onKeyPressed` observes a `KeyEvent` delivered while the element is focused. The callback runs after the element's standard key handling and affects neither that handling nor the event's consumed result. For example, a `div` has no standard key handling, so setting its callback still leaves `UiLayout.keyPressed()` returning `false`. Committed TextInput character changes continue to arrive through `charTyped` and should be observed with `onInput`.

### Range input

`rangeInput<T>()` creates a typed numeric slider corresponding to HTML `input type="range"`. `T` may be `Int`, `Float`, or `Double`; `value`, `min`, `max`, `step`, and callback values all retain that type. Values are always clamped between `min` and `max`. When `step` is present, the value is aligned to the nearest step relative to `min`; a tie selects the greater value.

```kotlin
lateinit var volume: RangeInput<Float>

val rangeStyles = object : StyleSheet {
    override val styles = mutableListOf<StyleSheetObject>()
}.apply {
    newStyle(
        TargetClass("volume").rangeTrack,
        UiStyle(backgroundColor = 0xFF404850.toInt()),
    )
    newStyle(
        TargetClass("volume").rangeProgress,
        UiStyle(backgroundColor = 0xFF4F8CFF.toInt()),
    )
    newStyle(TargetClass("volume").rangeThumb) { owner ->
        owner as RangeInput<*>
        UiStyle(
            width = 12f.px,
            height = 12f.px,
            backgroundColor = if (owner.focused) {
                0xFFFFFFFF.toInt()
            } else {
                0xFFE0E0E0.toInt()
            },
            border = if (owner.focused) {
                UiBorders(1f, 0xFF80B0FF.toInt())
            } else {
                UiBorders.NONE
            },
            borderRadius = UiBorderRadii(50f.percent),
        )
    }
}

val root = div(styleSheets = listOf(rangeStyles)) {
    volume = rangeInput<Float>(
        value = 0.5f,
        min = 0f,
        max = 1f,
        step = 0.05f,
        label = "Volume",
        valueText = { value -> "${(value * 100).toInt()} percent" },
        className = setOf("volume"),
        style = { input ->
            UiStyle(
                width = 140f.px,
                height = 24f.px,
                backgroundColor = if (input.focused) {
                    0xFF303840.toInt()
                } else {
                    0xFF202428.toInt()
                },
            )
        },
        onInput = { value -> previewVolume(value) },
        onChange = { value -> saveVolume(value) },
    )
}
```

The type argument can be explicit, as in `rangeInput<Int>()`, or inferred from numeric arguments, as in `rangeInput(value = 3, min = 0, max = 10)`. Calling `rangeInput()` with neither a type nor numeric arguments returns `RangeInput<Double>` for backward compatibility. Code that needs an explicit type token can use `rangeInput(RangeNumberTypes.INT, ...)`.

An omitted or null `value` initializes to the step-aligned midpoint of `min` and `max`. The defaults for every type are numerically `min = 0`, `max = 100`, and `step = 1`. A null `step` disables step alignment; keyboard input then changes by one hundredth of the range. For `Int`, pointer and keyboard results still round to the nearest integer, and the minimum keyboard increment is one. Programmatic assignment to `min`, `max`, `step`, or `value` re-sanitizes the value without invoking callbacks. Non-finite `Float` / `Double` values, `min > max`, and non-positive steps are rejected.

Clicking or dragging the track dispatches `onInput` whenever the aligned value actually changes, then dispatches `onChange` once on mouse release or focus loss. Each keyboard operation is committed independently and dispatches both callbacks when it changes the value. Arrow keys change one step, Page Up/Down change ten steps, and Home/End select the minimum/maximum allowed value. Wheel input is left to the normal scroll-container chain.

With `orientation = RangeOrientation.VERTICAL`, the minimum is at the bottom and maximum at the top. The default content size is `100 × 20` horizontally and `20 × 100` vertically. Changing orientation requires `relayout()`; changing the value or constraints does not.

The track, filled progress, and thumb are user-agent-managed control parts selected with `rangeTrack`, `rangeProgress`, and `rangeThumb`. They can only be styled through a style sheet; `rangeInput()` deliberately has no part-style arguments. Each part receives an independent cascade over its UA defaults and accepts an ordinary `UiStyle`. Box sizing, dimensions and constraints, margin, padding, border, background, radius, and shadows affect its generated empty box. Layout properties without a meaningful internal context, such as flex item ordering, have no effect. A dynamic part rule receives the originating `RangeInput`, allowing focused, hovering, dragging, and disabled appearances without a separate part state object. Pointer value conversion uses the same styled track and thumb geometry as painting. `RangeInput` remains the only focus, event, and accessibility target and does not activate platform text input or IME.

### Color input

`colorInput()` creates a `ColorInput` corresponding to HTML `input type="color"`. Its value uses Mine2DEngine's `0xAARRGGBB` integer format and is always normalized to opaque `0xFFRRGGBB`; assigning a transparent color therefore discards its alpha channel.

```kotlin
lateinit var accent: ColorInput

val root = div {
    accent = colorInput(
        value = 0xFF4CAF50.toInt(),
        label = "Accent color",
        style = { input ->
            UiStyle(
                width = 40f.px,
                height = 24f.px,
                padding = UiPaddings(2f),
                backgroundColor = if (input.focused) {
                    0xFF303840.toInt()
                } else {
                    0xFF202428.toInt()
                },
            )
        },
        onInput = { color -> previewAccent(color) },
        onChange = { color -> saveAccent(color) },
    )
}
```

Clicking the swatch opens an HSV picker overlay. Dragging its saturation/value field or hue strip dispatches `onInput`; dismissing or confirming an edited picker dispatches `onChange` once. Programmatic `value` assignment dispatches neither callback. Enter or Space opens/confirms the picker, Escape restores the color from when the picker opened, arrow keys adjust saturation/value, and Page Up/Down adjusts hue. Holding Shift uses larger keyboard steps.

The default `width: auto` and `height: auto` content size is `36 × 20` GUI pixels; these intrinsic metrics do not depend on a font. An inline formatting context can still require a parent font for its line box. `ColorInput` participates in the same focus and Tab order as the other input controls, but does not activate the platform text-input/IME path. Every input control uses the `input` tag by default; use a class or ID when control types need distinct style-sheet rules.

### Results, rendering, and input

`UiLayout.root` and `nodeOf(element)` provide the element-oriented compatibility view. `UiLayout.rootFragment` exposes the generated CSS fragment tree, and `fragmentsOf(element)` returns only boxes actually generated for that element. It returns no fragments for `display: none`; `display: contents` has no principal fragment, although a generated pseudo-element can still have one.

```kotlin
val node = layout.nodeOf(button)
val fragments = layout.fragmentsOf(button)

layout.render(draw)
val hit = layout.elementAt(mouseX, mouseY)
```

Rendering, shadows, hit testing, hover, click, and drag dispatch use the final CSS geometry. Changing only `left` or `top` on a calculated layout translates nodes, fragments, and hit regions together.

A viewport update does not dispatch pointer callbacks. Hover and drag state is retained for elements
that still generate boxes and cleared for elements removed by a complete relayout; the next
`mouseMove` reconciles hover against the new geometry.

A dynamic style provider that switches to or from `UiDisplay.NONE` is checked before rendering and
pointer operations and triggers geometry recalculation. After changing any other layout property,
text, style-sheet contents, or children, call `layout.relayout()` to rebuild geometry with the
current viewport.

## Custom shaders

A shader defines a pipeline and its bindings. A material is an immutable set of uniform values and textures for that shader. Register custom pipelines during client initialization. Vertex and fragment shader identifiers are relative to `assets/<namespace>/shaders/` and omit the `.vsh` or `.fsh` extension.

```kotlin
val elementBounds = Mine2DUniform.elementBounds()
val radius = Mine2DUniform.float("Radius", defaultValue = 0f)
val edgeSoftness = Mine2DUniform.float("EdgeSoftness", defaultValue = 1f)

val roundedRectShader = Mine2DShader.register(
    location = Identifier.fromNamespaceAndPath("examplemod", "pipeline/rounded_rect"),
    vertexShader = Identifier.fromNamespaceAndPath("examplemod", "core/rounded_rect"),
    fragmentShader = Identifier.fromNamespaceAndPath("examplemod", "core/rounded_rect"),
    uniformBlock = Mine2DUniformBlock(
        "Mine2DMaterial",
        elementBounds,
        radius,
        edgeSoftness,
    ),
)

val roundedPanel = roundedRectShader.material {
    set(radius, 8f)
    set(edgeSoftness, 0.75f)
}

draw.withMaterial(roundedPanel) {
    quad(20f, 20f, 80f, 24f, 0xFFFFFFFF.toInt())
    circle(60f, 72f, 20f, 0xFFFFFFFF.toInt(), segments = 32)
}
```

Declare a std140 block in the shader sources with names, types, and ordering matching the keys:

```glsl
layout(std140) uniform Mine2DMaterial {
    vec4 ElementBounds;
    float Radius;
    float EdgeSoftness;
};
```

Set an individual layout background with the independent `UiStyle.backgroundColor` and
`UiStyle.backgroundMaterial` properties. A non-null color draws the background with the specified
material, or with the `Mine2DEngine.material` active during rendering when the material is null. A
material without a color does not create a background. These properties are local to the element
and are not inherited by children. During style-sheet composition they are overridden
independently, so a later declaration can replace a supplied material while retaining a supplied
color; `null` means unspecified during this composition and cannot explicitly clear a material.
Paragraph text uses Minecraft's text rendering path and is not affected by the background material.

```kotlin
val panel = div(
    UiStyle(
        width = 120f.px,
        height = 40f.px,
        backgroundColor = 0xFFFFFFFF.toInt(),
        backgroundMaterial = roundedPanel,
        border = UiBorders(1f.px, 0xFF808080.toInt()),
        borderRadius = UiBorderRadii(8f.px),
    ),
)
```

`Mine2DUniform.int`, `float`, `vec2`, `vec3`, `vec4`, and `mat4` create typed material keys. Keys without defaults are required when the material is built. The engine fills these semantic keys for every draw:

- `elementBounds()`: painted `(left, top, width, height)`
- `contentBounds()`: area inside padding; equal to element bounds for ordinary polygon calls
- `viewportSize()`: GUI `(width, height)`
- `timeSeconds()`: monotonic elapsed seconds

For textures, pass `Mine2DSampler` keys to `register(samplers = ...)` and assign every key while building the material. Use `bind(key, textureView, gpuSampler)` for a fixed texture. A shader that needs the scene behind the GUI can use `bindGuiBackground(key)`:

```kotlin
val backgroundSampler = Mine2DSampler("BackgroundSampler")
val blurShader = Mine2DShader.register(
    location = Identifier.fromNamespaceAndPath("examplemod", "pipeline/blur"),
    vertexShader = Identifier.fromNamespaceAndPath("examplemod", "core/blur"),
    fragmentShader = Identifier.fromNamespaceAndPath("examplemod", "core/blur"),
    samplers = listOf(backgroundSampler),
)

val blur = blurShader.material {
    bindGuiBackground(backgroundSampler)
}
```

The GUI background is copied to a separate full-resolution texture immediately before extracted GUI elements are rendered. All `bindGuiBackground` bindings in that frame share the snapshot. This avoids the invalid feedback loop caused by sampling the main color texture while it is also the active render attachment. Pass a `FilterMode` as the second argument to override the default linear filtering. Uniforms, vectors, matrices, and sampler assignments are validated when the material is built, and immutable binding descriptions are queued for rendering.

A Mine2D shader must use `DefaultVertexFormat.POSITION_COLOR` at binding 0 and `PrimitiveTopology.TRIANGLES`. `Mine2DShader.register` enforces the vertex format and topology and disables culling. Use `Mine2DShader.from(...)` only for a compatible pipeline that already declares the same uniform and sampler bindings. Polygons with material bindings use one draw each so different uniform values remain isolated; the standard material without custom bindings can still be batched normally.

## Building from source

Use JDK 25 and the included Gradle wrapper:

```shell
./gradlew test
./gradlew build
```

Build artifacts are written to `build/libs/`.

## License

Mine2DEngine is available under the [MIT License](LICENSE.txt).
