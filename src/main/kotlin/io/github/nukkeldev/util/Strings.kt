package io.github.nukkeldev.util

fun String.capitalize() = if (isNotEmpty()) first().uppercase() + substring(1) else this
fun String.decapitalize() = if (isNotEmpty()) first().lowercase() + substring(1) else this
fun String.formatAsClassName() =
    split(Regex("[-_ ]")).joinToString("") { s -> s.capitalize() }
fun String.formatAsParameterName() = formatAsClassName().decapitalize()
private fun String.splitOnce(string: String): Pair<String, String> =
    substring(0, indexOf(string)) to substring(indexOf(string) + string.length)

data class StringTemplate(private val template: String, private val indent: Int = 0) {
    sealed class Param {
        abstract val string: String
    }

    data class Value(override val string: String) : Param()
    data class Cond(
        override val string: String,
        val template: StringTemplate,
        val condition: (Boolean, Map<String, String>) -> String
    ) : Param()

    private val parameters: List<Pair<String, Param>>

    init {
        val xParam = Regex("""%.*%""")
        val xParamVal = Regex("""%\w*?%""")
        val xParamCond = Regex("""%.* -> .*%""")
        val xLineParamCond = Regex("""%~.* -> .*~%""")

        parameters = xParam.findAll(template).flatMap { match ->
            val value = match.value

            xParamCond.findAll(value).let { if (it.count() == 0) null else it }?.map { submatch ->
                val line = submatch.value matches xLineParamCond
                val (c, t) = submatch.value.trim('%', '~').splitOnce(" -> ")

                val indentLevel = template.lines().find { l -> l.contains(value) }!!
                    .let { s -> s.substring(0, s.indexOf(value)) }.length
                val tmplt = t.trim('"').replace("\\n", "\n").toTemplate(indent = indentLevel)

                c to Cond(
                    value,
                    tmplt
                ) { cond, params -> if (cond) tmplt.build(params) else (if (line) "\\xRemove" else "") }
            }?.let { return@flatMap it.toList() }

            xParamVal.findAll(value).let { if (it.count() == 0) null else it }?.map { submatch ->
                submatch.value.trim('%') to Value(submatch.value)
            }?.let { return@flatMap it.toList() }

            emptyList()
        }.toList()
    }

    fun build(arguments: Map<String, String>): String {
        var output = template

        for ((k, param) in parameters) {
            when (param) {
                is Value -> arguments[k] ?: ""
                is Cond -> param.condition(
                    arguments[k].toBoolean(),
                    arguments.filterKeys { key -> key in param.template.parameters.map { (k3, _) -> k3 } })
            }.let {
                output = output.replace(param.string, it)
            }
        }

        output =
            output
                .lines()
                .filter { l -> !l.contains("\\xRemove") }
                .mapIndexed { index, s -> if (index > 0) s.prependIndent(" ".repeat(indent)) else s }
                .joinToString("\n")

        return output
    }
}

fun String.toTemplate(indent: Int = 0): StringTemplate = StringTemplate(this, indent)