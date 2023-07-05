package io.github.nukkeldev.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}

fun String.logger(): Logger {
    return LoggerFactory.getLogger(this)
}