package io.github.adumeige.infermats.jev

import java.util.Collections
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.*

@DslMarker
public annotation class JevDsl

public data class RequestError(val code: String, val path: String, val message: String)

public class InvalidRequestException(errors: List<RequestError>) : IllegalArgumentException(
    errors.joinToString("; ") { "${it.code} at ${it.path}: ${it.message}" }
) {
    public val errors: List<RequestError> = immutableList(errors)
}

/** Missing instructions are distinct from an explicit JSON null. */
public sealed class Question(instructions: JsonElement?) {
    public val instructions: JsonElement? = instructions?.let(::freeze)
    public fun validate(): List<RequestError> = validateQuestion(this, "$")
}

public class ChoiceQuestion(instructions: JsonElement?, options: Map<String, JsonElement>) : Question(instructions) {
    public val options: Map<String, JsonElement> = immutableMap(options.mapValues { freeze(it.value) })
}

public class ScoreQuestion(instructions: JsonElement?, levels: List<JsonElement>) : Question(instructions) {
    public val levels: List<JsonElement> = immutableList(levels.map(::freeze))
}

/** Kotlin null omits a side; JsonNull explicitly describes it as null. */
public class NoulCriteria(yes: JsonElement? = null, no: JsonElement? = null) {
    public val yes: JsonElement? = yes?.let(::freeze)
    public val no: JsonElement? = no?.let(::freeze)
    public fun validate(): List<RequestError> = immutableList(listOfNotNull(yes?.let { "$.true" to it }, no?.let { "$.false" to it }).flatMap { (path, value) ->
        if (!validContent(value, true)) listOf(RequestError("CONTENT_KIND", path, "Expected text, object, array or explicit null")) else validateJson(value, path)
    })
}

public class NoulQuestion(instructions: JsonElement?, public val criteria: NoulCriteria? = null) : Question(instructions)

/** An immutable request body, independent of transport and authentication. */
public class JevRequest(public val model: String, state: JsonElement?, questions: Map<String, Question>) {
    public val state: JsonElement? = state?.let(::freeze)
    public val questions: Map<String, Question> = immutableMap(questions)

    public fun validate(): List<RequestError> {
        val errors = mutableListOf<RequestError>()
        fun content(value: JsonElement?, path: String, allowNull: Boolean) {
            if (value == null) errors += RequestError("MISSING_CONTENT", path, "Supply content explicitly")
            else if (!validContent(value, allowNull)) errors += RequestError("CONTENT_KIND", path, "Expected text, object, array${if (allowNull) " or explicit null" else ""}")
            else errors += validateJson(value, path)
        }
        if (model.isBlank()) errors += RequestError("MODEL_REQUIRED", "$.model", "Supply an explicit nonblank model ID")
        content(state, "$.state", false)
        questions.forEach { (id, question) ->
            // Quote IDs using JSON escaping, so punctuation cannot make a path ambiguous.
            val path = "$.questions[${JsonPrimitive(id)}]"
            errors += validateQuestion(question, path)
        }
        return immutableList(errors)
    }

    /** Ordered body; optional Noul criteria are omitted, explicit null content is retained. */
    public fun toJsonObject(): JsonObject {
        requireValid(validate())
        return freeze(buildJsonObject {
            put("model", model)
            put("state", checkNotNull(state))
            put("questions", buildJsonObject {
                questions.forEach { (id, question) -> put(id, buildJsonObject {
                    put("type", when (question) { is ChoiceQuestion -> "choice"; is ScoreQuestion -> "score"; is NoulQuestion -> "noul" })
                    put("instructions", checkNotNull(question.instructions))
                    when (question) {
                        is ChoiceQuestion -> put("criteria", JsonObject(question.options))
                        is ScoreQuestion -> put("criteria", JsonArray(question.levels))
                        is NoulQuestion -> question.criteria?.let { c -> put("criteria", buildJsonObject {
                            c.yes?.let { put("true", it) }; c.no?.let { put("false", it) }
                        }) }
                    }
                }) }
            })
        }) as JsonObject
    }
    public fun toJsonString(): String = toJsonObject().toString()
}

@JevDsl
public class RequestBuilder(public val model: String) {
    private var stateValue: JsonElement? = null
    private val questionsValue = linkedMapOf<String, Question>()
    /** Text is never parsed even if it resembles JSON. Repeated state calls replace it. */
    public fun stateText(value: String) { stateValue = JsonPrimitive(value) }
    public fun state(value: JsonElement) { stateValue = freeze(value) }
    public fun <T> state(value: T, serializer: SerializationStrategy<T>) { state(Json.encodeToJsonElement(serializer, value)) }
    public inline fun <reified T> stateValue(value: T) { state(Json.encodeToJsonElement(value)) }
    public fun question(id: String, value: Question) {
        if (questionsValue.containsKey(id)) duplicate("QUESTION_DUPLICATE", "$.questions[${JsonPrimitive(id)}]", "Question ID already exists")
        questionsValue[id] = value
    }
    public fun questions(block: QuestionsBuilder.() -> Unit) { QuestionsBuilder(::question).apply(block) }
    public fun validate(): List<RequestError> = JevRequest(model, stateValue, questionsValue).validate()
    public fun build(): JevRequest = JevRequest(model, stateValue, questionsValue).also { requireValid(it.validate()) }
}

@JevDsl
public class QuestionsBuilder internal constructor(private val add: (String, Question) -> Unit) {
    public fun question(id: String, value: Question) { add(id, value) }
    public fun choice(id: String, block: ChoiceBuilder.() -> Unit) { add(id, ChoiceBuilder().apply(block).build()) }
    public fun score(id: String, block: ScoreBuilder.() -> Unit) { add(id, ScoreBuilder().apply(block).build()) }
    public fun noul(id: String, block: NoulBuilder.() -> Unit) { add(id, NoulBuilder().apply(block).build()) }
}

@JevDsl
public abstract class QuestionBuilder {
    protected var instructionsValue: JsonElement? = null
    public fun instructions(value: String) { instructionsValue = JsonPrimitive(value) }
    public fun instructions(value: JsonElement) { instructionsValue = freeze(value) }
    public fun <T> instructions(value: T, serializer: SerializationStrategy<T>) { instructions(Json.encodeToJsonElement(serializer, value)) }
    public inline fun <reified T> instructionsValue(value: T) { instructions(Json.encodeToJsonElement(value)) }
}

@JevDsl
public class ChoiceBuilder : QuestionBuilder() {
    private val options = linkedMapOf<String, JsonElement>()
    public fun option(key: String, description: String) { option(key, JsonPrimitive(description)) }
    public fun option(key: String, description: JsonElement = JsonNull) {
        if (options.containsKey(key)) duplicate("OPTION_DUPLICATE", "$.criteria[${JsonPrimitive(key)}]", "Option key already exists")
        options[key] = freeze(description)
    }
    public fun <T> option(key: String, value: T, serializer: SerializationStrategy<T>) { option(key, Json.encodeToJsonElement(serializer, value)) }
    public inline fun <reified T> optionValue(key: String, value: T) { option(key, Json.encodeToJsonElement(value)) }
    /** Domain values need an explicit wire label; neither ordinal nor toString is used. */
    public fun <T> options(values: Iterable<T>, wireLabel: (T) -> String, description: (T) -> JsonElement = { JsonNull }) {
        values.forEach { option(wireLabel(it), description(it)) }
    }
    public fun build(): ChoiceQuestion = ChoiceQuestion(instructionsValue, options).also { requireValid(it.validate()) }
}

@JevDsl
public class ScoreBuilder : QuestionBuilder() {
    private val levels = mutableListOf<JsonElement>()
    public fun level(description: String) { level(JsonPrimitive(description)) }
    public fun level(description: JsonElement) { levels += freeze(description) }
    public fun <T> level(value: T, serializer: SerializationStrategy<T>) { level(Json.encodeToJsonElement(serializer, value)) }
    public inline fun <reified T> levelValue(value: T) { level(Json.encodeToJsonElement(value)) }
    public fun build(): ScoreQuestion = ScoreQuestion(instructionsValue, levels).also { requireValid(it.validate()) }
}

@JevDsl
public class NoulBuilder : QuestionBuilder() {
    private var criteriaValue: NoulCriteria? = null
    public fun criteria(value: NoulCriteria) { criteriaValue = value }
    public fun criteria(block: NoulCriteriaBuilder.() -> Unit) { criteriaValue = NoulCriteriaBuilder().apply(block).build() }
    public fun build(): NoulQuestion = NoulQuestion(instructionsValue, criteriaValue).also { requireValid(it.validate()) }
}

@JevDsl
public class NoulCriteriaBuilder {
    private var yesValue: JsonElement? = null
    private var noValue: JsonElement? = null
    public fun yes(value: String) { yes(JsonPrimitive(value)) }
    public fun yes(value: JsonElement) { yesValue = freeze(value) }
    public fun no(value: String) { no(JsonPrimitive(value)) }
    public fun no(value: JsonElement) { noValue = freeze(value) }
    public fun <T> yes(value: T, serializer: SerializationStrategy<T>) { yes(Json.encodeToJsonElement(serializer, value)) }
    public fun <T> no(value: T, serializer: SerializationStrategy<T>) { no(Json.encodeToJsonElement(serializer, value)) }
    public inline fun <reified T> yesValue(value: T) { yes(Json.encodeToJsonElement(value)) }
    public inline fun <reified T> noValue(value: T) { no(Json.encodeToJsonElement(value)) }
    public fun build(): NoulCriteria = NoulCriteria(yesValue, noValue).also { requireValid(it.validate()) }
}

public fun jevRequest(model: String, block: RequestBuilder.() -> Unit): JevRequest = RequestBuilder(model).apply(block).build()

private fun validContent(value: JsonElement, allowNull: Boolean): Boolean = when (value) {
    JsonNull -> allowNull
    is JsonPrimitive -> value.isString
    is JsonObject, is JsonArray -> true
}
// JsonElement wrappers can be constructed over mutable caller collections, so freeze recursively.
private fun freeze(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> JsonObject(immutableMap(value.mapValues { freeze(it.value) }))
    is JsonArray -> JsonArray(immutableList(value.map(::freeze)))
    is JsonPrimitive -> value
}
private fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
private fun <T> immutableMap(values: Map<String, T>): Map<String, T> = Collections.unmodifiableMap(LinkedHashMap(values))
private fun requireValid(errors: List<RequestError>) { if (errors.isNotEmpty()) throw InvalidRequestException(errors) }
private fun duplicate(code: String, path: String, message: String): Nothing = throw InvalidRequestException(listOf(RequestError(code, path, message)))

private fun validateQuestion(question: Question, path: String): List<RequestError> {
    val errors = mutableListOf<RequestError>()
    fun content(value: JsonElement?, path: String, allowNull: Boolean) {
        if (value == null) errors += RequestError("MISSING_CONTENT", path, "Supply content explicitly")
        else if (!validContent(value, allowNull)) errors += RequestError("CONTENT_KIND", path, "Expected text, object, array or explicit null")
        else errors += validateJson(value, path)
    }
    content(question.instructions, "$path.instructions", true)
    when (question) {
        is ChoiceQuestion -> {
            if (question.options.size > 255) errors += RequestError("CHOICE_SIZE", "$path.criteria", "Expected at most 255 options")
            question.options.forEach { (key, value) -> content(value, "$path.criteria[${JsonPrimitive(key)}]", true) }
        }
        is ScoreQuestion -> {
            if (question.levels.size !in 2..10) errors += RequestError("SCORE_SIZE", "$path.criteria", "Expected 2 to 10 ordered levels")
            question.levels.forEachIndexed { index, value -> content(value, "$path.criteria[$index]", true) }
        }
        is NoulQuestion -> question.criteria?.let {
            it.yes?.let { value -> content(value, "$path.criteria.true", true) }
            it.no?.let { value -> content(value, "$path.criteria.false", true) }
        }
    }
    return immutableList(errors)
}

private val jsonNumber = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
private fun validateJson(value: JsonElement, path: String): List<RequestError> = when (value) {
    is JsonObject -> value.flatMap { (key, item) -> validateJson(item, "$path[${JsonPrimitive(key)}]") }
    is JsonArray -> value.flatMapIndexed { index, item -> validateJson(item, "$path[$index]") }
    is JsonPrimitive -> if (value === JsonNull || value.isString || value.content == "true" || value.content == "false" || jsonNumber.matches(value.content)) emptyList()
        else listOf(RequestError("JSON_LITERAL", path, "Expected a valid JSON literal; nonfinite numbers and raw tokens are unsupported"))
}
