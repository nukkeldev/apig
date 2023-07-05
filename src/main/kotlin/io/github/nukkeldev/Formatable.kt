package io.github.nukkeldev

interface Formatable {
    fun format(indentLevel: Int = 0): String
}