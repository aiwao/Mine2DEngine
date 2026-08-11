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
| `line(startX, startY, endX, endY, width, color)` | Draws a filled line with butt caps. |
| `circle(centerX, centerY, radius, color, segments)` | Draws a filled regular-polygon approximation of a circle. More segments produce a smoother edge. |
| `boxShadow(x, y, width, height, ...)` | Draws a soft rounded-box shadow without drawing the box itself. |
| `textShadow(font, text, x, y, ...)` | Draws a configurable glyph shadow without drawing the foreground text. |
| `text(font, text, x, y, color)` | Draws text using a loaded `Mine2DFont`. |
| `withMaterial(material) { ... }` | Temporarily changes the default polygon material and restores it after the block. |

Polygon points may use clockwise or counterclockwise order. A polygon must have at least three distinct points, a non-zero area, and no self-intersections. Consecutive duplicate points and redundant collinear points are removed automatically. Lines require different endpoints and a positive width; circles require a positive radius and at least three segments.

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

The layout package builds trees from `Div` and `Paragraph`. It follows a CSS-like box model:

- Every `UiStyle` property defaults to `null`, meaning unspecified. Style selectors and the
  element's own style are composed first, then unspecified properties receive their initial
  values during layout. Explicit initial values such as `padding = UiEdges()`, `gap = 0f`, or
  `position = UiPosition.STATIC` therefore override lower-priority declarations.

- `boxSizing` controls whether a non-null `width` or `height` specifies the content box or the
  complete padded box. It defaults to `UiBoxSizing.CONTENT_BOX`; use `UiBoxSizing.BORDER_BOX` to
  include padding in the specified size. A `null` size still shrinks to the text or children.
- Set `width` and `height` with `Float.px` (for example, `120f.px`) or `Float.percent` (for
  example, `50f.percent`). A percentage uses the corresponding resolved containing-block
  dimension, normally the matching content dimension of its parent.
- Padding is inside the painted background; margin is outside it.
- `position` supports `UiPosition.STATIC`, `RELATIVE`, and `ABSOLUTE`. The nullable `left`, `top`,
  `right`, and `bottom` values represent CSS `auto` when null. Relative elements move from their
  normal position without changing the space they occupy. Absolute elements are removed from
  normal flow and use the padded box of their nearest non-static ancestor, or the root box when
  there is none. Paired horizontal or vertical insets stretch an automatic width or height.
  The root itself remains at the coordinates passed to `LayoutEngine.layout`.
- `backgroundColor` is the sole condition for drawing an element background. When it is non-null,
  the background uses `backgroundMaterial`, or the renderer's current material when no background
  material is specified. `backgroundMaterial` alone does not draw anything.
- `boxShadow` paints a soft rounded-box shadow behind an element. It is local to that element and
  does not affect layout size or pointer bounds.
- `dropShadow` follows the composited alpha of an element's background, text, and descendants, like
  CSS `filter: drop-shadow()`. It is local to that element and does not affect layout or hit bounds.
- When the function passed to `noneDisplay` returns `true`, the element and its descendants are removed from layout, rendering, and pointer input, like CSS `display: none`. It is also evaluated before rendering and pointer operations; the layout is recalculated automatically when its value changes.
- `Div` places direct children vertically or horizontally.
- A `Div` can receive `styleSheets` scoped to itself and its descendants. Nested container scopes
  participate in the same specificity cascade, while a nested component stops an outer local sheet
  below the component root. Mutating a container's sheet list requires recalculating the layout.
- A `StyleSheet` passed to `LayoutEngine.layout` applies rules to matching element tags, IDs, or
  class names. `newStyle` accepts one `StyleSheetTarget`. Use `TargetAnd` for compound selectors
  such as `button.primary`, `.card.active.large`, and `div#main`; use `TargetOr` for selector lists
  such as `h1, h2, h3`. Specificity is compared by ID, then class/pseudo-class, then tag; later
  rules win at equal specificity, and an element's own specified style values win last.
- `TargetScope` is the CSS `:scope` selector. It selects the layout root in a global sheet, or the
  container/component root in a scoped sheet, and has pseudo-class specificity.
- `TargetWildcard` is the CSS universal selector `*`. It matches every element and adds no
  specificity, including when used in a chain such as `.parent > *`.
- `TargetCombinator(left, combinator, right)` joins selectors with `StyleSheetCombinator`:
  `DESCENDANT` (`" "`), `CHILD` (`">"`), `ADJACENT_SIBLING` (`"+"`), and `GENERAL_SIBLING`
  (`"~"`). Combinators can be nested into a chain; `combine`, `descendant`, `child`,
  `adjacentSibling`, and `generalSibling` are builder alternatives.
- `id` is an HTML-compatible element ID. `className` is a read-only `Set<String>`, specified with
  values such as `setOf("card", "active")`. A `String` is also accepted as shorthand for a
  single-element set and is not split on whitespace. Class and tag names are matched exactly and
  may contain whitespace.
- `horizontalAlignment` and `verticalAlignment` align children and text on both axes.
- `color`, `font`, and `textShadow` are inherited from ancestors and may be overridden by a child.
  A null value inherits the parent. At the root, color defaults to opaque white and text shadow
  defaults to none. Set `textShadow = UiTextShadow.NONE` to explicitly clear an inherited shadow.
- Paragraph newlines create multiple lines.

```kotlin
import io.github.aiwao.mine2dengine.layout.LayoutEngine
import io.github.aiwao.mine2dengine.layout.Paragraph
import io.github.aiwao.mine2dengine.layout.StyleSheet
import io.github.aiwao.mine2dengine.layout.StyleSheetCombinator
import io.github.aiwao.mine2dengine.layout.StyleSheetObject
import io.github.aiwao.mine2dengine.layout.TargetAnd
import io.github.aiwao.mine2dengine.layout.TargetClass
import io.github.aiwao.mine2dengine.layout.TargetCombinator
import io.github.aiwao.mine2dengine.layout.TargetId
import io.github.aiwao.mine2dengine.layout.TargetOr
import io.github.aiwao.mine2dengine.layout.TargetScope
import io.github.aiwao.mine2dengine.layout.TargetTag
import io.github.aiwao.mine2dengine.layout.TargetWildcard
import io.github.aiwao.mine2dengine.layout.UiBoxShadow
import io.github.aiwao.mine2dengine.layout.UiBoxSizing
import io.github.aiwao.mine2dengine.layout.UiDirection
import io.github.aiwao.mine2dengine.layout.UiDropShadow
import io.github.aiwao.mine2dengine.layout.UiEdges
import io.github.aiwao.mine2dengine.layout.UiHorizontalAlignment
import io.github.aiwao.mine2dengine.layout.UiPosition
import io.github.aiwao.mine2dengine.layout.UiStyle
import io.github.aiwao.mine2dengine.layout.UiTextShadow
import io.github.aiwao.mine2dengine.layout.UiVerticalAlignment
import io.github.aiwao.mine2dengine.layout.descendant
import io.github.aiwao.mine2dengine.layout.div
import io.github.aiwao.mine2dengine.layout.percent
import io.github.aiwao.mine2dengine.layout.px
import io.github.aiwao.mine2dengine.layout.uiComponent

val root = div(
    UiStyle(
        font = font,
        width = 180f.px,
        height = 100f.px,
        padding = UiEdges(8f),
        boxSizing = UiBoxSizing.BORDER_BOX,
        position = UiPosition.RELATIVE,
        backgroundColor = 0xD0202020.toInt(),
        boxShadow = UiBoxShadow(
            color = 0x80000000.toInt(),
            offsetY = 3f,
            blurRadius = 5f,
            cornerRadius = 6f,
        ),
        textShadow = UiTextShadow(
            color = 0xA0000000.toInt(),
            offsetY = 2f,
            blurRadius = 1f,
        ),
        horizontalAlignment = UiHorizontalAlignment.CENTER,
        verticalAlignment = UiVerticalAlignment.CENTER,
    ),
) {
    p(
        "v2",
        UiStyle(
            position = UiPosition.ABSOLUTE,
            top = 6f,
            right = 6f,
        ),
    )
    p(
        "Mine2DEngine",
        UiStyle(
            color = 0xFFFFCC00.toInt(),
            dropShadow = UiDropShadow(
                color = 0x60000000,
                offsetY = 3f,
                blurRadius = 4f,
            ),
        ),
        onClick = { event -> println("Title: button=${event.button()}") },
        onMouseMove = { x, y -> println("Title: x=$x, y=$y") },
        onDrag = { event ->
            println("Dragging title: x=${event.x()}, y=${event.y()}, button=${event.button()}")
        },
        onMouseOver = { println("Pointer entered title") },
        onMouseOut = { println("Pointer left title") },
    )
    p("A lightweight Fabric UI", UiStyle(textShadow = UiTextShadow.NONE))

    div(
        UiStyle(
            width = 100f.percent,
            direction = UiDirection.HORIZONTAL,
            margin = UiEdges(top = 6f, right = 0f, bottom = 0f, left = 0f),
        ),
    ) {
        div(onClick = { event -> println("OK: button=${event.button()}") }) {
            p("OK")
        }
        div(onClick = { event -> println("Cancel: button=${event.button()}") }) {
            p("Cancel")
        }
    }
}

val layout = LayoutEngine.layout(root, left = 12f, top = 12f)
layout.render(draw)
```

Define reusable UI trees as `UiComponent` values. Every `component(...)` call adds a fresh
`UiElement` tree to its parent, where it participates in the parent's measurement, rendering, and
input handling. Inherited text styles and global `StyleSheet` values passed to
`LayoutEngine.layout` continue across component boundaries.

```kotlin
val actionBar = uiComponent(styleSheet = actionBarStyleSheet) {
    div(UiStyle(direction = UiDirection.HORIZONTAL, gap = 4f)) {
        p("Save", className = "action")
        p("Close", className = "action")
    }
}

val composedLayout = LayoutEngine.layout(
    rootStyle = UiStyle(font = font),
    left = 12f,
    top = 12f,
) {
    p("Editor")
    component(actionBar)
    component(
        actionBar,
        styleSheet = compactActionBarStyleSheet,
    ) // Added only to this instance
}
```

Sheets attached to a `Div` or passed to `uiComponent` apply only to that scope root and its
contents. `TargetScope` selects that root. Their local
selectors cannot inspect ancestors or siblings at the call site. At a nested child component, a
parent component's local sheet can style the child's root but does not enter its contents. A sheet
passed to `component(...)` follows the component's default sheets. As in the regular cascade,
specificity is compared first; later sheets win at equal specificity, and the element's own style
wins last.

The component factory runs once when `component(...)` adds it. When the same `UiLayout` is
recalculated after a `noneDisplay` change, it reuses the existing element tree and preserves state
such as hover and drag state.

For example, this dynamic rule applies a drop shadow only to paragraph descendants, including the
one inside the nested `Div`. A dynamic declaration receives each matching element whenever its
style is resolved. It may run more than once and should not perform side effects. Every element
exposes its read-only `tag`; tag names are matched exactly and may contain whitespace:

```kotlin
object LabelStyleSheet : StyleSheet {
    override val styles = mutableListOf<StyleSheetObject>()

    init {
        newStyle(TargetScope descendant TargetWildcard) { descendant ->
            UiStyle(
                dropShadow = if (descendant.tag == "p") UiDropShadow() else null,
            )
        }
    }
}

val labels = div(
    tag = "section",
    style = UiStyle(font = font),
    styleSheets = listOf(LabelStyleSheet),
) {
    p("First")
    div {
        p("Second")
    }
}
```

CSS-like rules can be shared independently of the element tree. Wrap `newStyle` calls in an
initializer; each call appends one rule to `styles`:

```kotlin
object ExampleStyleSheet : StyleSheet {
    override val styles = mutableListOf<StyleSheetObject>()

    init {
        newStyle(
            target = TargetOr(TargetClass("example-class"), TargetTag("div")),
            style = UiStyle(color = 0xFFFF0000.toInt()),
        )
        newStyle(
            target = TargetAnd(
                TargetClass("card"),
                TargetClass("active"),
                TargetClass("large"),
            ),
            style = UiStyle(width = 120f.px),
        )
        newStyle(
            target = TargetAnd(TargetTag("div"), TargetId("main")),
            style = UiStyle(backgroundColor = 0xFF202020.toInt()),
        )
        newStyle(
            target = TargetCombinator(
                left = TargetClass("screen"),
                combinator = StyleSheetCombinator.CHILD,
                right = TargetWildcard,
            ),
            style = UiStyle(padding = UiEdges(4f)),
        )
    }
}

val styledRoot = div(
    tag = "div",
    id = "main",
    className = "screen",
    style = UiStyle(font = font),
) {
    p("Red Text")
}

val styledLayout = LayoutEngine.layout(styledRoot, ExampleStyleSheet)
```

`style` can also be a function that receives the concrete element. It is resolved from the
element's current state whenever layout or rendering uses it, so states such as `hovering` and
`dragging` can change appearance without callbacks mutating the style:

```kotlin
val hoverable = div(
    style = { element ->
        UiStyle(
            width = 120f.px,
            height = 24f.px,
            backgroundColor = if (element.hovering) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF000000.toInt()
            },
        )
    },
)

val hoverableLayout = LayoutEngine.layout(hoverable)
hoverableLayout.render(draw)
hoverableLayout.mouseMove(mouseX, mouseY)
hoverableLayout.render(draw)
```

Dynamic styles are supported by `div`, `p` / `paragraph`, and style-sheet declarations. Redrawing an existing
layout refreshes drawing properties such as inherited text colors, shadows, background colors, and
background materials.
Drawing-only changes do not require relayout. Recalculate the
layout when a resolved element style or style-sheet declaration changes sizing, spacing,
direction, alignment, positioning, insets, or font. Assigning `element.style` replaces its dynamic
style with that static value.

Every element supports `onClick`, `onMouseMove`, `onDrag`, `onMouseOver`, and `onMouseOut`, including `div` and `p` / `paragraph`. Set an element's `disabled` property to `true` to prevent its `onClick` callback from running until it is enabled again. The read-only `hovering` property reports whether the pointer is inside an element. Keep the returned `UiLayout` to perform hit testing and dispatch pointer input using the same GUI coordinate system. Pass Minecraft's `MouseButtonEvent` to `mouseClick`; the event coordinates identify the topmost clickable element, start its drag state, and forward the event to its `onClick` callback. Pass the mouse coordinates to `mouseMove` to update `hovering`, invoke boundary-crossing and `onMouseMove` callbacks, and invoke the dragging element's `onDrag` callback. The `MouseButtonEvent` passed to `onDrag` uses the current mouse coordinates and retains the button and modifier information from the `mouseClick` event that started the drag. A drag continues outside the element's bounds until `mouseRelease` is called:

```kotlin
val element = layout.elementAt(mouseX.toFloat(), mouseY.toFloat())
val handled = layout.mouseClick(event)
val moveHandled = layout.mouseMove(mouseX, mouseY)
val releaseHandled = layout.mouseRelease()
```

`LayoutEngine.layout(root)` calculates geometry without drawing. Call `render(renderer)` on the returned layout to draw it. Changes to `noneDisplay` trigger automatic recalculation; recalculate the layout after changing text, other styles, or children.

To move an already calculated layout, change its `left` / `top`. Every element and hit-test area moves with it. `layout.render(renderer, left, top)` moves and redraws it in one call.

```kotlin
layout.left = 24f
layout.top = 32f
layout.render(draw)

// Equivalent shorthand
layout.render(draw, left = 24f, top = 32f)
```

`UiBoxShadow` supports ARGB `color`, finite `offsetX` / `offsetY`, non-negative `blurRadius`,
finite positive or negative `spreadRadius`, and non-negative `cornerRadius`. Spread and blur are
paint-only overflow: they do not change the element's geometry or hit area. The shadow follows the
current GUI pose and scissor. For a standalone shadow outside the layout engine, call
`Mine2DEngine.boxShadow(...)` before drawing its foreground box. This built-in effect follows a
rectangle or rounded rectangle; use a custom shader or off-screen mask for an arbitrary alpha shape.

`UiDropShadow` supports ARGB `color`, finite `offsetX` / `offsetY`, and non-negative `blurRadius`.
The renderer composites the element's background, text, and complete descendant subtree into a
temporary alpha mask, then draws one Gaussian-blurred copy behind the original pixels. The property
is not inherited, supports nested drop shadows, and corresponds to one CSS
`filter: drop-shadow(offsetX offsetY blurRadius color)` operation.

`UiTextShadow` supports ARGB `color`, finite `offsetX` / `offsetY`, and non-negative `blurRadius`.
It is inherited with other text properties and does not affect layout or hit bounds. Use
`UiTextShadow.NONE` to explicitly clear an inherited shadow. These values correspond to one CSS
`text-shadow`. Positive blur is sampled from each glyph's alpha by a Gaussian shader in one glyph
draw instead of repeatedly drawing displaced copies of the text. Outside the layout engine, call
`Mine2DEngine.textShadow(...)` immediately before the foreground `text(...)` call.

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
