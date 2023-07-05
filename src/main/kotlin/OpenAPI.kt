import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import util.formatAsClassName

/**
 * Conforms to the (Unextended) OpenAPI 3.0.3 Specs.
 * https://spec.openapis.org/oas/v3.0.3.html
 */
@Suppress("PropertyName")
@Serializable
data class OpenAPI(
    @SerialName("openapi") val version: String,
    val info: Info,
    val servers: List<Server>? = null,
    val paths: Map<String, PathItemObject>,
    val components: Components? = null,
    val tags: List<Tag>? = null,
    val externalDocs: ExternalDocumentation? = null,
    val security: List<Map<String, List<String>>>? = null,
    var outputDirectory: String? = null,
    var basePackage: String? = null,
) {
    @Serializable
    data class Info(
        val title: String,
        val description: String? = null,
        val termsOfService: String? = null,
        val contact: Contact? = null,
        val license: License? = null,
        val version: String
    ) {
        @Serializable
        data class Contact(
            val name: String? = null,
            val url: String? = null,
            val email: String? = null,
        )

        @Serializable
        data class License(
            val name: String,
            val url: String?
        )
    }

    @Serializable
    data class Server(
        val url: String,
        val description: String? = null,
        val variables: Map<String, ServerVariableObject>? = null,
    ) : Formatable {
        @Serializable
        data class ServerVariableObject(
            val enum: List<String>? = null,
            val default: String,
            val description: String?
        ) : Formatable {
            override fun format(indentLevel: Int): String = "${"\t".repeat(indentLevel)}${description.orEmpty()}${
                if (!enum.isNullOrEmpty())
                    " [${enum.joinToString(", ") { s -> if (s == default) "*$s*" else s }}]"
                else
                    " [Default: $default]"
            }"

            override fun toString(): String = format(0)
        }

        override fun format(indentLevel: Int): String {
            val indent = "\t".repeat(indentLevel)
            var output = ""
            if (description != null) output += "$indent// Description: $description\n"
            if (variables != null) output += "$indent// Variables:\n${variables.entries.joinToString { (k, v) -> "$indent\t$k - $v" }}"
            output += indent + "\"$url\""
            return output
        }
    }

    @Serializable
    data class PathItemObject(
        @SerialName("\$ref") val ref: String? = null,
        val summary: String? = null,
        val description: String? = null,
        val get: Operation? = null,
        val put: Operation? = null,
        val post: Operation? = null,
        val delete: Operation? = null,
        val options: Operation? = null,
        val head: Operation? = null,
        val patch: Operation? = null,
        val trace: Operation? = null,
        val servers: List<Server>? = null,
        val parameters: List<ParameterOrReference>? = null
    )

    @Serializable
    data class Operation(
        val tags: List<String>? = null,
        val summary: String? = null,
        val description: String? = null,
        val externalDocs: ExternalDocumentation? = null,
        val operationId: String? = null,
        val parameters: List<ParameterOrReference>? = null,
        val requestBody: RequestBodyOrReference? = null,
        @SerialName("_responses") var responses: Responses? = null, // REQUIRED
        @SerialName("responses") private val _responses: Map<String, ResponseOrReference>? = null,
        @SerialName("_callbacks") var callbacks: Map<String, CallbackOrReference>? = null,
        @SerialName("callbacks") private val _callbacks: JsonObject? = null,
        val deprecated: Boolean = false,
        val security: List<Map<String, List<String>>>? = null,
        val servers: List<Server>? = null,
    ) {
        init {
            responses = _responses?.let { resps ->
                Responses(
                    default = resps["default"],
                    statusCodes = resps.filterKeys { key -> key != "default" }
                )
            }

            callbacks = _callbacks?.let { cb ->
                cb.entries.associate { e ->
                    val obj = e.value.jsonObject
                    if (obj.containsKey("\$ref"))
                        return@associate e.key to CallbackOrReference(
                            ref = obj["\$ref"]?.jsonPrimitive?.content
                        )
                    return@associate e.key to CallbackOrReference(
                        callback = obj.entries.associate { cbe ->
                            cbe.key to OpenAPIParser.json.decodeFromString<PathItemObject>(
                                cbe.value.toString()
                            )
                        }
                    )
                }
            }
        }
    }

    @Serializable
    open class Reference(open val ref: String? = null) {
        fun resolve(api: OpenAPI, name: String? = null): Pair<String?, Reference> =
            if (this.ref != null) {
                api.getReference(this.ref!!).let { (name ?: it.first) to it.second as Reference }
            } else {
                name to this
            }
    }

    @Serializable
    data class ParameterOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Parameter
        val name: String? = null, // REQUIRED
        @SerialName("in") val in_: String? = null, // REQUIRED
        val description: String? = null,
        val required: Boolean = false,
        val deprecated: Boolean = false,
        val allowEmptyValue: Boolean = false,

        // Simple Config
        val style: String? = null,
        val explode: Boolean = true,
        val allowReserved: Boolean = false,
        val schema: SchemaOrReference? = null,
        val example: JsonElement? = null,
        val examples: Map<String, ExampleOrReference>? = null,

        // Complex Config
        val content: Map<String, Media>? = null,

        // Style Values
        val matrix: JsonElement? = null, // in: path
        val label: JsonElement? = null, // in: path
        val form: JsonElement? = null, // in: query, cookie
        val simple: JsonArray? = null, // in: path, header
        val spaceDelimited: JsonArray? = null, // in: query
        val pipeDelimited: JsonArray? = null, // in: query
        val deepObject: JsonObject? = null, // in: query
    ) : Reference()

    @Serializable
    data class SchemaOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Schema
        // From JSON Schema Specs
        val title: String? = null,
        val multipleOf: Int? = null,
        val maximum: Int? = null,
        val exclusiveMaximum: Int? = null,
        val minimum: Int? = null,
        val exclusiveMinimum: Int? = null,
        val maxLength: Int? = null,
        val minLength: Int? = null,
        val pattern: String? = null, // REGEX,
        val maxItems: Int? = null,
        val minItems: Int? = null,
        val uniqueItems: Boolean = false,
        val maxProperties: Int? = null,
        val minProperties: Int? = null,
        val required: List<String>? = null,
        val enum: List<JsonElement>? = null,

        // Adjusted for OpenAPI
        val type: String? = null,
        val allOf: SchemaOrReference? = null,
        val oneOf: SchemaOrReference? = null,
        val anyOf: SchemaOrReference? = null,
        val not: SchemaOrReference? = null,
        val items: SchemaOrReference? = null,
        val properties: Map<String, SchemaOrReference>? = null,
        @SerialName("_additionalProperties") var additionalProperties: BooleanOrSchemaOrReference? = null,
        @SerialName("additionalProperties") private val _additionalProperties: JsonElement? = null,
        val description: String? = null,
        val format: String? = null,
        val default: JsonElement? = null,

        // OpenAPI Unique
        val nullable: Boolean = false,
        val discriminator: Discriminator? = null,
        val readOnly: Boolean = false,
        val writeOnly: Boolean = false,
        val xml: XML? = null,
        val externalDocs: ExternalDocumentation? = null,
        val example: JsonElement? = null,
        val deprecated: Boolean = false,
    ) : Reference() {
        init {
            additionalProperties = _additionalProperties?.let { elm ->
                try {
                    return@let BooleanOrSchemaOrReference(
                        boolean = elm.jsonPrimitive.boolean
                    )
                } catch (_: Exception) {
                }

                BooleanOrSchemaOrReference(
                    schemaOrReference = OpenAPIParser.json.decodeFromString<SchemaOrReference>(elm.jsonObject.toString())
                )
            }
        }

        @Serializable
        data class BooleanOrSchemaOrReference(
            val boolean: Boolean? = null,
            val schemaOrReference: SchemaOrReference? = null,
        )
    }

    @Serializable
    data class Discriminator(
        val propertyName: String,
        val mapping: Map<String, String>? = null,
    )

    @Serializable
    data class XML(
        val name: String? = null,
        val namespace: String? = null,
        val prefix: String? = null,
        val attribute: Boolean = false,
        val wrapped: Boolean = false,
    )

    @Serializable
    data class ExampleOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Example
        val summary: String? = null,
        val description: String? = null,
        val value: JsonElement? = null,
        val externalValue: String? = null
    ) : Reference()

    @Serializable
    data class Media(
        val schema: SchemaOrReference? = null,
        val example: JsonElement? = null,
        val examples: Map<String, ExampleOrReference>? = null,
        val encoding: Map<String, Encoding>? = null,
    )

    @Serializable
    data class Encoding(
        val contentType: String? = null,
        val headers: Map<String, HeaderOrReference>? = null,
        val style: String? = null,
        val explode: Boolean = true,
        val allowReserved: Boolean = false,
    )

    @Serializable
    data class HeaderOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Header
        val description: String? = null,
        val required: Boolean = false,
        val deprecated: Boolean = false,
        val allowEmptyValue: Boolean = false,

        // Simple Config
        val style: String? = null,
        val explode: Boolean = true,
        val allowReserved: Boolean = false,
        val schema: SchemaOrReference? = null,
        val example: JsonElement? = null,
        val examples: Map<String, ExampleOrReference>? = null,

        // Complex Config
        val content: Map<String, Media>? = null,
    ) : Reference()

    @Serializable
    data class RequestBodyOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Request Body
        val description: String? = null,
        val content: Map<String, Media>? = null, // REQUIRED
        val required: Boolean = false,
    ) : Reference()

    @Serializable
    data class Responses(
        val default: ResponseOrReference? = null,
        // https://spec.openapis.org/oas/v3.0.3.html#patterned-fields-0
        val statusCodes: Map<String, ResponseOrReference>? = null,
    )

    @Serializable
    data class ResponseOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Response
        val description: String? = null, // REQUIRED
        val headers: Map<String, HeaderOrReference>? = null,
        val content: Map<String, Media>? = null,
        val links: Map<String, LinkOrReference>? = null,
    ) : Reference()

    @Serializable
    data class LinkOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Link
        val operationRef: String? = null,
        val operationId: String? = null,
        val parameters: Map<String, JsonObject>? = null, // JsonObject = Any | {expression}
        val requestBody: JsonObject? = null, // ^
        val description: String? = null,
        val server: Server? = null,
    ) : Reference()

    @Serializable
    data class CallbackOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Callback
        val callback: Map<String, PathItemObject>? = null,
    ) : Reference()

    @Serializable
    data class Components(
        val schemas: Map<String, SchemaOrReference>? = null,
        val responses: Map<String, ResponseOrReference>? = null,
        val parameters: Map<String, ParameterOrReference>? = null,
        val examples: Map<String, ExampleOrReference>? = null,
        val requestBodies: Map<String, RequestBodyOrReference>? = null,
        val headers: Map<String, HeaderOrReference>? = null,
        val securitySchemes: Map<String, SecuritySchemeOrReference>? = null,
        val links: Map<String, LinkOrReference>? = null,
        val callbacks: Map<String, CallbackOrReference>? = null,
    )

    @Serializable
    data class SecuritySchemeOrReference(
        // Reference
        @SerialName("\$ref") override val ref: String? = null,

        // Security Scheme
        val type: String? = null, // REQUIRED
        val description: String? = null,
        val name: String? = null, // REQUIRED
        @SerialName("in") val in_: String? = null, // REQUIRED
        val scheme: String? = null, // REQUIRED
        val bearerFormat: String? = null,
        val flows: OAuthFlows? = null, // REQUIRED
        val openIdConnectUrl: String? = null, // REQUIRED
    ) : Reference()

    @Serializable
    data class OAuthFlows(
        val implicit: OAuthFlow? = null,
        val password: OAuthFlow? = null,
        val clientCredentials: OAuthFlow? = null,
        val authorizationCode: OAuthFlow? = null,
    )

    @Serializable
    data class OAuthFlow(
        val authorizationUrl: String,
        val tokenUrl: String,
        val refreshUrl: String? = null,
        val scopes: Map<String, String>
    )

    @Serializable
    data class Tag(
        val name: String,
        val description: String? = null,
        val externalDocs: ExternalDocumentation? = null
    )

    @Serializable
    data class ExternalDocumentation(
        val description: String? = null,
        val url: String
    )

    fun getReference(reference: String): Pair<String, Any?> {
        val path = reference.substring(2).split("/")

        return path[2].formatAsClassName() to when (path[0]) {
            "components" -> {
                val components = components!!
                when (path[1]) {
                    "schemas" -> {
                        components.schemas?.get(path[2])
                    }

                    "responses" -> {
                        components.responses?.get(path[2])
                    }

                    "parameters" -> {
                        components.parameters?.get(path[2])
                    }

                    "examples" -> {
                        components.examples?.get(path[2])
                    }

                    "requestBodies" -> {
                        components.requestBodies?.get(path[2])
                    }

                    "headers" -> {
                        components.headers?.get(path[2])
                    }

                    "securitySchemes" -> {
                        components.securitySchemes?.get(path[2])
                    }

                    "links" -> {
                        components.links?.get(path[2])
                    }

                    "callbacks" -> {
                        components.callbacks?.get(path[2])
                    }

                    else -> null
                }
            }

            else -> {
                null
            }
        }
    }

    companion object {
        fun getTypeString(type: String): String =
            when (type) {
                "string" -> "String"
                "integer" -> "Int"
                "boolean" -> "Boolean"
                "number" -> "Double"
                else -> error("Unknown Type: $type")
            }
    }
}