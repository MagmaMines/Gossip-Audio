package xyz.beyoneer.gossipaudio.speech

/** Unicode-safe chunking shared by synthesis and JVM tests. */
object TextChunker {
    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars >= 2)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val punctuation = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';'), end - 1)
                val space = text.lastIndexOf(' ', end - 1)
                end = if (punctuation >= start + maxChars / 3) punctuation + 1 else if (space > start) space + 1 else end
                if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            }
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotEmpty()) out.add(chunk)
            start = end
        }
        return out
    }
}
