package org.olo.player.viewer

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decoding a text file, sniffing its encoding.
 *
 * A phone is full of text in more than one encoding: a note saved by a modern
 * editor is UTF-8, but a great many Korean .lrc and .srt files on the same
 * phone are CP949, and opening one as the other turns every Korean character
 * into mojibake. So the bytes are sniffed rather than assumed.
 *
 * This is the reference app's text reader trimmed to what OLO Player needs of
 * it: reading an .lrc lyric sheet. The editor's write-back path and its
 * newline/BOM bookkeeping are gone; only [decode] and the sniffing remain.
 */
object TextFiles {

    private val UTF8: Charset = Charsets.UTF_8

    // CP949, the Windows Korean code page, where most non-UTF-8 Korean text on
    // a phone comes from. Named the Java way rather than "MS949", which not
    // every Android build knows.
    private val CP949: Charset = Charset.forName("x-windows-949")

    /** How a file decoded: its text, and the charset it was read with. */
    data class Loaded(val text: String, val charset: Charset)

    /**
     * Turns file bytes into text, sniffing the encoding.
     *
     * A byte-order mark settles it outright. Failing that, the bytes are run
     * through a strict UTF-8 decode: valid UTF-8 is distinctive enough that
     * text which decodes without a single malformed sequence is taken to be
     * UTF-8, and anything else falls back to CP949, which never rejects bytes
     * and so is the honest last resort rather than a second guess.
     */
    fun decode(bytes: ByteArray): Loaded {
        bomOf(bytes)?.let { (charset, mark) ->
            val text = String(bytes, mark.size, bytes.size - mark.size, charset)
            return Loaded(normalizeNewlines(text), charset)
        }
        val charset = if (isValidUtf8(bytes)) UTF8 else CP949
        return Loaded(normalizeNewlines(String(bytes, charset)), charset)
    }

    /** The byte-order mark at the front, as (charset, mark bytes), or null. */
    private fun bomOf(bytes: ByteArray): Pair<Charset, ByteArray>? {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        return when {
            bytes.startsWith(utf8) -> UTF8 to utf8
            bytes.startsWith(le) -> Charsets.UTF_16LE to le
            bytes.startsWith(be) -> Charsets.UTF_16BE to be
            else -> null
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    /** Whether every byte is valid UTF-8, decided by a strict decoder. */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = UTF8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)) }.isSuccess
    }

    /** Everything becomes `\n`. */
    private fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')
}
