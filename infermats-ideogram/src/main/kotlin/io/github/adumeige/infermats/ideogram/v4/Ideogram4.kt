package io.github.adumeige.infermats.ideogram.v4

import java.util.Collections
import kotlinx.serialization.json.*

/** Isolates nested Ideogram builder receivers. */
@DslMarker
public annotation class IdeogramDsl

/** A local structural problem; paths refer to the caption, never to an API envelope. */
public data class CaptionError(val code: String, val path: String, val message: String)

public class InvalidCaptionException(errors: List<CaptionError>) : IllegalArgumentException(
    errors.joinToString("; ") { "${it.code} at ${it.path}: ${it.message}" }
) {
    public val errors: List<CaptionError> = snapshot(errors)
}

/** Six-digit RGB; [of] accepts lowercase and normalizes it, with no shorthand or trimming. */
public class HexColor private constructor(public val value: String) {
    public companion object {
        public fun of(value: String): HexColor {
            if (!Regex("#[0-9a-fA-F]{6}").matches(value)) {
                throw InvalidCaptionException(listOf(CaptionError("COLOR_FORMAT", "$", "Expected a six-digit #RRGGBB color")))
            }
            return HexColor(value.uppercase(java.util.Locale.ROOT))
        }
    }
    override fun equals(other: Any?): Boolean = other is HexColor && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value
}

/** Named coordinates; wire order is yMin, xMin, yMax, xMax. Zero-area boxes are valid. */
public data class BoundingBox(val yMin: Int, val xMin: Int, val yMax: Int, val xMax: Int)

public sealed class Style(palette: List<HexColor>?) {
    public val palette: List<HexColor>? = palette?.let(::snapshot)
    public abstract val aesthetics: String
    public abstract val lighting: String
}

/** The photographic variant always emits medium=photograph. */
public class PhotoStyle(
    override val aesthetics: String,
    override val lighting: String,
    public val photo: String,
    palette: List<HexColor>? = null
) : Style(palette)

public class ArtStyle(
    override val aesthetics: String,
    override val lighting: String,
    public val medium: String,
    public val artStyle: String,
    palette: List<HexColor>? = null
) : Style(palette)

public sealed class Element(
    public val description: String,
    public val bounds: BoundingBox?,
    palette: List<HexColor>?
) {
    public val palette: List<HexColor>? = palette?.let(::snapshot)
}

public class ObjectElement(description: String, bounds: BoundingBox? = null, palette: List<HexColor>? = null) :
    Element(description, bounds, palette)

public class TextElement(public val text: String, description: String, bounds: BoundingBox? = null, palette: List<HexColor>? = null) :
    Element(description, bounds, palette)

public class Composition(public val background: String, elements: List<Element>) {
    public val elements: List<Element> = snapshot(elements)
}

/** Immutable caption. Public construction is supported; every wire export validates it. */
public class Caption(
    public val composition: Composition,
    public val summary: String? = null,
    public val style: Style? = null
) {
    public fun validate(): List<CaptionError> {
        val errors = mutableListOf<CaptionError>()
        fun palette(colors: List<HexColor>?, max: Int, path: String) {
            if (colors != null && colors.size > max) errors += CaptionError("PALETTE_SIZE", path, "Expected at most $max colors")
        }
        palette(style?.palette, 16, "$.style_description.color_palette")
        composition.elements.forEachIndexed { index, element ->
            val path = "$.compositional_deconstruction.elements[$index]"
            palette(element.palette, 5, "$path.color_palette")
            element.bounds?.let { b ->
                if (listOf(b.yMin, b.xMin, b.yMax, b.xMax).any { it !in 0..1000 })
                    errors += CaptionError("BOUND_RANGE", "$path.bbox", "Coordinates must be between 0 and 1000 inclusive")
                if (b.yMin > b.yMax || b.xMin > b.xMax)
                    errors += CaptionError("BOUND_ORDER", "$path.bbox", "Minimum coordinates must not exceed maxima")
            }
        }
        return snapshot(errors)
    }

    /** Canonical compact, ordered JSON with literal Unicode, suitable as the model prompt string. */
    public fun toPromptString(): String {
        requireValid(validate())
        return buildJsonObject {
            summary?.let { put("high_level_description", it) }
            style?.let { s -> put("style_description", buildJsonObject {
                put("aesthetics", s.aesthetics)
                put("lighting", s.lighting)
                when (s) {
                    is PhotoStyle -> { put("photo", s.photo); put("medium", "photograph") }
                    is ArtStyle -> { put("medium", s.medium); put("art_style", s.artStyle) }
                }
                s.palette?.let { put("color_palette", colors(it)) }
            }) }
            put("compositional_deconstruction", buildJsonObject {
                put("background", composition.background)
                put("elements", JsonArray(composition.elements.map { e -> buildJsonObject {
                    put("type", if (e is TextElement) "text" else "obj")
                    e.bounds?.let { put("bbox", JsonArray(listOf(it.yMin, it.xMin, it.yMax, it.xMax).map(::JsonPrimitive))) }
                    if (e is TextElement) put("text", e.text)
                    put("desc", e.description)
                    e.palette?.let { put("color_palette", colors(it)) }
                } }))
            })
        }.toString()
    }
}

@IdeogramDsl
public class CaptionBuilder {
    /** Repeated scalar assignments replace previous values. */
    public var summary: String? = null
    public var style: Style? = null
    private var compositionValue: Composition? = null
    public fun composition(background: String, block: CompositionBuilder.() -> Unit = {}) {
        compositionValue = CompositionBuilder(background).apply(block).build()
    }
    public fun composition(value: Composition) { compositionValue = value }
    public fun validate(): List<CaptionError> = compositionValue?.let { Caption(it, summary, style).validate() }
        ?: listOf(CaptionError("MISSING_COMPOSITION", "$.compositional_deconstruction", "Supply a composition with background and elements"))
    public fun build(): Caption {
        requireValid(validate())
        return Caption(checkNotNull(compositionValue), summary, style)
    }
}

@IdeogramDsl
public class CompositionBuilder(public var background: String) {
    private val elements = mutableListOf<Element>()
    /** Collection calls append, preserving order. Immutable elements may be reused. */
    public fun element(value: Element) { elements += value }
    public fun obj(description: String, bounds: BoundingBox? = null, palette: List<HexColor>? = null) {
        element(ObjectElement(description, bounds, palette))
    }
    public fun text(text: String, description: String, bounds: BoundingBox? = null, palette: List<HexColor>? = null) {
        element(TextElement(text, description, bounds, palette))
    }
    public fun build(): Composition = Composition(background, elements).also { requireValid(Caption(it).validate()) }
}

public fun ideogram4(block: CaptionBuilder.() -> Unit): Caption = CaptionBuilder().apply(block).build()

private fun colors(values: List<HexColor>): JsonArray = JsonArray(values.map { JsonPrimitive(it.value) })
private fun <T> snapshot(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun requireValid(errors: List<CaptionError>) { if (errors.isNotEmpty()) throw InvalidCaptionException(errors) }
