package io.github.adumeige.infermats.jev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@Serializable data class Ticket(val text: String, val count: Int)
class RequestTest {
    private fun fixture(name: String) = javaClass.getResource("/$name.json")!!.readText().trimEnd()
    @Test fun minimalGolden() {
        val request = jevRequest(model = "jev-latest") {
            stateText("Help!")
            questions { noul("urgent") { instructions("Urgent?") } }
        }
        assertEquals(fixture("minimal"), request.toJsonString())
    }
    @Test fun minimalChoiceAndScore() {
        assertEquals(fixture("minimal-choice"), jevRequest("m") {
            state(JsonArray(emptyList()))
            questions { choice("c") { instructions("") } }
        }.toJsonString())
        assertEquals(fixture("minimal-score"), jevRequest("m") {
            stateText("")
            questions { score("s") { instructions(""); level("Low"); level("High") } }
        }.toJsonString())
    }
    @Test fun completeGolden() {
        val request = jevRequest("jev-latest") {
            state(Json.parseToJsonElement("""{"ticket":["Café\n😀",null,3,true]}"""))
            questions {
                choice("department") {
                    instructions(buildJsonObject { put("question", "Which team?"); put("context", buildJsonArray { add("billing"); add("tech") }) })
                    option("billing", buildJsonObject { put("includes", buildJsonArray { add("Invoices") }) })
                    option("technical")
                }
                score("priority") {
                    instructions(buildJsonArray { add("Rate urgency") })
                    level("Low"); level(buildJsonObject { put("description", "High") }); level(JsonNull)
                }
                noul("urgent") {
                    instructions(JsonNull)
                    criteria { yes(buildJsonObject { put("definition", "Immediate") }); no(JsonNull) }
                }
            }
        }
        assertEquals(fixture("complete"), request.toJsonString())
        assertEquals(Json.parseToJsonElement(fixture("complete")), request.toJsonObject())
    }
    @Test fun stringNeverParsedAndFidelity() {
        val text = "{\"café\":\"été😀\"}\n\\end"
        val r = jevRequest("m") { stateText(text); questions { noul("x") { instructions(text) } } }
        assertEquals(text, r.toJsonObject().getValue("state").jsonPrimitive.content)
        assertEquals(text, Json.parseToJsonElement(r.toJsonString()).jsonObject.getValue("questions").jsonObject.getValue("x").jsonObject.getValue("instructions").jsonPrimitive.content)
    }
    @Test fun kindsMissingAndConstructorBypass() {
        fun r(state: JsonElement?) = JevRequest("m", state, emptyMap())
        listOf(JsonPrimitive(2), JsonPrimitive(true), JsonNull).forEach {
            assertEquals("CONTENT_KIND", r(it).validate().single().code)
            assertThrows(InvalidRequestException::class.java) { r(it).toJsonObject() }
        }
        listOf(JsonPrimitive(""), JsonObject(emptyMap()), JsonArray(emptyList())).forEach { r(it).toJsonString() }
        assertEquals("MISSING_CONTENT", RequestBuilder("m").validate().single().code)
        assertThrows(InvalidRequestException::class.java) { jevRequest(" ") { stateText("") } }
        val missing = JevRequest("m", JsonPrimitive(""), mapOf("x" to NoulQuestion(null)))
        assertEquals("$.questions[\"x\"].instructions", missing.validate().single().path)
        assertThrows(InvalidRequestException::class.java) { missing.toJsonString() }
        for (q in listOf(NoulQuestion(JsonPrimitive(false)), ChoiceQuestion(JsonPrimitive(""), mapOf("x" to JsonPrimitive(3))), ScoreQuestion(JsonPrimitive(""), listOf(JsonPrimitive(1),JsonPrimitive(""))), NoulQuestion(JsonPrimitive(""), NoulCriteria(JsonPrimitive(1))))) {
            assertThrows(InvalidRequestException::class.java) { JevRequest("m", JsonPrimitive(""), mapOf("x" to q)).toJsonString() }
        }
    }
    @Test fun nestedInvalidJsonLiteralsAreRejected() {
        val request = JevRequest("m", buildJsonObject { put("n", JsonPrimitive(Double.NaN)) }, emptyMap())
        assertEquals("JSON_LITERAL", request.validate().single().code)
        assertThrows(InvalidRequestException::class.java) { request.toJsonString() }
        val valid = JevRequest("m", buildJsonArray { add(1.5); add(-2); add(true); add(JsonNull) }, emptyMap())
        valid.toJsonString()
    }
    @Test fun countBoundariesAndDuplicates() {
        fun choice(n: Int) = jevRequest("m") { stateText(""); questions { choice("x") { instructions(""); repeat(n) { option("k$it") } } } }
        choice(0); choice(255)
        assertEquals("CHOICE_SIZE", assertThrows(InvalidRequestException::class.java) { choice(256) }.errors.single().code)
        fun score(n: Int) = jevRequest("m") { stateText(""); questions { score("x") { instructions(""); repeat(n) { level("$it") } } } }
        score(2); score(10)
        listOf(0,1,11).forEach { assertThrows(InvalidRequestException::class.java) { score(it) } }
        assertEquals("QUESTION_DUPLICATE", assertThrows(InvalidRequestException::class.java) {
            jevRequest("m") { stateText(""); questions { noul("x") { instructions("") }; noul("x") { instructions("") } } }
        }.errors.single().code)
        assertEquals("OPTION_DUPLICATE", assertThrows(InvalidRequestException::class.java) {
            jevRequest("m") { stateText(""); questions { choice("x") { instructions(""); option("a"); option("a", "duplicate") } } }
        }.errors.single().code)
    }
    @Test fun criteriaOmissionAndNull() {
        val r = jevRequest("m") {
            stateText("")
            questions {
                noul("absent") { instructions("") }
                noul("empty") { instructions(""); criteria {} }
                noul("yes") { instructions(""); criteria { yes(JsonNull) } }
                noul("no") { instructions(""); criteria { no("No") } }
            }
        }.toJsonObject().getValue("questions").jsonObject
        assertFalse(r.getValue("absent").jsonObject.containsKey("criteria"))
        assertEquals(JsonObject(emptyMap()), r.getValue("empty").jsonObject.getValue("criteria"))
        assertEquals(JsonNull, r.getValue("yes").jsonObject.getValue("criteria").jsonObject.getValue("true"))
        assertFalse(r.getValue("no").jsonObject.getValue("criteria").jsonObject.containsKey("true"))
    }
    @Test fun deepSnapshotAndReusedBuilder() {
        val nested = mutableListOf<JsonElement>(JsonPrimitive("original"))
        val backing = linkedMapOf("nested" to JsonArray(nested))
        val options = linkedMapOf<String, JsonElement>("a" to JsonObject(backing))
        val q = ChoiceQuestion(JsonObject(backing), options)
        val questions = linkedMapOf<String, Question>("q" to q)
        val state = JsonObject(backing)
        val request = JevRequest("m", state, questions)
        val before = request.toJsonString()
        nested.clear(); backing.clear(); options.clear(); questions.clear()
        assertEquals(before, request.toJsonString())
        assertThrows(ClassCastException::class.java) { (request.state!!.jsonObject as MutableMap<*, *>).clear() }
        assertThrows(ClassCastException::class.java) { (request.toJsonObject() as MutableMap<*, *>).clear() }
        val b = RequestBuilder("m").apply { stateText("first"); question("a", NoulQuestion(JsonPrimitive("a"))) }
        val first = b.build()
        b.stateText("second"); b.question("b", q)
        assertEquals("first", first.state!!.jsonPrimitive.content); assertEquals(listOf("a"), first.questions.keys.toList())
        val cb = ChoiceBuilder().apply { instructions("first"); option("a") }
        val built = cb.build(); cb.instructions("second"); cb.option("b")
        assertEquals(listOf("a"), built.options.keys.toList()); assertEquals("first", built.instructions!!.jsonPrimitive.content)
    }
    @Test fun serializersAndComposition() {
        val shared = NoulQuestion(JsonPrimitive("Urgent?"))
        val r = jevRequest("jev-latest") {
            state(Ticket("Café", 2), Ticket.serializer())
            questions {
                question("shared", shared)
                for (id in listOf("a", "b")) noul(id) { instructionsValue(Ticket(id,1)) }
                choice("team") {
                    instructions(Ticket("Which?",1), Ticket.serializer())
                    options(listOf(Team.BILLING, Team.TECH), wireLabel = { when (it) { Team.BILLING -> "billing"; Team.TECH -> "technical" } })
                }
            }
        }
        assertEquals(listOf("shared","a","b","team"), r.questions.keys.toList())
        assertEquals(listOf("billing","technical"), (r.questions.getValue("team") as ChoiceQuestion).options.keys.toList())
        assertEquals(JsonPrimitive(2), r.state!!.jsonObject.getValue("count"))
        assertSame(shared, jevRequest("m") { stateValue(Ticket("",0)); question("shared",shared) }.questions.getValue("shared"))
    }
    enum class Team { BILLING, TECH }
}
