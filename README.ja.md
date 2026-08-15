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
| `roundedRect(x, y, width, height, ..., color)` | 単一の円形半径、または四隅ごとの楕円半径を持つ塗りつぶした角丸矩形を描画します。                                      |
| `line(startX, startY, endX, endY, width, color)` | 端が平らな塗りつぶし線を描画します。                                                                                   |
| `circle(centerX, centerY, radius, color, segments)` | 正多角形で近似した塗りつぶし円を描画します。segmentsを増やすほど輪郭が滑らかになります。                               |
| `boxShadow(x, y, width, height, ...)` | 前景のbox自体を描かず、柔らかい角丸box shadowを描画します。                                                           |
| `textShadow(font, text, x, y, ...)` | 前景文字を描かず、設定可能なglyph shadowを描画します。                                                                |
| `text(font, text, x, y, color)` | 読み込み済みの `Mine2DFont` で文字列を描画します。                                                                                 |
| `withMaterial(material) { ... }` | ブロック内だけ既定のポリゴンMaterialを変更し、終了後に元へ戻します。                                                |
| `withRoundedClip(x, y, width, height, ...) { ... }` | textを含むブロック内のすべてのdeferred GUI描画を、座標変換された角丸矩形でclipします。 |

ポリゴンの各点は時計回り、反時計回りのどちらでも指定できます。3 個以上の異なる点と 0 ではない面積が必要で、自己交差はできません。連続する重複点と不要な同一直線上の点は自動的に取り除かれます。線には異なる始点・終点と正の幅、円には正の半径と 3 以上の分割数が必要です。角丸矩形は曲率に応じて自動的に分割され、重なり合う半径はCSSと同じ共通係数で縮小されます。四隅を個別指定する場合は `Mine2DRoundedRectRadii` と `Mine2DCornerRadius` を使用します。

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

レイアウトパッケージはcontainer、paragraph、型付きinput controlの要素ツリーへstyleをcascadeし、CSS box treeを生成してbox fragmentへ配置します。従来の独自stack layoutではなく、CSSのプロパティ名と初期値を使います。

### 対応するCSS layout profile

- `display: block | inline | flow-root | flex | inline-flex | none | contents`
- block flow、inline textのline box、normal/pre/nowrapの空白処理、`text-align`
- content-box / border-box、px / percentage、intrinsic size keyword、min/max制約
- 物理margin / padding、横方向の`auto` margin、隣接block marginのcollapse
- `position: static | relative | absolute`、length / percentage inset、両側inset間の自動stretch
- Flexboxのrow / column、reverse、wrap、grow / shrink / basis、order、gap、auto margin、justify / align
- replaced elementとしての単一行`TextInput`、数値slider `RangeInput`、不透明RGB `ColorInput`
- `::before` / `::after` の生成box
- `display: none`によるsubtree除外と、`display: contents`によるprincipal box除外

Grid、table、float、ruby、縦書き、fragmentation、fixed / sticky positioning、border、および組み込みinput control以外のreplaced elementは対象外です。

### Initial containing block

CSS layoutでは利用可能な幅と高さが必要なため、すべてのlayout呼び出しへviewport矩形を渡します。

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

`layout.viewport`から現在のinitial containing blockを取得できます。利用可能なGUI領域が変わった場合は、`UiLayout`インスタンスを置き換えずに更新できます。

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

`updateViewport`は同期処理です。現在と同じ矩形なら何もせず、原点だけが変わった場合は全geometryを平行移動し、幅または高さが変わった場合はCSS layout全体を再計算します。計算に失敗した場合は以前のviewportとgeometryを維持します。取得済みの`UiLayoutNode`と`UiBoxFragment`はsnapshotなので、更新後は`root`、`nodeOf`、`rootFragment`、`fragmentsOf`から取得し直してください。

`width: auto`のblock boxは利用可能なinline sizeを埋め、`height: auto`は通常フロー内の内容に合わせます。そのため、style未指定のルート`div`は子へ縮むのではなく、通常はviewport幅になります。

`div`と`p`には組み込みUA style層から`display: block`が与えられます。他のtagはCSSのdisplay初期値である`inline`です。author stylesheetはUA層を上書きし、要素へ直接指定したstyleが最優先です。

### CSS値とbox model

`UiStyle`のnullは「未宣言」を表し、CSSの`auto`ではありません。`auto`、`none`、`content`などは明示的な値なので、後段のruleからCSS初期値へ戻せます。

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

val style = UiStyle(
    width = 50f.percent,
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

`Float.px`と`Float.percent`でlength-percentageを作ります。負のlengthはmarginとinsetでは利用できますが、size、padding、gapでは拒否されます。CSSと同様に、padding percentageと物理margin percentageは包含blockの幅を基準にします。

`border`には物理top/right/bottom/leftを持つ`UiBorders`を指定します。各`UiBorderSide`で
`NONE`と`SOLID`を利用でき、幅は0以上のpixel lengthです。色がnullなら要素のcomputed
`color`（`currentColor`）を使い、`NONE`のused widthは0になります。cascadeでは1つの
`UiBorders`値をatomicな宣言として扱い、`UiBorders.NONE`で明示的にリセットできます。
borderはintrinsic size、flex、positioned layout、`box-sizing`の計算に含まれます。

`borderRadius`はレイアウト寸法には影響しません。角半径の水平方向percentageはborder boxの幅、垂直方向percentageは高さを基準にします。overflowの両軸がclipする場合は、外側の半径から隣接するborder幅を引いたpadding-edge半径がclipに適用されます。box shadowは既定で外側のborder radiusを使用します。従来の単一半径を使う場合は `UiBoxShadow(cornerRadius = ..., followBorderRadius = false)` を指定します。0より大きい `cornerRadius` では `followBorderRadius` の既定値がfalseになります。

preferred / minimum / maximum sizeでは`AUTO`、`MIN_CONTENT`、`MAX_CONTENT`、`FitContent(...)`、length-percentageを利用できます。maximum sizeでは`NONE`も利用できます。

### Overflow

`overflow`、`overflowX`、`overflowY`では`VISIBLE`、`HIDDEN`、`CLIP`、`SCROLL`、`AUTO`を利用できます。

```kotlin
import io.github.aiwao.mine2dengine.layout.UiOverflow
import io.github.aiwao.mine2dengine.layout.UiOverflowValue

val scrollerStyle = UiStyle(
    width = 160f.px,
    height = 80f.px,
    overflow = UiOverflow(UiOverflowValue.AUTO),
)

// 2値のshorthandは物理x軸、y軸の順です。
val verticalScrollerStyle = UiStyle(
    overflow = UiOverflow(
        x = UiOverflowValue.CLIP,
        y = UiOverflowValue.AUTO,
    ),
)
```

同じ`UiStyle`内では`overflowX`と`overflowY`がshorthandの対応軸を上書きします。cascadeで後からshorthandが指定された場合は、それ以前のlonghandをリセットします。CSSと同様に、もう一方の軸が`hidden`、`scroll`、`auto`なら`visible`のcomputed valueは`auto`になり、`clip`は非スクロールのままです。

`hidden`はwheel入力を受け付けませんが、`UiLayout.scrollTo`と`scrollBy`からスクロールできます。`clip`はユーザー操作とプログラム操作の両方を禁止します。`auto`と`scroll`はwheel入力を受け付け、内側が端へ到達すると外側のscroll containerへ連鎖します。scroll offsetはrelayout後も維持され、新しいoverflow geometryへclampされます。現在値は`scrollOffsetOf`から取得でき、`UiLayoutNode`では`paddingBounds`、`scrollableOverflowBounds`、`maximumScrollX`、`maximumScrollY`を参照できます。

overflowはpadding boxでclipし、scrollbarは現在描画しません。両軸がclipする場合は`borderRadius`がclipとpointer hit testの形状にも適用されます。片軸だけがclipする場合は矩形のままです。

### Flexbox

`UiDisplay.FLEX`または`UiDisplay.INLINE_FLEX`でinner display typeをflexにします。

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

container propertyは`flexDirection`、`flexWrap`、`justifyContent`、`alignItems`、`alignContent`、`rowGap`、`columnGap`です。item propertyは`flexGrow`、`flexShrink`、`flexBasis`、`order`、`alignSelf`です。

flexible lengthは各itemのflex base sizeから、scaled shrink factorとmin/max clampの反復処理で解決します。CSSのautomatic minimum sizeはnon-scrollable overflowではcontent-based、main axisがscrollableなら0です。non-scrollable itemをcontentより小さく縮めたい場合は`minWidth = 0f.px`を指定してください。columnの場合は`minHeight`です。

absolute childはflex itemになりません。生成された疑似要素はflex itemになります。flex container直下のtextはanonymous flex itemで囲まれます。

### Positioning

insetには`UiInsetValue.AUTO`またはlength-percentageを使います。

```kotlin
val badgeStyle = UiStyle(
    position = UiPosition.ABSOLUTE,
    top = 6f.px,
    right = 8f.px,
    width = 20f.px,
)
```

relative boxは通常フローの領域を保ったままvisual offsetを受けます。absolute boxはフローから外れ、最も近いpositioned ancestorのpadding boxを使います。なければinitial containing blockを使います。同じ軸の両insetがdefiniteで対応するsizeが`auto`なら、そのinset間までstretchします。

### Cascade、component、generated content

既存のtag、ID、class、scope、compound、selector list、combinator targetは、引き続きCSS specificityとsource orderへ参加します。container scoped sheetとcomponent sheetの境界も維持されます。

pseudo-element ruleでは`UiPseudoStyle`と`UiGeneratedContent.Text`または`UiGeneratedContent.EmptyBox`を使います。生成boxのdisplay、sizing、positioning、flex item propertyも通常boxと同様に解決されます。

`color`、`font`、`textShadow`、`textAlign`、`whiteSpace`は継承されます。backgroundやbox / drop shadowなどのpaint propertyは継承されません。

### Text input

`input()`はHTMLの`input type="text"`に対応する単一行の`TextInput`を作ります。値、caret、選択範囲、横スクロール、clipboard shortcut、IME preeditを独自に管理しており、Minecraftの`EditBox`は使用しません。

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

`onInput`はユーザー操作で確定値が変わるたびに呼ばれ、`onChange`は編集後にfocusを失った時に呼ばれます。`value`へのプログラムからの代入ではどちらも呼ばれません。`readOnly`ではfocus、選択、copyは可能ですが編集できず、`disabled`ではfocusできません。

CSSの`width:auto`では`size`個の`0` glyph相当がintrinsic widthとなり、`height:auto`ではfontのline heightが使われます。`value`、placeholder、caret、選択の変更はgeometryを変えないため`relayout()`は不要です。`size`やlayout propertyを変更した場合は`relayout()`を呼んでください。

input controlを含むlayoutは、`Screen.init()`でそのままrenderable widgetとして登録できます。登録すると描画に加え、mouse、keyboard、character、Tab focus、IME preeditがMinecraftから自動配送されます。

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

widgetとして登録したlayoutを別途`layout.render(...)`で描画しないでください。手動配送もできますが、IMEを有効化してpreeditを正しく受け取るにはScreenへの登録が推奨です。focusは`layout.focus(playerName)`、`layout.clearFocus()`、`layout.focusedElement`から制御・確認できます。

すべてのinput controlは`onKeyPressed`で、focus中に配送された`KeyEvent`を監視できます。callbackはcontrol固有の標準key処理の後に呼ばれ、eventの消費結果や標準処理には影響しません。たとえばText inputのEnterは標準処理では消費されないため、`onKeyPressed`を設定しても`UiLayout.keyPressed()`の戻り値は`false`のままです。確定文字の値変更は引き続き`charTyped`経由で処理されるため、`onInput`で監視してください。

### Range input

`rangeInput<T>()`はHTMLの`input type="range"`に対応する型付き数値sliderを作ります。`T`には`Int`、`Float`、`Double`を指定でき、`value`、`min`、`max`、`step`とcallbackの値は同じ型を保ちます。値は常に`min`と`max`の範囲へclampされ、`step`が指定されている場合は`min`を基準に最も近いstepへ揃えられます。同距離のstepが2つある場合は大きい値が選ばれます。

```kotlin
lateinit var volume: RangeInput<Float>

val root = div {
    volume = rangeInput<Float>(
        value = 0.5f,
        min = 0f,
        max = 1f,
        step = 0.05f,
        label = "Volume",
        valueText = { value -> "${(value * 100).toInt()} percent" },
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

型引数は`rangeInput<Int>()`のように明示できるほか、`rangeInput(value = 3, min = 0, max = 10)`のように数値引数から推論されます。型も数値引数も指定しない`rangeInput()`は後方互換のため`RangeInput<Double>`になります。明示的な型tokenが必要な箇所では`rangeInput(RangeNumberTypes.INT, ...)`も使用できます。

`value`を省略または`null`にすると、`min`と`max`の中間をstepへ揃えた値で初期化されます。各型のデフォルトは数値として`min = 0`、`max = 100`、`step = 1`です。`step = null`はstep整列を無効にし、keyboardでは範囲の1/100ずつ変化します。ただし`Int`ではpointer・keyboardとも結果は最も近い整数になり、keyboardの最小変化量は1です。`min`、`max`、`step`、`value`へのプログラムからの代入は値を再正規化しますが、callbackを呼びません。`Float` / `Double`の非有限値、`min > max`、0以下のstepは拒否されます。

trackのclickとdrag中は、step整列後の値が実際に変わるたびに`onInput`を呼び、mouse releaseまたはfocus喪失時に`onChange`を一度呼びます。keyboardの各操作は単独で確定され、変更時に`onInput`と`onChange`を呼びます。左右または上下矢印で1 step、Page Up/Downで10 steps、Home/Endで最小／最大の許容値へ移動します。wheelはsliderでは消費せず、通常のscroll containerへ配送されます。

`orientation = RangeOrientation.VERTICAL`では最小値が下、最大値が上になります。デフォルトのcontent sizeはhorizontalで`100 × 20`、verticalで`20 × 100`です。orientation変更後は`relayout()`が必要ですが、値や範囲の変更では不要です。track、active track、thumb、focus ringの色はそれぞれ`trackColor`、`activeTrackColor`、`thumbColor`、`focusColor`から変更できます。`RangeInput`はfocusとTab順へ参加しますが、platform text input / IMEは有効化しません。

### Color input

`colorInput()`はHTMLの`input type="color"`に対応する`ColorInput`を作ります。値はMine2DEngine共通の`0xAARRGGBB`整数ですが、常に不透明な`0xFFRRGGBB`へ正規化されるため、透明色を代入するとalphaは破棄されます。

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

swatchをクリックするとHSV picker overlayが開きます。saturation/value領域またはhue stripのdrag中は`onInput`を呼び、編集したpickerの確定または外側clickによる終了時に`onChange`を一度呼びます。プログラムからの`value`代入ではどちらも呼びません。EnterまたはSpaceで開く／確定、Escapeでpickerを開いた時点の色へ復元、矢印keyでsaturation/value、Page Up/Downでhueを調整します。Shiftを押すとkeyboard操作のstepが大きくなります。

デフォルトの`width:auto`と`height:auto`のcontent sizeはGUI座標で`36 × 20`で、このintrinsic metrics自体はfontに依存しません。ただしinline formatting contextのline boxには親のfontが必要になる場合があります。`ColorInput`はほかのinput controlと同じfocus・Tab順へ参加しますが、platform text input / IMEは有効化しません。すべてのinput controlはデフォルトtagが`input`なので、型ごとに異なるstylesheet ruleが必要な場合はclassまたはIDを指定してください。

### 結果、描画、入力

`UiLayout.root`と`nodeOf(element)`は要素単位の互換viewです。`UiLayout.rootFragment`はanonymous boxと疑似要素を含むCSS fragment treeを公開し、`fragmentsOf(element)`はその要素が実際に生成したboxだけを返します。`display: none`ではfragmentを返しません。`display: contents`ではprincipal fragmentを返しませんが、生成された疑似要素自身のfragmentは返る場合があります。

```kotlin
val node = layout.nodeOf(button)
val fragments = layout.fragmentsOf(button)

layout.render(draw)
val hit = layout.elementAt(mouseX, mouseY)
```

描画、shadow、hit test、hover、click、drag dispatchは最終的なCSS geometryを使います。計算済みlayoutの`left`または`top`だけを変更すると、node、fragment、hit regionが一緒に移動します。

viewport更新ではpointer callbackを発火しません。完全な再レイアウト後もboxを生成する要素のhoverとdrag状態は維持し、削除された要素の状態は解除します。次の`mouseMove`で新しいgeometryに対してhoverを同期します。

dynamic style providerが`UiDisplay.NONE`へ切り替わる、またはそこから戻る変化は、描画とpointer操作の前に検査され、geometryが自動再計算されます。それ以外のlayout property、text、stylesheet内容、childを変更した場合は`layout.relayout()`を呼び、現在のviewportでgeometryを再構築してください。

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
StyleSheetの合成時にはそれぞれ独立して上書きされるため、指定済みの背景色を維持したまま後の
declarationでMaterialだけを変更できます。この合成ではnullは未指定を意味し、
Materialを明示的に解除することはできません。Paragraphの文字列はMinecraftのテキスト描画経路を
使うため、背景Materialの対象外です。

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
