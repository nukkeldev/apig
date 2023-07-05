@file:Suppress("NAME_SHADOWING")

package io.github.nukkeldev

import io.github.nukkeldev.util.formatAsClassName
import io.github.nukkeldev.util.formatAsParameterName
import io.github.nukkeldev.util.toTemplate
import java.io.File

val classTemplate = """
    %~urlCond -> "/**\n* Endpoint: %url%\n*/"~%
    object %name% {
        %~isEndpoint -> "%functions%"~%
        %~space -> ""~%
        %~hasChildren -> "%children%"~%
    }
""".trimIndent().toTemplate()

val functionTemplate = """
    suspend fun %name%(%parameters%): %return% {
        return getURL<%return%>("%url%")
    }
""".trimIndent().toTemplate()

val schemaTemplate = """
    package %package%
    
    import kotlinx.serialization.SerialName
    import kotlinx.serialization.Serializable
    import kotlinx.serialization.json.*
    
    @Suppress("PropertyName")
    @Serializable
    data class %name%(
        %properties%
    )
""".trimIndent().toTemplate()

data class Path(
    var url: String? = null,
    var parameter: String? = null,
    val pathItemObject: OpenAPI.PathItemObject? = null,
    var children: MutableMap<String, Path> = mutableMapOf()
) {
    fun writeClass(className: String, api: OpenAPI): String {
        val childClasses = children.map { (k, v) ->
            v.writeClass(k, api)
        }

        val endp = url != null
        val hasChildren = childClasses.isNotEmpty()

        val functions = if (pathItemObject != null) {
            with(pathItemObject) {
                listOf(
                    "get" to get,
                    "put" to put,
                    "post" to post,
                    "delete" to delete,
                    "options" to options,
                    "head" to head,
                    "patch" to patch,
                    "trace" to trace,
                )
            }.mapNotNull { (k, v) ->
                v?.let { v2 ->
                    val parameters = v2.parameters?.let { params ->
                        params.joinToString(", ") { param ->
                            val (_, p) = param.resolve(api).let {
                                it.first to it.second as OpenAPI.ParameterOrReference
                            }

                            val paramName = p.name?.formatAsParameterName()
                            val (_, qualifiedType) = writeType(null, p.schema!!, api)
                            val required = p.required

                            "$paramName: $qualifiedType${if (required) "" else "? = null"}"
                        }
                    } ?: ""

                    val (_, qualifiedType) = v2.responses!!.statusCodes?.entries?.find { (code, _) -> code.startsWith("2") }
                        ?.let { (_, respOrRef) ->
                            val (_, resp) = respOrRef.resolve(api).let {
                                it.first to it.second as OpenAPI.ResponseOrReference
                            }

                            // TODO: Find Correct Entry?
                            resp.content?.entries?.first()?.let {
                                val (name, schema) = it.value.schema?.resolve(api)?.let { (name, ref) ->
                                    name to (ref as OpenAPI.SchemaOrReference)
                                } ?: (null to null)

                                try {
                                    OpenAPI.getTypeString(schema?.type!!) to false
                                } catch (_: Exception) {
                                    writeType(name, schema!!, api)
                                }
                            }
                        } ?: ("Unit" to false)

                    functionTemplate.build(
                        mapOf(
                            "name" to k,
                            "parameters" to parameters,
                            "url" to url!!.split("/").joinToString("/") {
                                if (it.startsWith("{") && it.endsWith("}")) "\$" + it.substring(1, it.length - 1)
                                    .formatAsParameterName() else it
                            },
                            "return" to "$qualifiedType?"
                        )
                    )
                }
            }
        } else {
            listOf()
        }.joinToString("\n\n")

        return classTemplate.build(
            mapOf(
                "name" to className.trim('{', '}').formatAsClassName(),
                "urlCond" to endp.toString(),
                "url" to (url ?: ""),
                "isEndpoint" to endp.toString(),
                "functions" to functions,
                "space" to (endp && hasChildren).toString(),
                "hasChildren" to hasChildren.toString(),
                "children" to childClasses.joinToString("\n\n")
            )
        )
    }
}

fun writeType(
    name: String?,
    schema: OpenAPI.SchemaOrReference,
    api: OpenAPI,
    depth: Int = 0,
    parentName: String? = null,
    propertyName: String? = null
): Pair<String, String> /* (Short, Qualified) */ {
    var (name, schema) = schema.resolve(api, name)
        .let { (name, schema) -> name?.formatAsClassName() to schema as OpenAPI.SchemaOrReference }
    if (name == null && parentName != null && propertyName != null)
        name = "$parentName${propertyName.formatAsClassName()}"

    return when (schema.type) {
        "object" -> {
            schema.properties?.let { properties ->
                if (properties.isEmpty()) return "Unit" to "Unit"
                if (name != null) {
                    val props =
                        properties.entries.joinToString("\n\t") { (k, v) ->
                            val k2 = when (k) {
                                "in" -> "in_"
                                else -> k.formatAsParameterName()
                            }
                            "@SerialName(\"$k\") val $k2: ${
                                writeType(
                                    null,
                                    v,
                                    api,
                                    depth + 1,
                                    name,
                                    k2
                                ).first
                            }${if (k !in (schema.required ?: listOf())) "? = null" else ""},"
                        }

                    File(api.outputDirectory!!, "schemas/$name.kt").writeText(
                        schemaTemplate.build(
                            mapOf(
                                "package" to api.basePackage + ".schemas",
                                "name" to name,
                                "properties" to props
                            )
                        )
                    )

                    return name to (api.basePackage + ".schemas." + name)
                }
                error("Uh Oh!")
            }
            schema.additionalProperties?.let {
                if (it.boolean != null)
                    error("Boolean supplied, where a schema was expected.\nSchema: $schema")
                val (additionalName, additionalSchema) = it.schemaOrReference?.resolve(api)
                    ?.let { (name, ref) -> name to (ref as OpenAPI.SchemaOrReference) } ?: (null to null)

                return writeType(additionalName, additionalSchema!!, api, depth + 1, parentName, propertyName)
            }
            if (schema.description != null) // They have a reason for it
                return "Unit" to "Unit"
            error("Schema is of type 'object' but no 'properties' or 'additionalProperties' were supplied.\nSchema: $schema")
        }

        "array" -> {
            val (itemName, itemSchema) = schema.items?.resolve(api)?.let { (name, ref) ->
                name to (ref as OpenAPI.SchemaOrReference)
            } ?: (null to null)

            val (n, custom) = writeType(itemName, itemSchema!!, api, depth + 1, parentName, propertyName)

            "List<$n>" to custom
        }

        else -> OpenAPI.getTypeString(schema.type!!).let { it to it }
    }
}

fun Map.Entry<String, Path>.print(indentLevel: Int = 0, params: MutableList<String> = mutableListOf()): String {
    val (route, path) = this

    val indent = "\t".repeat(indentLevel)
    var output = "${indent}$route/"
    path.parameter?.let {
        if (!route.endsWith("}"))
            output += "{$it}"
    }
    params.let {
        if (it.isNotEmpty())
            output += " [${params.joinToString(", ") { s -> s.substring(1, s.length - 1) }}]"
    }
    path.pathItemObject?.let {
        output += " *"
    }
    output += "\n"

    for (child in path.children)
        output += child.print(indentLevel + 1, params.toMutableList().let {
            path.parameter?.let { param -> it.add(param) }
            it
        })

    return output
}

fun Path.print(): String = mapOf("" to this).entries.first().print()