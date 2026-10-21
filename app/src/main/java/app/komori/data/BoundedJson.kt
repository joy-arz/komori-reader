package app.komori.data

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.CodingErrorAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Avoid materializing an arbitrarily expanded escaped JSON string before enforcing the byte limit.
 */
internal fun boundedJson(root: JSONObject): ByteArray {
    val bytes = ByteArrayOutputStream()
    val output =
        object : OutputStream() {
            override fun write(value: Int) {
                checkLimit(1)
                bytes.write(value)
            }

            override fun write(buffer: ByteArray, offset: Int, count: Int) {
                checkLimit(count)
                bytes.write(buffer, offset, count)
            }

            private fun checkLimit(count: Int) {
                if (bytes.size().toLong() + count > MAX_BACKUP_BYTES)
                    throw InvalidBackup("Backup exceeds the 16 MiB limit.")
            }
        }
    OutputStreamWriter(
            output,
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT),
        )
        .use { writeJson(root, it) }
    return bytes.toByteArray()
}

private fun writeJson(value: Any?, writer: Writer) {
    when (value) {
        null,
        JSONObject.NULL -> writer.write("null")
        is JSONObject -> {
            writer.write("{")
            value.keys().asSequence().forEachIndexed { index, key ->
                if (index > 0) writer.write(",")
                writeString(key, writer)
                writer.write(":")
                writeJson(value.get(key), writer)
            }
            writer.write("}")
        }
        is JSONArray -> {
            writer.write("[")
            for (index in 0 until value.length()) {
                if (index > 0) writer.write(",")
                writeJson(value.get(index), writer)
            }
            writer.write("]")
        }
        is String -> writeString(value, writer)
        is Boolean -> writer.write(value.toString())
        is Number -> writer.write(JSONObject.numberToString(value))
        else -> throw InvalidBackup("Unsupported backup value.")
    }
}

private fun writeString(value: String, writer: Writer) {
    writer.write("\"")
    var start = 0
    val escape = charArrayOf('\\', 'u', '0', '0', '0', '0')
    for (index in value.indices) {
        val ch = value[index]
        if (ch == '"' || ch == '\\' || ch < ' ') {
            if (index > start) writer.write(value, start, index - start)
            if (ch < ' ') {
                escape[4] = "0123456789abcdef"[ch.code / 16]
                escape[5] = "0123456789abcdef"[ch.code % 16]
                writer.write(escape)
            } else {
                writer.write("\\")
                writer.write(ch.code)
            }
            start = index + 1
        }
    }
    if (start < value.length) writer.write(value, start, value.length - start)
    writer.write("\"")
}
