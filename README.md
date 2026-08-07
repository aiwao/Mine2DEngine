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
| `text(font, text, x, y, color, dropShadow)` | Draws text using a loaded `Mine2DFont`. |
| `withShader(shader) { ... }` | Temporarily changes the default polygon shader and restores it after the block. |

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
    draw.text(font, "Mine2DEngine", 16, 16, 0xFFFFFFFF.toInt(), dropShadow = true)

    val width = font.width("Mine2DEngine")
    val lineHeight = font.lineHeight
}
```

`Mine2DFont.load`, `width`, and `close` must run on the render thread. Do not load a font once per frame. If you recreate a font during a resource reload, close the previous instance first.

## Layout engine

The layout package builds trees from `Div`, `Paragraph`, and `Button`. It follows a CSS-like box model:

- `width` and `height` specify the content box. A `null` value shrinks to the text or children.
- Padding is inside the painted background; margin is outside it.
- A `Div` places direct children vertically or horizontally.
- `alignment` aligns children and text horizontally.
- Fonts are inherited from ancestors and may be overridden by a child.
- Paragraph newlines create multiple lines.

```kotlin
import io.github.aiwao.mine2dengine.layout.LayoutEngine
import io.github.aiwao.mine2dengine.layout.UiAlignment
import io.github.aiwao.mine2dengine.layout.UiDirection
import io.github.aiwao.mine2dengine.layout.UiEdges
import io.github.aiwao.mine2dengine.layout.UiStyle
import io.github.aiwao.mine2dengine.layout.div

val root = div(
    UiStyle(
        font = font,
        width = 180f,
        padding = UiEdges(8f),
        backgroundColor = 0xD0202020.toInt(),
        alignment = UiAlignment.CENTER,
    ),
) {
    p("Mine2DEngine", UiStyle(color = 0xFFFFCC00.toInt()))
    p("A lightweight Fabric UI", UiStyle(dropShadow = false))

    div(
        UiStyle(
            direction = UiDirection.HORIZONTAL,
            margin = UiEdges(top = 6f, right = 0f, bottom = 0f, left = 0f),
        ),
    ) {
        button("OK", onClick = { println("Clicked OK") })
        button("Cancel", onClick = { println("Clicked Cancel") })
    }
}

val layout = LayoutEngine(draw).render(root, left = 12f, top = 12f)
```

Keep the returned `UiLayout` to perform hit testing or dispatch a click using the same GUI coordinate system:

```kotlin
val element = layout.elementAt(mouseX.toFloat(), mouseY.toFloat())
val handled = layout.click(mouseX.toFloat(), mouseY.toFloat())
```

`layout(root)` calculates geometry without drawing. `render(root)` calculates and draws, while `render(existingLayout)` draws previously calculated geometry again. Recalculate the layout after changing text, styles, or children.

## Custom shaders

Register custom pipelines during client initialization. Vertex and fragment shader identifiers are relative to `assets/<namespace>/shaders/` and omit the `.vsh` or `.fsh` extension.

```kotlin
val accentShader = Mine2DShader.register(
    location = Identifier.fromNamespaceAndPath("examplemod", "pipeline/accent"),
    vertexShader = Identifier.fromNamespaceAndPath("examplemod", "core/accent"),
    fragmentShader = Identifier.fromNamespaceAndPath("examplemod", "core/accent"),
)

draw.withShader(accentShader) {
    quad(20f, 20f, 80f, 24f, 0xFFFFFFFF.toInt())
    circle(60f, 72f, 20f, 0xFFFFFFFF.toInt(), segments = 32)
}
```

A Mine2D shader must use `DefaultVertexFormat.POSITION_COLOR` at binding 0 and `PrimitiveTopology.TRIANGLES`. `Mine2DShader.register` enforces that vertex format and topology and disables culling. Use `Mine2DShader.from(...)` only for an already-created compatible pipeline.

## Building from source

Use JDK 25 and the included Gradle wrapper:

```shell
./gradlew test
./gradlew build
```

Build artifacts are written to `build/libs/`.

## License

Mine2DEngine is available under the [MIT License](LICENSE.txt).
