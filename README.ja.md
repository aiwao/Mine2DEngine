# Mine2DEngine

[English](README.md)

Mine2DEngine は、Minecraft Fabric Mod 向けのクライアントサイド 2D 描画ライブラリです。

| 必要環境 | バージョン |
| --- | --- |
| Minecraft | `26.2` |
| Java | `25` |
| Fabric Loader | `0.19.3` 以降 |
| Fabric Language Kotlin | `1.13.13+kotlin.2.4.10` 以降 |
| Fabric API | `0.156.0+26.2` |

## インストール

リリースは Maven Central で公開されています。Gradle のビルドで `mavenCentral()` を使用し、Mine2DEngine を Mod の依存関係に追加してください。

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    modImplementation("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
}
```

Mine2DEngine を別の Mod としてインストールする場合は、自分の Mod の `fabric.mod.json` に依存関係を宣言します。

```json
{
  "depends": {
    "mine2dengine": ">=1.0.0"
  }
}
```

代わりに、Fabric Loom でライブラリを自分の Mod 内へ同梱することもできます。

```kotlin
dependencies {
    modImplementation("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
    include("io.github.aiwao.mine2dengine:mine2dengine:1.0.0")
}
```

自分の Mod 側でも Fabric API と Fabric Language Kotlin を用意する必要があります。

## クイックスタート

クライアント初期化処理から `Mine2DEngine.initialize()` を一度呼び出します。その後、HUD 要素または画面に渡された `GuiGraphicsExtractor` からエンジンを作り、そのフレームの描画命令を発行します。

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

座標は GUI 座標、色は Minecraft の ARGB 整数（`0xAARRGGBB`）です。各描画メソッドは現在のフレームへ不変なレンダー状態を追加し、その場ですぐに描画するわけではありません。

## 描画 API

| メソッド | 説明                                                                                                                   |
| --- |------------------------------------------------------------------------------------------------------------------------|
| `polygon(...)` | 単純な凸・凹ポリゴンを描画します。頂点ごとの色には `Mine2DVertex`、単色には色と JOML の `Vector2fc` の各点を渡します。 |
| `quad(x, y, width, height, color)` | 塗りつぶした矩形を描画します。                                                                                         |
| `line(startX, startY, endX, endY, width, color)` | 端が平らな塗りつぶし線を描画します。                                                                                   |
| `circle(centerX, centerY, radius, color, segments)` | 正多角形で近似した塗りつぶし円を描画します。segmentsを増やすほど輪郭が滑らかになります。                               |
| `text(font, text, x, y, color, dropShadow)` | 読み込み済みの `Mine2DFont` で文字列を描画します。                                                                     |
| `withShader(shader) { ... }` | ブロック内だけ既定のポリゴンシェーダーを変更し、終了後に元へ戻します。                                                 |

ポリゴンの各点は時計回り、反時計回りのどちらでも指定できます。3 個以上の異なる点と 0 ではない面積が必要で、自己交差はできません。連続する重複点と不要な同一直線上の点は自動的に取り除かれます。線には異なる始点・終点と正の幅、円には正の半径と 3 以上の分割数が必要です。

エンジンは呼び出しごとに `graphics.pose()` と有効なシザー矩形を取得します。そのため、Minecraft の GUI 座標変換とクリッピングを通常どおり利用できます。

```kotlin
graphics.enableScissor(10, 10, 170, 110)
try {
    Mine2DEngine(graphics).quad(0f, 0f, 240f, 140f, 0xAA336699.toInt())
} finally {
    graphics.disableScissor()
}
```

## TrueType フォント

クライアントリソース内に `.ttf` ファイルを配置します。次は配置例です。

```text
src/main/resources/
└── assets/
    └── examplemod/
        └── font/
            └── ui.ttf
```

フォントは GPU リソースとネイティブリソースを作成します。クライアントリソースが利用可能になった後に一度だけ読み込み、開いている間だけ使用し、レンダースレッド上で閉じてください。Fabric のクライアントライフサイクルイベントを使うと管理しやすくなります。

```kotlin
import io.github.aiwao.mine2dengine.Mine2DFont
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.resources.Identifier

private var uiFont: Mine2DFont? = null

// onInitializeClient() から次のコールバックを登録します。
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

読み込んだフォントで文字列を描画・計測します。

```kotlin
uiFont?.let { font ->
    draw.text(font, "Mine2DEngine", 16, 16, 0xFFFFFFFF.toInt(), dropShadow = true)

    val width = font.width("Mine2DEngine")
    val lineHeight = font.lineHeight
}
```

`Mine2DFont.load`、`width`、`close` はレンダースレッド上で実行する必要があります。フレームごとにフォントを読み込まないでください。リソースの再読み込み時にフォントを作り直す場合は、先に以前のインスタンスを閉じます。

Mine2DEngine は `Mine2DFont` が作成したグリフアトラスだけにリニアフィルタを適用します。Minecraft のほかのフォントは元のサンプリング方式を維持しつつ、オーバーサンプリングした TrueType グリフを滑らかに縮小します。

## レイアウトエンジン

レイアウトパッケージでは `Div`、`Paragraph`、`Button` からツリーを作ります。CSS に似たボックスモデルを採用しています。

- `width` と `height` はコンテンツボックスを指定します。`null` の場合は文字列または子要素に合わせて縮みます。
- パディングは描画される背景の内側、マージンは外側です。
- `Div` と `Button` は直接の子要素を縦または横に並べます。
- `alignment` は子要素と文字列を水平方向に配置します。
- フォントは祖先から継承され、子要素で上書きできます。
- 段落内の改行は複数行になります。

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
    p(
        "Mine2DEngine",
        UiStyle(color = 0xFFFFCC00.toInt()),
        onClick = { event -> println("タイトル: button=${event.button()}") },
        onMouseMove = { x, y -> println("タイトル: x=$x, y=$y") },
        onDrag = { x, y -> println("タイトルをドラッグ中: x=$x, y=$y") },
        onMouseOver = { println("タイトルにカーソルが入りました") },
        onMouseOut = { println("タイトルからカーソルが出ました") },
    )
    p("軽量な Fabric UI", UiStyle(dropShadow = false))

    div(
        UiStyle(
            direction = UiDirection.HORIZONTAL,
            margin = UiEdges(top = 6f, right = 0f, bottom = 0f, left = 0f),
        ),
    ) {
        button(onClick = { event -> println("OK: button=${event.button()}") }) {
            p("OK")
        }
        button(onClick = { event -> println("キャンセル: button=${event.button()}") }) {
            p("キャンセル")
        }
    }
}

val layout = LayoutEngine().layout(root, left = 12f, top = 12f)
layout.render(draw)
```

`style` には具体的な要素を受け取る関数も指定できます。レイアウトまたは描画で使われるたびに
要素の現在の状態から解決されるため、コールバックからスタイルを書き換えなくても `hovering` や
`dragging` に応じて見た目を変えられます。

```kotlin
val hoverable = div(
    style = { element ->
        UiStyle(
            width = 120f,
            height = 24f,
            backgroundColor = if (element.hovering) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF000000.toInt()
            },
        )
    },
)

val hoverableLayout = LayoutEngine().layout(hoverable)
hoverableLayout.render(draw)
hoverableLayout.mouseMove(mouseX, mouseY)
hoverableLayout.render(draw)
```

動的スタイルは `div`、`p` / `paragraph`、`button` で利用できます。既存レイアウトを再描画すると
色などの描画プロパティが更新されます。解決後のスタイルによってサイズ、余白、方向、配置、フォントが
変わる場合は、レイアウトを再計算してください。`element.style` に代入すると、動的スタイルはその
静的な値で置き換えられます。

`div`、`p` / `paragraph`、`button` を含むすべての要素で `onClick`、`onMouseMove`、`onDrag`、`onMouseOver`、`onMouseOut` を利用できます。読み取り専用の `hovering` プロパティで、カーソルが要素内にあるかを確認できます。返された `UiLayout` を保持すると、同じ GUI 座標系でヒットテストやポインター入力の通知ができます。Minecraft の `MouseButtonEvent` を `click` に渡すと、イベントの座標で最前面のクリック可能な要素を特定してドラッグ状態を開始し、そのイベントを要素の `onClick` に渡します。`mouseMove` にマウス座標を渡すと、`hovering` の更新、境界をまたいだ際のコールバック、座標上で最前面の `onMouseMove`、ドラッグ中の要素の `onDrag` が呼び出されます。ドラッグは要素の領域外でも継続し、`release` を呼ぶと終了します。

```kotlin
val element = layout.elementAt(mouseX.toFloat(), mouseY.toFloat())
val handled = layout.click(event)
val moveHandled = layout.mouseMove(mouseX, mouseY)
val releaseHandled = layout.release()
```

`LayoutEngine().layout(root)` は描画せずにジオメトリを計算します。返されたレイアウトの `render(renderer)` を呼び出すと描画できます。文字列、スタイル、子要素を変更した後はレイアウトを再計算してください。

計算済みレイアウトの位置だけを変える場合は、`left` / `top` を変更します。すべての要素とヒットテスト領域が一緒に移動します。`layout.render(renderer, left, top)` を使うと、移動と再描画を一度に行えます。

```kotlin
layout.left = 24f
layout.top = 32f
layout.render(draw)

// 同等の短縮形
layout.render(draw, left = 24f, top = 32f)
```

## カスタムシェーダー

クライアント初期化時にカスタムパイプラインを登録します。頂点・フラグメントシェーダーの識別子は `assets/<namespace>/shaders/` からの相対位置で、拡張子 `.vsh` または `.fsh` は付けません。

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

Mine2D のシェーダーは、バインディング 0 で `DefaultVertexFormat.POSITION_COLOR`、プリミティブトポロジーに `PrimitiveTopology.TRIANGLES` を使う必要があります。`Mine2DShader.register` はこの頂点形式とトポロジーを適用し、カリングを無効にします。すでに作成済みで互換性のあるパイプラインに限り `Mine2DShader.from(...)` を使用してください。

## ソースからのビルド

JDK 25 と同梱の Gradle Wrapper を使用します。

```shell
./gradlew test
./gradlew build
```

ビルド成果物は `build/libs/` に出力されます。

## ライセンス

Mine2DEngine は [MIT License](LICENSE.txt) の下で利用できます。
