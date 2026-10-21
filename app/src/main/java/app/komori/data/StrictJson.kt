package app.komori.data

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class InvalidBackup(message: String) : IOException(message)

/** Bounded RFC 8259 syntax. org.json alone accepts duplicate keys and non-JSON syntax. */
internal class StrictJson(private val text: String) {
    private var position = 0
    private var nodes = 0

    fun document(): JSONObject {
        val result = value(0) as? JSONObject ?: fail("Expected a JSON object.")
        whitespace()
        if (position != text.length) fail("Unexpected content after the backup.")
        return result
    }

    private fun value(depth: Int): Any {
        if (depth > 24 || ++nodes > 500_000) fail("Backup structure exceeds the supported limits.")
        whitespace()
        return when (peek()) {
            '{' -> {
                position++
                val result = JSONObject()
                whitespace()
                if (!take('}')) {
                    do {
                        whitespace()
                        if (peek() != '"') fail("Expected a quoted field name.")
                        val key = string()
                        if (result.has(key)) fail("Duplicate field: ${key.take(60)}.")
                        whitespace()
                        if (!take(':')) fail("Expected ':' after a field name.")
                        result.put(key, value(depth + 1))
                        whitespace()
                        if (take('}')) return result
                    } while (take(','))
                    fail("Expected ',' or '}'.")
                }
                result
            }
            '[' -> {
                position++
                val result = JSONArray()
                whitespace()
                if (!take(']')) {
                    do {
                        result.put(value(depth + 1))
                        whitespace()
                        if (take(']')) return result
                    } while (take(','))
                    fail("Expected ',' or ']'.")
                }
                result
            }
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", JSONObject.NULL)
            '-',
            in '0'..'9' -> number()
            else -> fail("Invalid or incomplete JSON value.")
        }
    }

    private fun literal(token: String, value: Any): Any {
        if (!text.startsWith(token, position)) fail("Invalid JSON literal.")
        position += token.length
        return value
    }

    private fun number(): Number {
        val start = position
        take('-')
        if (!take('0')) digits()
        if (take('.')) digits()
        if (take('e') || take('E')) {
            if (!take('+')) take('-')
            digits()
        }
        val token = text.substring(start, position)
        if (token.length > 128) fail("JSON number exceeds the supported range.")
        if (token.none { it in ".eE" })
            return token.toLongOrNull() ?: fail("JSON integer is out of range.")
        return token.toDoubleOrNull()?.takeIf { it.isFinite() }
            ?: fail("JSON number is out of range.")
    }

    private fun digits() {
        val start = position
        while (peek() in '0'..'9') position++
        if (start == position) fail("Expected a digit.")
    }

    private fun string(): String {
        position++
        val result = StringBuilder()
        while (position < text.length) {
            val c = text[position++]
            if (c == '"') {
                val string = result.toString()
                var index = 0
                while (index < string.length) {
                    val ch = string[index++]
                    if (ch.isHighSurrogate()) {
                        if (index == string.length || !string[index++].isLowSurrogate())
                            fail("Invalid Unicode in backup text.")
                    } else if (ch.isLowSurrogate()) fail("Invalid Unicode in backup text.")
                }
                return string
            }
            if (c < ' ') fail("Unescaped control character in JSON text.")
            if (c != '\\') result.append(c)
            else {
                if (position >= text.length) fail("Incomplete JSON escape.")
                result.append(
                    when (val escaped = text[position++]) {
                        '"',
                        '\\',
                        '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            if (position + 4 > text.length) fail("Incomplete Unicode escape.")
                            val hex = text.substring(position, position + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                                fail("Invalid Unicode escape.")
                            position += 4
                            hex.toInt(16).toChar()
                        }
                        else -> fail("Invalid JSON escape.")
                    }
                )
            }
            if (result.length > 262_144) fail("Backup text field is too long.")
        }
        fail("Unterminated JSON string.")
    }

    private fun whitespace() {
        while (peek() in " \t\n\r") position++
    }

    private fun peek(): Char = text.getOrNull(position) ?: '\u0000'

    private fun take(c: Char): Boolean = (peek() == c).also { if (it) position++ }

    private fun fail(message: String): Nothing = throw InvalidBackup(message)
}
