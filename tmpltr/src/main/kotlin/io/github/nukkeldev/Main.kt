package io.github.nukkeldev

import org.slf4j.LoggerFactory
import java.util.*

class Template(
    templateBuilder: TemplateScope.() -> String
) {
    private val logger = LoggerFactory.getLogger(Template::class.java)

    private val template: String
    private val templateScope: TemplateScope = TemplateScope()

    init {
        template = templateBuilder(templateScope)
    }

    fun build(indent: Int = 0, buildScope: BuildScope.() -> Unit): String {
        val scope = BuildScope()
        buildScope(scope)

        val arguments = scope.variables
        val variables = templateScope.variables

        var output = template

        for ((k, v) in variables) {
            val anyValue = arguments[k]

            if (anyValue == null) {
                if ((arguments.containsKey(k) && v.nullable) || (!arguments.containsKey(k) && v.optional)) {
                    output = output.replace(v.toString(), if (v.wholeline) "\\xRemove" else "")
                } else {
                    error("Value '$k' supplied is null, but it was either non-nullable supplied as null or non-optional and not supplied")
                }
            } else {
                output = output.replace(v.toString(), v.format(anyValue))
            }
        }

        return output
            .lines()
            .filter { l -> !l.contains("\\xRemove") }
            .mapIndexed { index, s -> if (index > 0) s.prependIndent(" ".repeat(indent)) else s }
            .joinToString("\n")
    }

    override fun toString(): String =
        """
            Variables: ${templateScope.variables.map { v -> v.toString() }}
        """.trimIndent()

    data class Variable<T>(
        val name: String,
        var type: Class<*> = Any::class.java,
        var formatter: (T) -> String = { v -> v.toString() },
        var optional: Boolean = false,
        var wholeline: Boolean = false,
        var nullable: Boolean = false,
    ) {
        private val logger = LoggerFactory.getLogger(Variable::class.java)

        private val key: String = "[" + UUID.randomUUID().toString() + "]"

        constructor(scope: VariableScope<T>) : this(
            name = scope.name ?: error("No name set for variable"),
        ) {
            scope.type?.let { this.type = it }
            scope.formatter?.let { this.formatter = it }
            scope.optional?.let { this.optional = it }
            scope.wholeline?.let { this.wholeline = it }
            scope.nullable?.let { this.nullable = it }
        }

        override fun toString(): String {
            return key
        }

        @Suppress("UNCHECKED_CAST")
        private fun checkType(value: Any): T? {
            return if (type.isInstance(value)) {
                value as T
            } else {
                null
            }
        }

        fun format(value: Any): String =
            formatter(checkType(value)
                .let { casted ->
                    casted
                        ?: error(
                            "Value supplied for '$name' ($value) is not the correct type!\n" +
                                    "It is of type '${value::class.java.simpleName}' but needs to be of type '${type.simpleName}'"
                        )
                })

        open class VariableScope<T> {
            var name: String? = null
            var type: Class<*>? = null
            var formatter: ((T) -> String)? = null
            var optional: Boolean? = null
            var wholeline: Boolean? = null
            var nullable: Boolean? = null
        }
    }

    class TemplateScope {
        val variables: MutableMap<String, Variable<*>> = mutableMapOf()

        fun <T> newVariable(builder: Variable.VariableScope<T>.() -> Unit): Variable<T> {
            val scope = Variable.VariableScope<T>()
            return Variable(builder(scope).let { scope }).also { variables[it.name] = it }
        }
    }

    class BuildScope {
        val variables: MutableMap<String, Any?> = mutableMapOf()

        fun <T : Any?> setVariable(name: String, value: T) {
            variables[name] = value
        }
    }
}

fun main() {
    val template = Template {
        val name = newVariable<String> {
            this.name = "name"
        }

        val parameters = newVariable<List<String>> {
            this.name = "parameters"
            this.type = List::class.java
            this.formatter = { parameters -> parameters.joinToString(", ") }
        }

        val type = newVariable<String> {
            this.name = "type"
        }

        // GENERIC ARE REQUIRED FOR COMPILATION
        val condition = newVariable<Boolean> {
            this.name = "condition"
            this.type = Boolean::class.java
            this.formatter = { if (it) "" else "\\xRemove" }
            this.optional = true
            this.wholeline = true
        }

        val nullable = newVariable<Any?> {
            this.name = "nullable"
            this.nullable = true
            this.formatter = { it?.toString() ?: "" }
        }

        """
            suspend fun $name($parameters): $type {
                ${condition}Good Morning!
                $nullable
            }
        """.trimIndent()
    }

    println(
        template.build {
            setVariable("name", "thisIsACoolFunction")
            setVariable("parameters", listOf("cool: String? = null"))
            setVariable("type", "String")
            setVariable("nullable", null)
        }
    )
}