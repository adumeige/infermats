# Jev request bodies

Add `io.github.adumeige.infermats:infermats-jev:1.0.0` when released (development builds use `1.0.0-SNAPSHOT`). Package: `io.github.adumeige.infermats.jev`. No default model ID is selected. The model identifier is independent of the supported request shape, documented in [contracts](contracts.md); this library does not invent an upstream contract version.

This complete example compiles in the independent Maven consumer check:

```kotlin
import io.github.adumeige.infermats.jev.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class Ticket(val text: String, val attempts: Int)

fun main() {
    val request = jevRequest(model = "jev-latest") {
        stateText("Help! My invoice failed.")
        questions {
            choice("department") {
                instructions("Which team should handle this ticket?")
                option("billing", "Invoices and payment problems")
                option("technical", "Software faults and integrations")
            }
            noul("urgent") { instructions("Does the ticket require immediate attention?") }
        }
    }
    val structured = jevRequest(model = "jev-latest") {
        state(Ticket("Café 😀", 3), Ticket.serializer())
        questions {
            for (id in listOf("sender", "recipient")) noul(id) {
                instructions(buildJsonObject { put("question", "Is this field valid?"); put("field", id) })
                criteria { yes("Valid"); no(JsonNull) }
            }
            score("urgency") {
                instructionsValue(Ticket("Rate urgency", 0))
                level("Calm")
                level(buildJsonObject { put("description", "Frustrated"); put("threshold", 2) })
                level("Very angry")
            }
        }
    }
    val body: JsonObject = request.toJsonObject()
    val json: String = structured.toJsonString()
    check(body.getValue("state").jsonPrimitive.isString)
    check(Json.parseToJsonElement(json).jsonObject.getValue("state") is JsonObject)
}
```

`stateText` always means text, even for `"{...}"` strings. `state(JsonElement)` supplies structured state; `state(value, serializer)` and reified `stateValue(value)` use kotlinx.serialization, without reflection or `Any` conversion. The same text/JSON/serializer patterns apply to instructions, options, score levels and Noul sides. Top-level state accepts text/object/array; numbers, booleans and null are rejected. Nested JSON retains all data types.

All three question variants have explicitly assigned instructions. `JsonNull` is explicit content null; Kotlin null in a manually constructed question means missing and fails validation. Choice descriptions default to explicit JSON null when `option(key)` is used; they can also contain text/object/array. Score levels retain order and accept the same content kinds including explicit null. Noul criteria can be absent, empty, contain one side or both sides. In `NoulCriteria`, Kotlin null omits a side and `JsonNull` retains it. No content is converted into prose or embedded JSON strings.

Repeated scalar state/instructions/criteria assignments replace the old value. Questions/options append in insertion order; duplicate IDs/labels throw instead of overwriting. Scores append levels. `ChoiceBuilder.options(values, wireLabel, description)` requires an explicit wire-label function for domain/enum values; no ordinal or `toString()` mapping is provided. Immutable question fragments can be shared via `question(id, fragment)`. Builders and constructed values defensively copy nested collections.

`RequestBuilder.validate()`, `Question.validate()` and `JevRequest.validate()` expose nonthrowing structural checks. Throwing builders and both exports reject invalid data with `InvalidRequestException`; errors contain a model-local code, escaped JSON path and short explanation. Public constructors permit editing intermediate values but cannot bypass export validation. Local validation preserves empty collections where no minimum is documented; Choice has at most 255 options, Score has 2–10 levels. Blank model IDs are rejected as a library policy.

Output is a request body, never an OpenAI message envelope. Authentication, endpoint, HTTP execution, retries and response handling belong to the consumer. There are no network calls, telemetry, downloads or side effects. The supported contract and documented optional exclusions are recorded in [contracts](contracts.md). Model output quality is outside structural validation.
