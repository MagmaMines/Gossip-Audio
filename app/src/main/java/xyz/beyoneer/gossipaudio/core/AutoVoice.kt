package xyz.beyoneer.gossipaudio.core

import java.util.Locale

/** Conservative, offline language hints; null means the user should choose manually. */
object AutoVoice {
    private val words = mapOf(
        "ljspeech" to setOf("the", "and", "hello", "welcome", "this", "your", "is", "are", "with", "you", "today", "good"),
        "fr_super" to setOf("bonjour", "bienvenue", "français", "merci", "avec", "nous", "vous", "une", "les", "est", "pour", "dans"),
        "es_super" to setOf("hola", "bienvenidos", "español", "gracias", "buenos", "con", "para", "una", "los", "que", "está"),
        "de_super" to setOf("hallo", "willkommen", "deutsch", "danke", "und", "der", "die", "das", "ist", "mit", "ein", "ich"),
        "it_super" to setOf("ciao", "benvenuti", "italiano", "grazie", "buongiorno", "con", "per", "una", "sono", "questa"),
        "pt_super" to setOf("olá", "bem-vindos", "português", "obrigado", "obrigada", "você", "uma", "para", "com", "não", "esta")
    )

    fun detect(text: String): String? {
        val scripts = mutableSetOf<Character.UnicodeScript>()
        val iter = text.codePoints().iterator()
        while (iter.hasNext()) {
            val cp = iter.nextInt()
            if (Character.isLetter(cp)) scripts.add(Character.UnicodeScript.of(cp))
        }
        scripts.remove(Character.UnicodeScript.COMMON)
        scripts.remove(Character.UnicodeScript.INHERITED)
        if (scripts.isEmpty()) return null
        val kana = setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
        if (scripts.any { it in kana } && scripts.all { it in kana || it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.LATIN }) return "ja_super"
        for ((script, id) in listOf(Character.UnicodeScript.DEVANAGARI to "hi_super", Character.UnicodeScript.HANGUL to "ko_super", Character.UnicodeScript.ARABIC to "ar_super")) {
            if (script in scripts && scripts.all { it == script || it == Character.UnicodeScript.LATIN }) return id
        }
        // Han alone is ambiguous between Chinese and Japanese; never silently guess.
        if (scripts != setOf(Character.UnicodeScript.LATIN)) return null
        val tokens = Regex("[\\p{L}]+(?:-[\\p{L}]+)?").findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()
        val scores = words.mapValues { (_, clues) -> tokens.count { it in clues } }.entries.sortedByDescending { it.value }
        return if (scores.first().value > 0 && scores.first().value > scores[1].value) scores.first().key else null
    }
}
