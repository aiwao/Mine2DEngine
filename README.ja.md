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
| `boxShadow(x, y, width, height, ...)` | 前景のbox自体を描かず、柔らかい角丸box shadowを描画します。                                                           |
| `textShadow(font, text, x, y, ...)` | 前景文字を描かず、設定可能なglyph shadowを描画します。                                                                |
| `text(font, text, x, y, color)` | 読み込み済みの `Mine2DFont` で文字列を描画します。                                                                                 |
| `withMaterial(material) { ... }` | ブロック内だけ既定のポリゴンMaterialを変更し、終了後に元へ戻します。                                                |

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

テキスト座標、水平advance、行の高さは浮動小数のGUI単位で扱われます。
レイアウトのline boxはTrueTypeフォントのascender、descender、baseline間隔を使い、
文字列を垂直方向の中央に配置する場合にも反映します。
Paragraphのレイアウトは浮動小数のまま保ち、最終的なグリフの垂直原点だけを
フレームバッファのピクセルグリッドへ合わせ、サブピクセルbaselineのぼけを防ぎます。

`Mine2DFont.load`、`width`、`close` はレンダースレッド上で実行する必要があります。フレームごとにフォントを読み込まないでください。リソースの再読み込み時にフォントを作り直す場合は、先に以前のインスタンスを閉じます。

Mine2DEngine は `Mine2DFont` が作成したグリフアトラスだけにリニアフィルタを適用します。Minecraft のほかのフォントは元のサンプリング方式を維持しつつ、オーバーサンプリングした TrueType グリフを滑らかに縮小します。

## レイアウトエンジン

レイアウトパッケージでは `Div` と `Paragraph` からツリーを作ります。CSS に似たボックスモデルを採用しています。

- `boxSizing` は、`null` ではない `width` と `height` がコンテンツボックスとpaddingを含む
  ボックスのどちらを指定するかを決めます。既定値は `UiBoxSizing.CONTENT_BOX` です。
  `UiBoxSizing.BORDER_BOX` を指定すると、指定寸法にpaddingが含まれます。寸法が `null` の場合は
  どちらでも文字列または子要素に合わせて縮みます。
- `width` と `height` は `Float.px`（例: `120f.px`）または `Float.percent`
  （例: `50f.percent`）で指定します。パーセント値は、通常は親要素の対応するcontent寸法となる、
  解決済みの包含ブロック寸法を基準にします。
- パディングは描画される背景の内側、マージンは外側です。
- `position` は `UiPosition.STATIC`、`RELATIVE`、`ABSOLUTE` に対応します。nullを指定した
  `left`、`top`、`right`、`bottom` はCSSの `auto` として扱われます。relative要素は通常位置から
  移動しますが、元の占有領域は変わりません。absolute要素は通常フローから外れ、最も近い
  non-static祖先のpadding box（なければルートbox）を基準に配置されます。横または縦の両側を
  指定し、対応する幅または高さがnullなら、その範囲まで自動的に伸びます。ルート自体は
  `LayoutEngine.layout` に渡した座標に配置されます。
- 要素の背景を描画するかどうかは `backgroundColor` だけで決まります。nullではない場合は
  `backgroundMaterial` を使い、背景Materialが未指定ならrendererの現在のMaterialを使います。
  `backgroundMaterial` だけを指定しても何も描画されません。
- `boxShadow` は要素の背後に柔らかい角丸box shadowを描画します。要素内だけの指定であり、
  レイアウト寸法やポインター判定領域には影響しません。
- `dropShadow` はCSSの `filter: drop-shadow()` と同様に、要素の背景・文字・子孫を合成した
  alpha形状へ影を付けます。要素内だけの指定であり、レイアウトやヒット領域には影響しません。
- `noneDisplay` に渡した関数が `true` を返すと、CSS の `display: none` と同様に、その要素と子孫が配置、描画、ポインター入力の対象から外れます。この関数は描画やポインター操作の前にも評価され、戻り値が変わるとレイアウトが自動的に再計算されます。
- `Div` は直接の子要素を縦または横に並べます。
- `Div` の `descendantStyle` は、CSS の `.parent *` と同様に、各子孫を受け取って `UiStyle` を
  解決します。子孫の型や現在の状態を参照できます。子孫自身のstyleにあるデフォルト以外の値が
  優先されます。ネストしたコンテナにnullではない `descendantStyle` がある場合、その配下では近い
  指定が優先されます。
- `childStyle` はCSSの `.parent > *` と同様に、直接の子だけのstyleを解決します。両方が適用される
  場合は `childStyle` が `descendantStyle` より優先され、子自身のデフォルト以外のstyleがさらに
  優先されます。
- `LayoutEngine.layout` に `StyleSheet` を渡すと、要素のtag、ID、classに一致するルールが適用
  されます。`newStyle` は1つの `StyleSheetTarget` を受け取ります。`button.primary`、
  `.card.active.large`、`div#main` のような複合セレクターには `TargetAnd`、`h1, h2, h3` の
  ようなセレクターリストには `TargetOr` を使います。詳細度はID、class、tagの順に比較され、
  同じなら後のルール、最後に要素自身の指定済みstyleが優先されます。
- `TargetWildcard` はCSSのユニバーサルセレクター `*` です。すべての要素に一致し、詳細度には
  加算されません。`.parent > *` のように結合子内でも利用できます。
- `TargetCombinator(left, combinator, right)` では `StyleSheetCombinator` の `DESCENDANT`
  (`" "`)、`CHILD` (`">"`)、`ADJACENT_SIBLING` (`"+"`)、`GENERAL_SIBLING` (`"~"`) を利用
  できます。結合子はネストしてチェーン化でき、
  `combine`、`descendant`、`child`、`adjacentSibling`、`generalSibling` でも構築できます。
- `id` はHTML互換の要素IDです。`className` は読み取り専用の `Set<String>` で、
  `setOf("card", "active")` のように指定します。`String` も単一要素Setの省略形として指定でき、
  空白では分割されません。class名とtag名は空白を含めることができ、文字列全体が完全一致で
  照合されます。
- `horizontalAlignment` と `verticalAlignment` は子要素と文字列を縦横の各方向に配置します。
- `color`、`font`、`textShadow` は祖先から継承され、子要素で上書きできます。`null` の値は
  親を継承します。ルートではcolorが不透明な白、文字shadowはなしが既定値です。継承したshadowを
  明示的に解除するには `textShadow = UiTextShadow.NONE` を指定します。
- 段落内の改行は複数行になります。

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
        onClick = { event -> println("タイトル: button=${event.button()}") },
        onMouseMove = { x, y -> println("タイトル: x=$x, y=$y") },
        onDrag = { event ->
            println("タイトルをドラッグ中: x=${event.x()}, y=${event.y()}, button=${event.button()}")
        },
        onMouseOver = { println("タイトルにカーソルが入りました") },
        onMouseOut = { println("タイトルからカーソルが出ました") },
    )
    p("軽量な Fabric UI", UiStyle(textShadow = UiTextShadow.NONE))

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
        div(onClick = { event -> println("キャンセル: button=${event.button()}") }) {
            p("キャンセル")
        }
    }
}

val layout = LayoutEngine.layout(root, left = 12f, top = 12f)
layout.render(draw)
```

再利用するUIツリーは `UiComponent` として定義できます。`component(...)` を呼ぶたびに新しい
`UiElement` ツリーが親へ追加され、親ツリーと一緒に計測、描画、入力処理されます。親からの
文字styleの継承や、`LayoutEngine.layout` に渡したグローバルな `StyleSheet` も
コンポーネント内へ通常どおり適用されます。

```kotlin
val actionBar = uiComponent(styleSheet = actionBarStyleSheet) {
    div(UiStyle(direction = UiDirection.HORIZONTAL, gap = 4f)) {
        p("保存", className = "action")
        p("閉じる", className = "action")
    }
}

val composedLayout = LayoutEngine.layout(
    rootStyle = UiStyle(font = font),
    left = 12f,
    top = 12f,
) {
    p("エディター")
    component(actionBar)
    component(
        actionBar,
        styleSheet = compactActionBarStyleSheet,
    ) // このインスタンスだけに追加
}
```

`uiComponent` に渡したシートはコンポーネントのルートとその内部だけに適用され、呼び出し側の
祖先や兄弟をローカルセレクターから参照することはできません。ネストした子コンポーネントでは、
親コンポーネントのローカルシートは子のルートには適用できますが、その内部には入りません。
`component(...)` に渡したシートはコンポーネント既定のシートより後に追加されます。通常のCSSと
同様に詳細度が先に比較され、同じ詳細度なら後のシートが優先され、要素自身のstyleが最後に
優先されます。

コンポーネントのfactoryは `component(...)` で追加するときに1回だけ呼ばれます。同じ
`UiLayout` が `noneDisplay` の変更で再計算される場合は、既存の要素ツリーが再利用されるため、
要素のhoverやdragなどの状態も維持されます。

たとえば、次の指定では、ネストした `Div` 内も含めて段落の子孫だけにdrop shadowを適用します。
各要素では読み取り専用の `tag` を参照でき、子孫の選択に利用できます。tag名には空白を含める
ことができ、文字列全体が完全一致で照合されます。デフォルト値は `div` が `"div"`、
`p` / `paragraph` が `"p"` で、コンストラクタ引数 `tag` から指定できます。

```kotlin
val labels = div(
    tag = "section",
    style = UiStyle(font = font),
    descendantStyle = { child ->
        UiStyle(
            dropShadow = if (child.tag == "p") UiDropShadow() else null,
        )
    },
) {
    p("First")
    div {
        p("Second")
    }
}
```

CSS風のルールは要素ツリーと分離して共有できます。`newStyle` は `styles` にルールを追加するため、
初期化ブロック内で呼び出します。

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

`style` には具体的な要素を受け取る関数も指定できます。レイアウトまたは描画で使われるたびに
要素の現在の状態から解決されるため、コールバックからスタイルを書き換えなくても `hovering` や
`dragging` に応じて見た目を変えられます。

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

動的スタイルは `div` と `p` / `paragraph` で利用できます。既存レイアウトを再描画すると
継承される文字色やshadow、背景色、背景Materialなどの描画プロパティも更新されます。これらの
描画プロパティだけを変える場合は再レイアウト不要です。解決後の `style`、`descendantStyle`、
`childStyle` によってサイズ、余白、方向、配置、position、inset、フォントが変わる場合は、
レイアウトを再計算してください。
`element.style` に代入すると、動的スタイルはその静的な値で置き換えられます。

`div` と `p` / `paragraph` を含むすべての要素で `onClick`、`onMouseMove`、`onDrag`、`onMouseOver`、`onMouseOut` を利用できます。要素の `disabled` プロパティを `true` にすると、再び有効にするまで `onClick` コールバックは呼び出されません。読み取り専用の `hovering` プロパティで、カーソルが要素内にあるかを確認できます。返された `UiLayout` を保持すると、同じ GUI 座標系でヒットテストやポインター入力の通知ができます。Minecraft の `MouseButtonEvent` を `mouseClick` に渡すと、イベントの座標で最前面のクリック可能な要素を特定してドラッグ状態を開始し、そのイベントを要素の `onClick` に渡します。`mouseMove` にマウス座標を渡すと、`hovering` の更新、境界をまたいだ際のコールバック、座標上で最前面の `onMouseMove`、ドラッグ中の要素の `onDrag` が呼び出されます。`onDrag` が受け取る `MouseButtonEvent` の座標は現在のマウス座標で、ボタンと修飾キーの情報はドラッグを開始した `mouseClick` のイベントから引き継がれます。ドラッグは要素の領域外でも継続し、`mouseRelease` を呼ぶと終了します。

```kotlin
val element = layout.elementAt(mouseX.toFloat(), mouseY.toFloat())
val handled = layout.mouseClick(event)
val moveHandled = layout.mouseMove(mouseX, mouseY)
val releaseHandled = layout.mouseRelease()
```

`LayoutEngine.layout(root)` は描画せずにジオメトリを計算します。返されたレイアウトの `render(renderer)` を呼び出すと描画できます。`noneDisplay` の戻り値が変わった場合は自動的に再計算されますが、文字列、その他のスタイル、子要素を変更した後はレイアウトを再計算してください。

計算済みレイアウトの位置だけを変える場合は、`left` / `top` を変更します。すべての要素とヒットテスト領域が一緒に移動します。`layout.render(renderer, left, top)` を使うと、移動と再描画を一度に行えます。

```kotlin
layout.left = 24f
layout.top = 32f
layout.render(draw)

// 同等の短縮形
layout.render(draw, left = 24f, top = 32f)
```

`UiBoxShadow` ではARGBの `color`、有限な `offsetX` / `offsetY`、0以上の `blurRadius`、
正負どちらも使える有限な `spreadRadius`、0以上の `cornerRadius` を指定できます。spreadとblurは
描画だけを要素外へ広げ、要素のジオメトリやヒット領域を変更しません。影には現在のGUI poseと
scissorが適用されます。Layout外で単独利用する場合は、前景boxの前に
`Mine2DEngine.boxShadow(...)` を呼び出します。この組み込み効果が追従するのは矩形または
角丸矩形です。任意のalpha形状に沿う影には、カスタムshaderまたはオフスクリーンmaskを使用してください。

`UiDropShadow` ではARGBの `color`、有限な `offsetX` / `offsetY`、0以上の `blurRadius` を
指定できます。要素の背景、文字、子孫ツリー全体を一時alpha maskへ合成し、そのGaussian blurを
元の描画の背後へ1回描画します。このプロパティは継承されず、ネストにも対応し、CSSの1つの
`filter: drop-shadow(offsetX offsetY blurRadius color)`に対応します。

`UiTextShadow` ではARGBの `color`、有限な `offsetX` / `offsetY`、0以上の `blurRadius` を
指定できます。他の文字プロパティと同様に子孫へ継承され、レイアウトやヒット領域には影響しません。
継承したshadowを明示的に解除するには`UiTextShadow.NONE`を指定します。各値はCSSの1つの
`text-shadow`に対応します。正のblurは文字列をずらして何度も描くのではなく、各glyphのalphaを
Gaussian shaderでサンプリングし、glyphごとに1回で描画します。Layout外では、前景の`text(...)`の直前に
`Mine2DEngine.textShadow(...)`を呼び出します。

## カスタムシェーダー

シェーダーはPipelineとバインディングの定義、Materialはそのシェーダーへ渡す不変なUniform値とTextureの組み合わせです。クライアント初期化時にカスタムPipelineを登録します。頂点・フラグメントシェーダーの識別子は `assets/<namespace>/shaders/` からの相対位置で、拡張子 `.vsh` または `.fsh` は付けません。

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

シェーダーソースには、キーと同じ名前、型、順序のstd140ブロックを宣言します。

```glsl
layout(std140) uniform Mine2DMaterial {
    vec4 ElementBounds;
    float Radius;
    float EdgeSoftness;
};
```

Layoutの要素別背景は、独立した `UiStyle.backgroundColor` と
`UiStyle.backgroundMaterial` で指定します。背景色がnullではない場合は指定したMaterialで描画し、
Materialがnullなら描画時の `Mine2DEngine.material` を使用します。背景色がnullなら、Materialだけを
指定しても背景は描画されません。これらのプロパティは要素内だけの指定であり、子へ継承されません。
`descendantStyle` または `childStyle` の合成時にはそれぞれ独立して上書きされるため、指定済みの
背景色を維持したまま要素側でMaterialだけを変更できます。この合成ではnullは未指定を意味し、
Materialを明示的に解除することはできません。Paragraphの文字列はMinecraftのテキスト描画経路を
使うため、背景Materialの対象外です。

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

`Mine2DUniform.int`、`float`、`vec2`、`vec3`、`vec4`、`mat4` はMaterialから設定する型付きキーです。既定値のないキーはMaterial作成時に必須です。次のキーは描画ごとにエンジンが自動設定します。

- `elementBounds()`：描画領域の `(left, top, width, height)`
- `contentBounds()`：padding内側の領域。通常のポリゴン描画ではelement boundsと同じ
- `viewportSize()`：GUIの `(width, height)`
- `timeSeconds()`：単調増加する秒数

Textureを使うシェーダーは `Mine2DSampler` キーを `register(samplers = ...)` に渡し、Material作成時に `bind(key, textureView, gpuSampler)` ですべて割り当てます。Uniform、Vector、Matrix、Textureの組み合わせはMaterial作成時に検証され、描画キューには不変な値が保存されます。

Mine2D のシェーダーは、バインディング0で `DefaultVertexFormat.POSITION_COLOR`、プリミティブトポロジーに `PrimitiveTopology.TRIANGLES` を使う必要があります。`Mine2DShader.register` はこの頂点形式とトポロジーを適用し、カリングを無効にします。すでに作成済みで互換性があり、同じUniform/Samplerバインディングを宣言済みのPipelineに限り `Mine2DShader.from(...)` を使用してください。Materialバインディングを持つポリゴンはUniform値を正しく分離するため1ポリゴン1Drawになり、バインディングのない標準Materialは従来どおりバッチ化できます。

## ソースからのビルド

JDK 25 と同梱の Gradle Wrapper を使用します。

```shell
./gradlew test
./gradlew build
```

ビルド成果物は `build/libs/` に出力されます。

## ライセンス

Mine2DEngine は [MIT License](LICENSE.txt) の下で利用できます。
