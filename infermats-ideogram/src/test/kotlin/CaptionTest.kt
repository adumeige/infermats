package io.github.adumeige.infermats.ideogram.v4

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.*

class CaptionTest {
    private fun fixture(name: String) = javaClass.getResource("/$name.json")!!.readText().trimEnd()
    @Test fun minimal() {
        assertEquals(fixture("minimal"), ideogram4 { composition("") }.toPromptString())
    }
    @Test fun minimalVariants() {
        assertEquals(fixture("minimal-photo"), ideogram4 {
            style = PhotoStyle("", "", "")
            composition("") { obj("") }
        }.toPromptString())
        assertEquals(fixture("minimal-art"), ideogram4 {
            style = ArtStyle("", "", "illustration", "")
            composition("") { text("", "") }
        }.toPromptString())
    }
    @Test fun photoGolden() {
        val reusable = PhotoStyle("warm", "sunlight", "85mm", listOf(HexColor.of("#abcdef")))
        val photo = ideogram4 {
            summary = "A café portrait"
            style = reusable
            composition("Café") {
                obj("Barista", BoundingBox(yMin = 0, xMin = 100, yMax = 1000, xMax = 900), emptyList())
            }
        }
        assertEquals(fixture("photo"), photo.toPromptString())
        assertEquals(fixture("photo"), photo.toPromptString())
    }
    @Test fun posterGoldenAndOuterRequest() {
        val poster = ideogram4 {
            style = ArtStyle("bold", "flat", "graphic_design", "vector", emptyList())
            composition("Paper") {
                text("Café \"été\"\n😀\\fin", "Title", BoundingBox(100, 0, 200, 1000), listOf(HexColor.of("#FFFFFF")))
                obj("Circle")
            }
        }
        val caption = poster.toPromptString()
        assertEquals(fixture("poster"), caption)
        val outer = buildJsonObject { put("prompt", caption) }.toString()
        assertEquals(caption, Json.parseToJsonElement(outer).jsonObject.getValue("prompt").jsonPrimitive.content)
        assertEquals("Café \"été\"\n😀\\fin", Json.parseToJsonElement(caption).jsonObject.getValue("compositional_deconstruction").jsonObject.getValue("elements").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content)
    }
    @Test fun missingCompositionHasDomainError() {
        val b = CaptionBuilder()
        assertEquals("MISSING_COMPOSITION", b.validate().single().code)
        assertEquals("$.compositional_deconstruction", assertThrows(InvalidCaptionException::class.java) { b.build() }.errors.single().path)
    }
    @Test fun boundsAndPublicConstructionCannotBypassValidation() {
        fun prompt(b: BoundingBox) = Caption(Composition("", listOf(ObjectElement("", b))))
        listOf(BoundingBox(0,0,1000,1000), BoundingBox(0,0,0,0), BoundingBox(1000,1000,1000,1000)).forEach {
            assertTrue(prompt(it).validate().isEmpty()); prompt(it).toPromptString()
        }
        listOf(BoundingBox(-1,0,1,1), BoundingBox(0,-1,1,1), BoundingBox(0,0,1001,1), BoundingBox(0,0,1,1001)).forEach {
            assertEquals("BOUND_RANGE", prompt(it).validate().single().code)
            assertThrows(InvalidCaptionException::class.java) { prompt(it).toPromptString() }
        }
        listOf(BoundingBox(2,0,1,1), BoundingBox(0,2,1,1)).forEach {
            assertEquals("BOUND_ORDER", prompt(it).validate().single().code)
        }
        assertThrows(InvalidCaptionException::class.java) { ideogram4 { composition("") { obj("", BoundingBox(1,0,0,1)) } } }
    }
    @Test fun paletteLimitsAndColorNormalization() {
        val c = HexColor.of("#aBcDeF")
        assertEquals("#ABCDEF", c.value)
        for (bad in listOf("#fff", "FFFFFF", "#GGGGGG", " #FFFFFF")) assertThrows(InvalidCaptionException::class.java) { HexColor.of(bad) }
        fun caption(n: Int, m: Int) = Caption(Composition("", listOf(ObjectElement("", palette = List(m) { c }))), style = PhotoStyle("", "", "", List(n) { c }))
        caption(16,5).toPromptString()
        assertEquals("$.style_description.color_palette", caption(17,5).validate().single().path)
        assertEquals("$.compositional_deconstruction.elements[0].color_palette", caption(16,6).validate().single().path)
        assertThrows(InvalidCaptionException::class.java) { caption(17,6).toPromptString() }
    }
    @Test fun snapshotsAndReusableComposition() {
        val colors = mutableListOf(HexColor.of("#FFFFFF"))
        val elements = mutableListOf<Element>(ObjectElement("First", palette = colors))
        val composition = Composition("", elements)
        val style = ArtStyle("", "", "illustration", "", colors)
        val first = Caption(composition, style = style)
        val before = first.toPromptString()
        colors.clear(); elements.clear()
        assertEquals(before, first.toPromptString())
        assertEquals(before, Caption(composition, style = style).toPromptString())
        assertThrows(UnsupportedOperationException::class.java) { (composition.elements as MutableList).clear() }
        val builder = CompositionBuilder("")
        builder.obj("One")
        val built = builder.build()
        builder.obj("Two"); builder.background = "new"
        assertEquals(1, built.elements.size); assertEquals("", built.background)
        val root = CaptionBuilder().apply { composition(built) }
        val previous = root.build()
        val original = previous.toPromptString()
        root.summary = "new"; root.composition("new")
        assertEquals(original, previous.toPromptString())
    }
    @Test fun ordinaryKotlinComposition() {
        val shared = ObjectElement("Sun")
        fun CompositionBuilder.clouds(names: List<String>) { for (name in names) obj(name) }
        val includeText = true
        val p = ideogram4 {
            summary = "discarded"; summary = "final"
            composition("Sky") {
                element(shared); clouds(listOf("Cloud A", "Cloud B"))
                if (includeText) text("Bonjour", "Heading")
            }
        }
        assertEquals(4, p.composition.elements.size)
        assertEquals("final", p.summary)
        assertSame(shared, ideogram4 { composition("") { element(shared) } }.composition.elements.single())
    }
}
