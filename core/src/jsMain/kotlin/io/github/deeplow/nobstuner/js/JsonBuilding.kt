package io.github.deeplow.nobstuner.js

/**
 * The JSON below is assembled by hand rather than with kotlinx.serialization.
 *
 * The library is already on the Android side for storing custom tunings, but
 * pulling it into the browser bundle to emit four fixed strings at start-up
 * costs more than 200 KB — most of the download, for something a dozen lines of
 * string building do. Leaving it unreferenced lets dead-code elimination drop
 * it from the web build entirely.
 */
internal fun StringBuilder.quoted(value: String): StringBuilder {
    append('"')
    for (character in value) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character < ' ') {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    return append('"')
}

internal fun StringBuilder.field(name: String, value: String): StringBuilder =
    quoted(name).append(':').quoted(value)

internal fun StringBuilder.field(name: String, value: Int): StringBuilder =
    quoted(name).append(':').append(value)

internal fun StringBuilder.field(name: String, value: Double): StringBuilder =
    quoted(name).append(':').append(value)

internal fun StringBuilder.field(name: String, value: Boolean): StringBuilder =
    quoted(name).append(':').append(value)

internal fun StringBuilder.field(name: String, values: List<Int>): StringBuilder =
    quoted(name).append(':').append(values.joinToString(",", "[", "]"))

internal fun <T> Iterable<T>.jsonArray(item: StringBuilder.(T) -> Unit): String =
    buildString {
        append('[')
        this@jsonArray.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append('{')
            item(value)
            append('}')
        }
        append(']')
    }
