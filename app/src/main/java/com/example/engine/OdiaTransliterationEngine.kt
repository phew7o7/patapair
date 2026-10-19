package com.example.engine

import com.example.model.ScriptType

object OdiaTransliterationEngine {

    private val ODIA_VOWELS = mapOf(
        '\u0B05' to "a",   // ଅ
        '\u0B06' to "aa",  // ଆ
        '\u0B07' to "i",   // ଇ
        '\u0B08' to "ee",  // ଈ
        '\u0B09' to "u",   // ଉ
        '\u0B0A' to "oo",  // ଊ
        '\u0B0B' to "ru",  // ଋ
        '\u0B0E' to "e",   // ଏ
        '\u0B0F' to "ai",  // ଐ
        '\u0B13' to "o",   // ଓ
        '\u0B14' to "au"   // ଔ
    )

    private val ODIA_MATRAS = mapOf(
        '\u0B3E' to "a",   // ା
        '\u0B3F' to "i",   // ି
        '\u0B40' to "ee",  // ୀ
        '\u0B41' to "u",   // ୁ
        '\u0B42' to "oo",  // ୂ
        '\u0B43' to "ru",  // ୃ
        '\u0B47' to "e",   // େ
        '\u0B48' to "ai",  // ୈ
        '\u0B4B' to "o",   // ୋ
        '\u0B4C' to "au"   // ୌ
    )

    private val ODIA_CONSONANTS = mapOf(
        '\u0B15' to "k",   // କ
        '\u0B16' to "kh",  // ଖ
        '\u0B17' to "g",   // ଗ
        '\u0B18' to "gh",  // ଘ
        '\u0B19' to "ng",  // ଙ
        '\u0B1A' to "ch",  // ଚ
        '\u0B1B' to "chh", // ଛ
        '\u0B1C' to "j",   // ଜ
        '\u0B1D' to "jh",  // ଝ
        '\u0B1E' to "ny",  // ଞ
        '\u0B1F' to "t",   // ଟ
        '\u0B20' to "th",  // ଠ
        '\u0B21' to "d",   // ଡ
        '\u0B22' to "dh",  // ଢ
        '\u0B23' to "n",   // ଣ
        '\u0B24' to "t",   // ତ
        '\u0B25' to "th",  // ଥ
        '\u0B26' to "d",   // ଦ
        '\u0B27' to "dh",  // ଧ
        '\u0B28' to "n",   // ନ
        '\u0B2A' to "p",   // ପ
        '\u0B2B' to "ph",  // ଫ
        '\u0B2C' to "b",   // ବ
        '\u0B2D' to "bh",  // ଭ
        '\u0B2E' to "m",   // ମ
        '\u0B2F' to "y",   // ଯ
        '\u0B5F' to "y",   // ୟ
        '\u0B30' to "r",   // ର
        '\u0B32' to "l",   // ଲ
        '\u0B33' to "l",   // ଳ
        '\u0B36' to "sh",  // ଶ
        '\u0B37' to "sh",  // ଷ
        '\u0B38' to "s",   // ସ
        '\u0B39' to "h",   // ହ
        '\u0B5C' to "d",   // ଡ଼
        '\u0B5D' to "dh"   // ଢ଼
    )

    private val ODIA_MODIFIERS = mapOf(
        '\u0B01' to "n",  // ଁ Candrabindu
        '\u0B02' to "n",  // ଂ Anusvara
        '\u0B03' to "h"   // ଃ Visarga
    )

    val ODIA_DIGITS_MAP = mapOf(
        '୦' to '0',
        '୧' to '1',
        '୨' to '2',
        '୩' to '3',
        '୪' to '4',
        '୫' to '5',
        '୬' to '6',
        '୭' to '7',
        '୮' to '8',
        '୯' to '9'
    )

    fun convertOdiaDigitsToAscii(input: String): String {
        val sb = java.lang.StringBuilder()
        for (ch in input) {
            sb.append(ODIA_DIGITS_MAP[ch] ?: ch)
        }
        return sb.toString()
    }

    private const val VIRAMA = '\u0B4D' // ୍ Halant

    fun detectScript(text: String): ScriptType {
        var odiaCount = 0
        var latinCount = 0
        for (ch in text) {
            when {
                ch in '\u0B00'..'\u0B7F' -> odiaCount++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latinCount++
            }
        }
        return when {
            odiaCount > 0 && latinCount == 0 -> ScriptType.ODIA
            latinCount > 0 && odiaCount == 0 -> ScriptType.LATIN
            odiaCount > 0 && latinCount > 0 -> ScriptType.MIXED
            else -> ScriptType.UNKNOWN
        }
    }

    fun transliterateOdiaToEnglish(text: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]

            // 1. Independent Vowels
            if (ODIA_VOWELS.containsKey(ch)) {
                sb.append(ODIA_VOWELS[ch])
                i++
                continue
            }

            // 2. Consonants
            if (ODIA_CONSONANTS.containsKey(ch)) {
                val base = ODIA_CONSONANTS[ch]!!
                val nextChar = if (i + 1 < len) text[i + 1] else null

                when {
                    nextChar == VIRAMA -> {
                        // Halant: suppress inherent vowel 'a'
                        sb.append(base)
                        i += 2 // skip consonant and virama
                        continue
                    }
                    nextChar != null && ODIA_MATRAS.containsKey(nextChar) -> {
                        // Followed by matra
                        sb.append(base)
                        sb.append(ODIA_MATRAS[nextChar])
                        i += 2 // skip consonant and matra
                        continue
                    }
                    else -> {
                        val isWordStart = (i == 0) || (i > 0 && text[i - 1].isWhitespace())
                        val isWordEnd = (i + 1 >= len) || text[i + 1].isWhitespace()
                        val isConjunctEndingWithR = (i > 0 && text[i - 1] == VIRAMA && base == "r")
                        val hasFollowingConsonantWithMatra = (i + 2 < len) &&
                                ODIA_CONSONANTS.containsKey(text[i + 1]) &&
                                ODIA_MATRAS.containsKey(text[i + 2]) &&
                                text[i + 1] != '\u0B39' // not 'h' (e.g. Maha)

                        if (isWordEnd && !isConjunctEndingWithR) {
                            sb.append(base)
                        } else if (hasFollowingConsonantWithMatra && !isWordStart) {
                            sb.append(base)
                        } else {
                            sb.append(base).append("a")
                        }
                        i++
                        continue
                    }
                }
            }

            // 3. Modifiers (anusvara, visarga)
            if (ODIA_MODIFIERS.containsKey(ch)) {
                sb.append(ODIA_MODIFIERS[ch])
                i++
                continue
            }

            // 4. Pass-through punctuation, spaces, latin characters
            sb.append(ch)
            i++
        }

        return cleanTransliteration(sb.toString())
    }

    private fun cleanTransliteration(raw: String): String {
        return raw.split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                // Clean double inherent vowels like "aa" if caused by 'a' + 'a'
                var cleaned = word.replace("aaa", "aa")
                // Capitalize first letter of each word
                cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    fun normalizeForMatching(name: String): String {
        var n = name.lowercase().trim()
        // Transliterate if contains Odia characters
        if (detectScript(n) == ScriptType.ODIA || detectScript(n) == ScriptType.MIXED) {
            n = transliterateOdiaToEnglish(n).lowercase()
        }

        // Remove honorifics and land prefixes
        n = n.replace("\\b(sri|shree|smt|smti|mr|mrs|dr|late|shri)\\b".toRegex(), " ")

        // Common Odia phonetic and spelling normalizations
        n = n.replace("omakara", "omkar")
        n = n.replace("omakar", "omkar")
        n = n.replace("omkara", "omkar")
        n = n.replace("ratha", "rath")
        n = n.replace("\\bnaik\\b".toRegex(), "nayak")
        n = n.replace("pattnayak", "patnaik")
        n = n.replace("patnayak", "patnaik")
        n = n.replace("pattanaik", "patnaik")
        n = n.replace("pattnaik", "patnaik")
        n = n.replace("mahapatra", "mohapatra")
        // Note: routray and rout are distinct Odia surnames; do not collapse them
        n = n.replace("sahu", "sahoo")
        n = n.replace("dash", "das")
        n = n.replace("tripathy", "tripathi")
        n = n.replace("mohanty", "mahanty")
        n = n.replace("padhee", "padhi")
        n = n.replace("misra", "mishra")
        n = n.replace("choudhury", "choudhary")
        n = n.replace("manjhi", "majhi")
        n = n.replace("pradhani", "pradhan")
        n = n.replace("chandana", "chandan")
        n = n.replace("tandy", "tandi")
        n = n.replace("gauda", "gouda")
        n = n.replace("baga", "bag")
        n = n.replace("dipa", "deep")
        n = n.replace("patela", "patel")
        n = n.replace("sunany", "sunani")
        n = n.replace("muduly", "muduli")
        n = n.replace("sethy", "sethi")

        // Cross-script consonant & phonetic harmonizations:
        // 'ksh' and 'x' are commonly written as 'kh' in Odisha (e.g. Lakheswar / Laksheswar)
        n = n.replace("ksh", "kh")
        n = n.replace("x", "kh")
        n = n.replace("shw", "sw")
        n = n.replace("sh", "s")
        n = n.replace("ee", "i")
        n = n.replace("oo", "u")
        // In Odia script 'b' and 'v'/'w' are represented similarly (e.g. Laksheshbar vs Laksheshwar)
        n = n.replace("w", "b")
        n = n.replace("v", "b")
        n = n.replace("bhabani", "bhavani")

        // De-duplicate double consonants (Dillip -> Dilip, Pattnaik -> Patnaik)
        n = n.replace("ll", "l")
            .replace("tt", "t")
            .replace("dd", "d")
            .replace("nn", "n")
            .replace("mm", "m")
            .replace("pp", "p")
            .replace("bb", "b")
            .replace("kk", "k")

        // Clean extra whitespace and apply phonetic terminal schwa normalization for Odia given names
        val tokens = n.split("\\s+".toRegex()).filter { it.isNotBlank() }.map { token ->
            var t = token
            // Specific frequent Odia transliterations
            when {
                t.endsWith("kumara") -> t = t.substring(0, t.length - 1)
                t.endsWith("ranjana") -> t = t.substring(0, t.length - 1)
                t.endsWith("singha") -> t = t.substring(0, t.length - 1)
                t.endsWith("pradhana") -> t = t.substring(0, t.length - 1)
                t.endsWith("nayaka") -> t = t.substring(0, t.length - 1)
                t.endsWith("dasa") -> t = t.substring(0, t.length - 1)
                t.endsWith("barika") -> t = t.substring(0, t.length - 1)
                t.endsWith("manasa") -> t = t.substring(0, t.length - 1)
                t.endsWith("bijaya") -> t = t.substring(0, t.length - 1)
                t.endsWith("sanjaya") -> t = t.substring(0, t.length - 1)
                t.endsWith("niranjana") -> t = t.substring(0, t.length - 1)
                t.endsWith("ashoka") -> t = t.substring(0, t.length - 1)
                t.endsWith("aloka") -> t = t.substring(0, t.length - 1)
                t.endsWith("deepaka") -> t = t.substring(0, t.length - 1)
                t.endsWith("subodha") -> t = t.substring(0, t.length - 1)
                t.endsWith("pramoda") -> t = t.substring(0, t.length - 1)
                t.endsWith("santosa") -> t = t.substring(0, t.length - 1)
                t.endsWith("dillipa") -> t = "dilip"
                t.endsWith("dilipa") -> t = "dilip"
                t.endsWith("duryodhana") -> t = "duryodhan"
                t.endsWith("ghasirama") -> t = "ghasiram"
                t.endsWith("tikarama") -> t = "tikaram"
                t.endsWith("purnachandra") -> t = "purnachandra"
                t.endsWith("purnachandraa") -> t = "purnachandra"
                else -> {
                    // Terminal inherent 'a' on consonant stems (e.g. 'sa', 'na', 'ka', 'pa', 'ma')
                    // Exclude root endings like -tra (Mohapatra), -hra (Mishra), -nda (Panda), -ida (Parida), -era (Behera), -ena (Jena)
                    if (t.length >= 5 && t.endsWith("a") && !t.endsWith("aa")) {
                        val prev = t[t.length - 2]
                        if (prev in "rnkdmpyst" &&
                            !t.endsWith("tra") && !t.endsWith("hra") && !t.endsWith("dra") &&
                            !t.endsWith("nda") && !t.endsWith("ida") && !t.endsWith("era") &&
                            !t.endsWith("ena") && !t.endsWith("uta")
                        ) {
                            t = t.substring(0, t.length - 1)
                        }
                    }
                }
            }
            t
        }

        return tokens.joinToString(" ")
    }

    /**
     * Compact string representation for compound given name comparison
     * (e.g. "Ghasi Ram" <-> "Ghasiram", "Purna Chandra" <-> "Purnachandra")
     */
    fun compactPhonetic(name: String): String {
        return normalizeForMatching(name).replace("[^a-z]".toRegex(), "")
    }
}
