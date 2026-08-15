package io.github.aiwao.mine2dengine.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo

class UiComponentHooksTest {
    private class InheritedCounter : UiComponent<Div>() {
        lateinit var setter: StateSetter<Int>
        var renders: Int = 0

        override fun ComponentScope.render(content: UiContent): Div {
            val (count, setCount) = useState { 0 }
            setter = setCount
            renders += 1
            return div {
                p(count.toString())
                content()
            }
        }
    }

    private class InheritedStyledComponent(
        override val styleSheets: List<StyleSheet>,
    ) : UiComponent<Div>() {
        lateinit var paragraph: Paragraph

        override fun ComponentScope.render(content: UiContent): Div = div {
            paragraph = p("styled")
            content()
        }
    }

    private val measurer = object : UiTextMeasurer {
        override val lineHeight: Float = 10f

        override fun width(text: String): Float = text.length * 5f
    }

    private fun layout(root: UiElement): UiLayout = calculateLayout(
        root = root,
        viewport = UiRect(0f, 0f, 200f, 100f),
        textMeasurer = measurer,
    )

    @Test
    fun `state updates are batched and retain host identity`() {
        var initializerCalls = 0
        var renders = 0
        lateinit var setter: StateSetter<Int>
        val counter = uiComponent {
            val state = useState {
                initializerCalls += 1
                0
            }
            setter = state.setter
            renders += 1
            div { p("Count: ${state.value}") }
        }
        val mounted = div { component(counter) }
        val componentRoot = mounted.children.single() as Div
        val paragraph = componentRoot.children.single() as Paragraph
        val result = layout(mounted)

        setter.update { it + 1 }
        setter.update { it + 1 }
        assertEquals("Count: 0", paragraph.text)

        result.flushUpdates()

        assertEquals(1, initializerCalls)
        assertEquals(2, renders)
        assertSame(componentRoot, mounted.children.single())
        assertSame(paragraph, componentRoot.children.single())
        assertEquals("Count: 2", paragraph.text)
    }

    @Test
    fun `separate mounts own separate state`() {
        val setters = mutableListOf<StateSetter<Int>>()
        val counter = uiComponent {
            val (count, setCount) = useState { 0 }
            setters += setCount
            div { p(count.toString()) }
        }
        val root = div {
            component(counter)
            component(counter)
        }
        val result = layout(root)

        setters.first()(7)
        result.flushUpdates()

        assertEquals("7", ((root.children[0] as Div).children.single() as Paragraph).text)
        assertEquals("0", ((root.children[1] as Div).children.single() as Paragraph).text)
    }

    @Test
    fun `changed hook count fails without committing the rendered tree`() {
        lateinit var setEnabled: StateSetter<Boolean>
        val component = uiComponent {
            val (enabled, setter) = useState { false }
            setEnabled = setter
            if (enabled) useState { "conditional" }
            div { p(enabled.toString()) }
        }
        val root = div { component(component) }
        val mountedParagraph = ((root.children.single() as Div).children.single() as Paragraph)
        val result = layout(root)

        setEnabled(true)

        assertFailsWith<IllegalStateException> { result.flushUpdates() }
        assertEquals("false", mountedParagraph.text)
    }

    @Test
    fun `unmounted setter is a no-op`() {
        lateinit var setVisible: StateSetter<Boolean>
        lateinit var childSetter: StateSetter<Int>
        var childRenders = 0
        val child = uiComponent {
            val (_, setter) = useState { 0 }
            childSetter = setter
            childRenders += 1
            div()
        }
        val parent = uiComponent {
            val (visible, setter) = useState { true }
            setVisible = setter
            div {
                if (visible) component(child, key = "child")
            }
        }
        val root = div { component(parent) }
        val result = layout(root)

        setVisible(false)
        result.flushUpdates()
        childSetter.update { it + 1 }
        result.flushUpdates()

        assertEquals(1, childRenders)
    }

    @Test
    fun `retained uncontrolled text input keeps editor state`() {
        lateinit var rerender: StateSetter<Int>
        lateinit var renderedInput: TextInput
        val component = uiComponent {
            val (_, setter) = useState { 0 }
            rerender = setter
            div {
                renderedInput = input(defaultValue = "initial")
            }
        }
        val root = div { component(component) }
        val retained = renderedInput
        val result = layout(root)
        retained.value = "edited"
        retained.setSelectionRange(1, 3)

        rerender.update { it + 1 }
        result.flushUpdates()

        assertSame(retained, ((root.children.single() as Div).children.single() as TextInput))
        assertEquals("edited", retained.value)
        assertEquals(1, retained.selectionStart)
        assertEquals(3, retained.selectionEnd)
    }

    @Test
    fun `controlled text input follows state without firing input callback`() {
        lateinit var setValue: StateSetter<String>
        var inputEvents = 0
        lateinit var renderedInput: TextInput
        val component = uiComponent {
            val (value, setter) = useState { "first" }
            setValue = setter
            div {
                renderedInput = input(
                    value = value,
                    onInput = { inputEvents += 1 },
                )
            }
        }
        val root = div { component(component) }
        val retained = renderedInput
        val result = layout(root)

        setValue("second")
        result.flushUpdates()

        assertSame(retained, ((root.children.single() as Div).children.single() as TextInput))
        assertEquals("second", retained.value)
        assertEquals(0, inputEvents)
    }

    @Test
    fun `event callback updates render only once at dispatch end`() {
        var renders = 0
        val component = uiComponent {
            val (count, setter) = useState { 0 }
            renders += 1
            div(
                style = UiStyle(width = 20f.px, height = 20f.px),
                onClick = {
                    setter.update { it + 1 }
                    setter.update { it + 1 }
                    assertEquals(1, renders)
                },
            ) {
                p(count.toString())
            }
        }
        val root = div { component(component) }
        val result = layout(root)

        result.mouseClick(MouseButtonEvent(5.0, 5.0, MouseButtonInfo(0, 0)))

        assertEquals(2, renders)
        assertEquals("2", ((root.children.single() as Div).children.single() as Paragraph).text)
    }

    @Test
    fun `keyed child reorder retains state with its key`() {
        lateinit var reorder: StateSetter<List<String>>
        val childSetters = mutableListOf<StateSetter<Int>>()
        val child = uiComponent {
            val (value, setter) = useState { 0 }
            childSetters += setter
            div { p(value.toString()) }
        }
        val parent = uiComponent {
            val (keys, setter) = useState { listOf("a", "b") }
            reorder = setter
            div {
                keys.forEach { key -> component(child, key = key) }
            }
        }
        val root = div { component(parent) }
        val result = layout(root)

        childSetters.first()(9)
        reorder(listOf("b", "a"))
        result.flushUpdates()

        val children = (root.children.single() as Div).children.map { it as Div }
        assertEquals(listOf("b", "a"), children.map(UiElement::key))
        assertEquals("0", (children[0].children.single() as Paragraph).text)
        assertEquals("9", (children[1].children.single() as Paragraph).text)
    }

    @Test
    fun `key change remounts child and resets state`() {
        lateinit var changeKey: StateSetter<String>
        val oldSetters = mutableListOf<StateSetter<Int>>()
        var initializers = 0
        val child = uiComponent {
            val (_, setter) = useState {
                initializers += 1
                0
            }
            oldSetters += setter
            div()
        }
        val parent = uiComponent {
            val (key, setter) = useState { "first" }
            changeKey = setter
            div { component(child, key = key) }
        }
        val root = div { component(parent) }
        val result = layout(root)
        val oldSetter = oldSetters.single()

        changeKey("second")
        result.flushUpdates()
        oldSetter(10)
        result.flushUpdates()

        assertEquals(2, initializers)
    }

    @Test
    fun `failed layout rolls element patches back`() {
        lateinit var setter: StateSetter<Boolean>
        val component = uiComponent {
            val (fail, setFail) = useState { false }
            setter = setFail
            div(style = {
                check(!fail) { "layout failed" }
                UiStyle()
            }) {
                p(fail.toString())
            }
        }
        val root = div { component(component) }
        val componentRoot = root.children.single() as Div
        val paragraph = componentRoot.children.single() as Paragraph
        val result = layout(root)
        val previousSnapshot = result.root

        setter(true)

        assertFailsWith<IllegalStateException> { result.flushUpdates() }
        assertSame(previousSnapshot, result.root)
        assertSame(componentRoot, root.children.single())
        assertEquals("false", paragraph.text)
    }

    @Test
    fun `UiComponent subclass uses hooks through the same render API`() {
        val counter = InheritedCounter()
        val root = div {
            component(counter) {
                p("content")
            }
        }
        val mountedRoot = root.children.single() as Div
        val result = layout(root)

        counter.setter.update { it + 1 }
        result.flushUpdates()

        assertEquals(2, counter.renders)
        assertSame(mountedRoot, root.children.single())
        assertEquals(
            listOf("1", "content"),
            mountedRoot.children.map { (it as Paragraph).text },
        )
    }

    @Test
    fun `UiComponent subclass overrides component style sheets`() {
        val sheet = object : StyleSheet {
            override val styles = mutableListOf<StyleSheetObject>()
        }.apply {
            newStyle(TargetTag("p"), UiStyle(width = 37f.px))
        }
        val component = InheritedStyledComponent(listOf(sheet))
        val root = div { component(component) }

        val result = layout(root)

        assertEquals(37f, result.nodeOf(component.paragraph)!!.contentBounds.width)
    }

    @Test
    fun `parent render updates nested component content while retaining host identity`() {
        val card = uiComponent { content ->
            div { content() }
        }
        lateinit var setLabel: StateSetter<String>
        val parent = uiComponent {
            val (label, setter) = useState { "before" }
            setLabel = setter
            div {
                component(card) {
                    p(label)
                }
            }
        }
        val root = div { component(parent) }
        val parentRoot = root.children.single() as Div
        val cardRoot = parentRoot.children.single() as Div
        val paragraph = cardRoot.children.single() as Paragraph
        val result = layout(root)

        setLabel("after")
        result.flushUpdates()

        assertSame(cardRoot, parentRoot.children.single())
        assertSame(paragraph, cardRoot.children.single())
        assertEquals("after", paragraph.text)
    }
}
