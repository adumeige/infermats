# Ideogram 4

Add `io.github.adumeige.infermats:infermats-ideogram:1.0.0` when released (development builds use `1.0.0-SNAPSHOT`). The package is `io.github.adumeige.infermats.ideogram.v4`. Output is the caption string; seed, dimensions and sampling settings belong to the consumer.

This complete example is compiled by the independent Maven consumer check:

```kotlin
import io.github.adumeige.infermats.ideogram.v4.*
import kotlinx.serialization.json.*

fun main() {
    val style = PhotoStyle("warm", "sunlight", "85mm", listOf(HexColor.of("#abcdef")))
    val reusable = ObjectElement("Barista", BoundingBox(yMin = 0, xMin = 100, yMax = 1000, xMax = 900))
    val photo = ideogram4 {
        summary = "A café portrait"
        this.style = style
        composition("Café") {
            element(reusable)
            for (description in listOf("Coffee cup", "Flowers")) obj(description)
        }
    }
    val poster = ideogram4 {
        this.style = ArtStyle("bold", "flat", "graphic_design", "vector")
        composition("Paper") {
            if (true) text("Café \"été\"\n😀", "Title", BoundingBox(100, 0, 200, 1000))
            element(reusable)
        }
    }
    val captionString = poster.toPromptString()
    val outerRequest = buildJsonObject { put("prompt", captionString) }.toString()
    check(Json.parseToJsonElement(outerRequest).jsonObject.getValue("prompt").jsonPrimitive.content == captionString)
    check(photo.validate().isEmpty())
}
```

`toPromptString()` is the canonical compact ordered encoder, with literal Unicode. JSON punctuation, quotes, backslashes and newlines are escaped once inside the caption. Pass the resulting string to the outer request's encoder once; do not pre-escape it or substitute a parsed caption object. There is no alternative debug serializer advertised as inference-compatible.

`PhotoStyle` and `ArtStyle` are separate variants. Photo medium is `photograph`; the art medium is supplied explicitly. `ObjectElement` and `TextElement` separate literal text from description. Palette null omits the field; an empty list emits `[]`. Summary/style null omit their fields. Empty strings and empty element lists are preserved. `HexColor.of` accepts exactly six hexadecimal digits prefixed with `#`, normalizing lowercase. Bounds use named coordinates and serialize in y,x,y,x order. Coordinates include 0 and 1000; equal minima and maxima are allowed.

Builders are scoped with `@IdeogramDsl`. Scalar assignments and composition calls replace earlier values; element calls append in order. Helper functions on `CompositionBuilder`, loops and conditionals work normally. Built values copy and protect collections; fragments and styles can be reused. Required background/element description/text are function arguments, so no fabricated placeholders are inserted.

`CaptionBuilder.validate()` and `Caption.validate()` return errors with code, JSON path and explanation. `build()` and `toPromptString()` throw `InvalidCaptionException` on invalid structure. Manually constructed captions and copied bounds are validated at export, including palette limits and coordinate range/order. Empty text does not imply invalidity; local validity does not promise image quality. See [contracts](contracts.md) for the precise field mapping, discrepancies and provenance. The Kotlin contract tests exercise both examples, exact ordering, text fidelity, boundaries, composition and snapshots.
