/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                         SPEECHNOVA v1.5                                  ║
 * ║              LEARN screen — alphabet & pronunciation data                ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Curated alphabet / syllabary data for the LEARN screen. Each letter carries
 * the native glyph plus a simple, read-it-aloud romanization (not academic
 * IAST). Tap-to-hear uses the phone's TextToSpeech in the target language, so
 * the audio is always authoritative even where the romanization is a friendly
 * approximation. Scripts we don't ship a curated table for fall back to a
 * clear "not available yet" note in the UI rather than showing wrong guidance.
 */

package com.parashmani.speechnova

/** One letter/syllable card in the LEARN screen. */
data class AlphabetLetter(
    val glyph: String,   // what we show, and what TTS speaks
    val roman: String    // friendly "say it like this" romanization
)

/** A titled group of letters (e.g. "Vowels", "Consonants"). */
data class AlphabetSection(
    val title: String,
    val letters: List<AlphabetLetter>
)

/** A whole learnable script for one language. */
data class AlphabetScript(
    val scriptName: String,      // e.g. "Devanagari", "Cyrillic"
    val sections: List<AlphabetSection>
)

private fun l(glyph: String, roman: String) = AlphabetLetter(glyph, roman)

// ── Devanagari (Hindi & Marathi) ───────────────────────────────────────────
private val devanagariVowelLetters = listOf(
    l("अ", "a"), l("आ", "aa"), l("इ", "i"), l("ई", "ee"), l("उ", "u"),
    l("ऊ", "oo"), l("ऋ", "ri"), l("ए", "e"), l("ऐ", "ai"), l("ओ", "o"),
    l("औ", "au"), l("अं", "an"), l("अः", "ah")
)
private val devanagariConsonantLetters = listOf(
    l("क", "ka"), l("ख", "kha"), l("ग", "ga"), l("घ", "gha"), l("ङ", "nga"),
    l("च", "cha"), l("छ", "chha"), l("ज", "ja"), l("झ", "jha"), l("ञ", "nya"),
    l("ट", "ta"), l("ठ", "tha"), l("ड", "da"), l("ढ", "dha"), l("ण", "na"),
    l("त", "ta"), l("थ", "tha"), l("द", "da"), l("ध", "dha"), l("न", "na"),
    l("प", "pa"), l("फ", "pha"), l("ब", "ba"), l("भ", "bha"), l("म", "ma"),
    l("य", "ya"), l("र", "ra"), l("ल", "la"), l("व", "va"), l("श", "sha"),
    l("ष", "sha"), l("स", "sa"), l("ह", "ha"), l("क्ष", "ksha"), l("त्र", "tra"),
    l("ज्ञ", "gya")
)

private val hindiScript = AlphabetScript(
    "Devanagari",
    listOf(
        AlphabetSection("Vowels (स्वर)", devanagariVowelLetters),
        AlphabetSection("Consonants (व्यंजन)", devanagariConsonantLetters)
    )
)

private val marathiScript = AlphabetScript(
    "Devanagari",
    listOf(
        AlphabetSection("Vowels (स्वर)", devanagariVowelLetters),
        // Marathi adds the retroflex ळ to the Devanagari consonant set.
        AlphabetSection("Consonants (व्यंजन)", devanagariConsonantLetters + l("ळ", "la"))
    )
)

// ── Latin-script languages ─────────────────────────────────────────────────
private fun latinScript(pairs: List<Pair<String, String>>) = AlphabetScript(
    "Latin",
    listOf(AlphabetSection("Letters", pairs.map { l(it.first, it.second) }))
)

private val englishScript = latinScript(
    listOf(
        "A" to "ay", "B" to "bee", "C" to "see", "D" to "dee", "E" to "ee",
        "F" to "ef", "G" to "jee", "H" to "aych", "I" to "eye", "J" to "jay",
        "K" to "kay", "L" to "el", "M" to "em", "N" to "en", "O" to "oh",
        "P" to "pee", "Q" to "cue", "R" to "ar", "S" to "ess", "T" to "tee",
        "U" to "you", "V" to "vee", "W" to "double-u", "X" to "ex", "Y" to "why",
        "Z" to "zee"
    )
)

private val spanishScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "ce", "D" to "de", "E" to "e",
        "F" to "efe", "G" to "ge", "H" to "hache", "I" to "i", "J" to "jota",
        "K" to "ka", "L" to "ele", "M" to "eme", "N" to "ene", "Ñ" to "eñe",
        "O" to "o", "P" to "pe", "Q" to "cu", "R" to "erre", "S" to "ese",
        "T" to "te", "U" to "u", "V" to "uve", "W" to "uve doble", "X" to "equis",
        "Y" to "ye", "Z" to "zeta"
    )
)

private val frenchScript = latinScript(
    listOf(
        "A" to "a", "B" to "bé", "C" to "cé", "D" to "dé", "E" to "e",
        "F" to "effe", "G" to "gé", "H" to "ache", "I" to "i", "J" to "ji",
        "K" to "ka", "L" to "elle", "M" to "emme", "N" to "enne", "O" to "o",
        "P" to "pé", "Q" to "ku", "R" to "erre", "S" to "esse", "T" to "té",
        "U" to "u", "V" to "vé", "W" to "double-vé", "X" to "ixe", "Y" to "i grec",
        "Z" to "zède"
    )
)

private val germanScript = latinScript(
    listOf(
        "A" to "ah", "B" to "beh", "C" to "tseh", "D" to "deh", "E" to "eh",
        "F" to "eff", "G" to "geh", "H" to "hah", "I" to "ih", "J" to "yot",
        "K" to "kah", "L" to "ell", "M" to "emm", "N" to "enn", "O" to "oh",
        "P" to "peh", "Q" to "kuh", "R" to "err", "S" to "ess", "T" to "teh",
        "U" to "uh", "V" to "fau", "W" to "veh", "X" to "iks", "Y" to "üpsilon",
        "Z" to "tsett", "Ä" to "ae", "Ö" to "oe", "Ü" to "ue", "ß" to "ess-tsett"
    )
)

private val italianScript = latinScript(
    listOf(
        "A" to "a", "B" to "bi", "C" to "ci", "D" to "di", "E" to "e",
        "F" to "effe", "G" to "gi", "H" to "acca", "I" to "i", "L" to "elle",
        "M" to "emme", "N" to "enne", "O" to "o", "P" to "pi", "Q" to "cu",
        "R" to "erre", "S" to "esse", "T" to "ti", "U" to "u", "V" to "vu",
        "Z" to "zeta"
    )
)

private val portugueseScript = latinScript(
    listOf(
        "A" to "á", "B" to "bê", "C" to "cê", "D" to "dê", "E" to "é",
        "F" to "efe", "G" to "gê", "H" to "agá", "I" to "i", "J" to "jota",
        "K" to "capa", "L" to "ele", "M" to "eme", "N" to "ene", "O" to "ó",
        "P" to "pê", "Q" to "quê", "R" to "erre", "S" to "esse", "T" to "tê",
        "U" to "u", "V" to "vê", "W" to "dáblio", "X" to "xis", "Y" to "ípsilon",
        "Z" to "zê"
    )
)

// ── Russian (Cyrillic) ─────────────────────────────────────────────────────
private val russianScript = AlphabetScript(
    "Cyrillic",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("А", "a"), l("Б", "b"), l("В", "v"), l("Г", "g"), l("Д", "d"),
                l("Е", "ye"), l("Ё", "yo"), l("Ж", "zh"), l("З", "z"), l("И", "ee"),
                l("Й", "y"), l("К", "k"), l("Л", "l"), l("М", "m"), l("Н", "n"),
                l("О", "o"), l("П", "p"), l("Р", "r"), l("С", "s"), l("Т", "t"),
                l("У", "oo"), l("Ф", "f"), l("Х", "kh"), l("Ц", "ts"), l("Ч", "ch"),
                l("Ш", "sh"), l("Щ", "shch"), l("Ъ", "hard sign"), l("Ы", "y"),
                l("Ь", "soft sign"), l("Э", "e"), l("Ю", "yu"), l("Я", "ya")
            )
        )
    )
)

// ── Arabic ─────────────────────────────────────────────────────────────────
private val arabicScript = AlphabetScript(
    "Arabic",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("ا", "alif (a)"), l("ب", "ba (b)"), l("ت", "ta (t)"), l("ث", "tha (th)"),
                l("ج", "jim (j)"), l("ح", "ha (h)"), l("خ", "kha (kh)"), l("د", "dal (d)"),
                l("ذ", "dhal (dh)"), l("ر", "ra (r)"), l("ز", "zay (z)"), l("س", "sin (s)"),
                l("ش", "shin (sh)"), l("ص", "sad (s)"), l("ض", "dad (d)"), l("ط", "ta (t)"),
                l("ظ", "za (z)"), l("ع", "ain (a)"), l("غ", "ghayn (gh)"), l("ف", "fa (f)"),
                l("ق", "qaf (q)"), l("ك", "kaf (k)"), l("ل", "lam (l)"), l("م", "mim (m)"),
                l("ن", "nun (n)"), l("ه", "ha (h)"), l("و", "waw (w)"), l("ي", "ya (y)")
            )
        )
    )
)

// ── Urdu ───────────────────────────────────────────────────────────────────
private val urduScript = AlphabetScript(
    "Nastaliq",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("ا", "alif (a)"), l("ب", "be (b)"), l("پ", "pe (p)"), l("ت", "te (t)"),
                l("ٹ", "tte (t)"), l("ث", "se (s)"), l("ج", "jim (j)"), l("چ", "che (ch)"),
                l("ح", "bari he (h)"), l("خ", "khe (kh)"), l("د", "dal (d)"), l("ڈ", "ddal (d)"),
                l("ذ", "zal (z)"), l("ر", "re (r)"), l("ڑ", "rre (r)"), l("ز", "ze (z)"),
                l("ژ", "zhe (zh)"), l("س", "sin (s)"), l("ش", "shin (sh)"), l("ص", "suad (s)"),
                l("ض", "zuad (z)"), l("ط", "toe (t)"), l("ظ", "zoe (z)"), l("ع", "ain (a)"),
                l("غ", "ghain (gh)"), l("ف", "fe (f)"), l("ق", "qaf (q)"), l("ک", "kaf (k)"),
                l("گ", "gaf (g)"), l("ل", "lam (l)"), l("م", "mim (m)"), l("ن", "nun (n)"),
                l("و", "wao (w)"), l("ہ", "he (h)"), l("ی", "ye (y)")
            )
        )
    )
)

// ── Japanese (Hiragana gojūon) ─────────────────────────────────────────────
private val japaneseScript = AlphabetScript(
    "Hiragana",
    listOf(
        AlphabetSection(
            "Hiragana",
            listOf(
                l("あ", "a"), l("い", "i"), l("う", "u"), l("え", "e"), l("お", "o"),
                l("か", "ka"), l("き", "ki"), l("く", "ku"), l("け", "ke"), l("こ", "ko"),
                l("さ", "sa"), l("し", "shi"), l("す", "su"), l("せ", "se"), l("そ", "so"),
                l("た", "ta"), l("ち", "chi"), l("つ", "tsu"), l("て", "te"), l("と", "to"),
                l("な", "na"), l("に", "ni"), l("ぬ", "nu"), l("ね", "ne"), l("の", "no"),
                l("は", "ha"), l("ひ", "hi"), l("ふ", "fu"), l("へ", "he"), l("ほ", "ho"),
                l("ま", "ma"), l("み", "mi"), l("む", "mu"), l("め", "me"), l("も", "mo"),
                l("や", "ya"), l("ゆ", "yu"), l("よ", "yo"),
                l("ら", "ra"), l("り", "ri"), l("る", "ru"), l("れ", "re"), l("ろ", "ro"),
                l("わ", "wa"), l("を", "wo"), l("ん", "n")
            )
        )
    )
)

// ── Korean (Hangul jamo) ───────────────────────────────────────────────────
private val koreanScript = AlphabetScript(
    "Hangul",
    listOf(
        AlphabetSection(
            "Consonants",
            listOf(
                l("ㄱ", "g / k"), l("ㄴ", "n"), l("ㄷ", "d / t"), l("ㄹ", "r / l"),
                l("ㅁ", "m"), l("ㅂ", "b / p"), l("ㅅ", "s"), l("ㅇ", "ng"),
                l("ㅈ", "j"), l("ㅊ", "ch"), l("ㅋ", "k"), l("ㅌ", "t"),
                l("ㅍ", "p"), l("ㅎ", "h")
            )
        ),
        AlphabetSection(
            "Vowels",
            listOf(
                l("ㅏ", "a"), l("ㅑ", "ya"), l("ㅓ", "eo"), l("ㅕ", "yeo"), l("ㅗ", "o"),
                l("ㅛ", "yo"), l("ㅜ", "u"), l("ㅠ", "yu"), l("ㅡ", "eu"), l("ㅣ", "i")
            )
        )
    )
)

// ── Chinese (Pinyin — Mandarin has no alphabet, so we teach the sounds) ─────
private val chineseScript = AlphabetScript(
    "Pinyin",
    listOf(
        AlphabetSection(
            "Initials (consonant sounds)",
            listOf(
                l("b", "b"), l("p", "p"), l("m", "m"), l("f", "f"), l("d", "d"),
                l("t", "t"), l("n", "n"), l("l", "l"), l("g", "g"), l("k", "k"),
                l("h", "h"), l("j", "jee"), l("q", "chee"), l("x", "shee"),
                l("zh", "j"), l("ch", "ch"), l("sh", "sh"), l("r", "r"),
                l("z", "dz"), l("c", "ts"), l("s", "s")
            )
        ),
        AlphabetSection(
            "Finals (vowel sounds)",
            listOf(
                l("a", "ah"), l("o", "aw"), l("e", "uh"), l("i", "ee"), l("u", "oo"),
                l("ü", "yu"), l("ai", "eye"), l("ei", "ay"), l("ao", "ow"), l("ou", "oh"),
                l("an", "ahn"), l("en", "un"), l("ang", "ahng"), l("eng", "ung"),
                l("er", "ar")
            )
        )
    )
)

// ── Bengali ────────────────────────────────────────────────────────────────
private val bengaliScript = AlphabetScript(
    "Bengali",
    listOf(
        AlphabetSection(
            "Vowels",
            listOf(
                l("অ", "o"), l("আ", "a"), l("ই", "i"), l("ঈ", "ee"), l("উ", "u"),
                l("ঊ", "oo"), l("এ", "e"), l("ঐ", "oi"), l("ও", "o"), l("ঔ", "ou")
            )
        ),
        AlphabetSection(
            "Consonants",
            listOf(
                l("ক", "ka"), l("খ", "kha"), l("গ", "ga"), l("ঘ", "gha"), l("চ", "cha"),
                l("ছ", "chha"), l("জ", "ja"), l("ঝ", "jha"), l("ট", "ta"), l("ঠ", "tha"),
                l("ড", "da"), l("ঢ", "dha"), l("ত", "ta"), l("থ", "tha"), l("দ", "da"),
                l("ধ", "dha"), l("ন", "na"), l("প", "pa"), l("ফ", "pha"), l("ব", "ba"),
                l("ভ", "bha"), l("ম", "ma"), l("য", "ja"), l("র", "ra"), l("ল", "la"),
                l("শ", "sha"), l("ষ", "sha"), l("স", "sa"), l("হ", "ha")
            )
        )
    )
)

// ── Tamil ──────────────────────────────────────────────────────────────────
private val tamilScript = AlphabetScript(
    "Tamil",
    listOf(
        AlphabetSection(
            "Vowels (உயிர்)",
            listOf(
                l("அ", "a"), l("ஆ", "aa"), l("இ", "i"), l("ஈ", "ee"), l("உ", "u"),
                l("ஊ", "oo"), l("எ", "e"), l("ஏ", "ae"), l("ஐ", "ai"), l("ஒ", "o"),
                l("ஓ", "oo"), l("ஔ", "au")
            )
        ),
        AlphabetSection(
            "Consonants (மெய்)",
            listOf(
                l("க", "ka"), l("ங", "nga"), l("ச", "cha"), l("ஞ", "nya"), l("ட", "ta"),
                l("ண", "na"), l("த", "tha"), l("ந", "na"), l("ப", "pa"), l("ம", "ma"),
                l("ய", "ya"), l("ர", "ra"), l("ல", "la"), l("வ", "va"), l("ழ", "zha"),
                l("ள", "la"), l("ற", "ra"), l("ன", "na")
            )
        )
    )
)

// ── Telugu ─────────────────────────────────────────────────────────────────
private val teluguScript = AlphabetScript(
    "Telugu",
    listOf(
        AlphabetSection(
            "Vowels",
            listOf(
                l("అ", "a"), l("ఆ", "aa"), l("ఇ", "i"), l("ఈ", "ee"), l("ఉ", "u"),
                l("ఊ", "oo"), l("ఎ", "e"), l("ఏ", "ae"), l("ఐ", "ai"), l("ఒ", "o"),
                l("ఓ", "oo"), l("ఔ", "au")
            )
        ),
        AlphabetSection(
            "Consonants",
            listOf(
                l("క", "ka"), l("ఖ", "kha"), l("గ", "ga"), l("ఘ", "gha"), l("చ", "cha"),
                l("ఛ", "chha"), l("జ", "ja"), l("ట", "ta"), l("డ", "da"), l("ణ", "na"),
                l("త", "ta"), l("థ", "tha"), l("ద", "da"), l("న", "na"), l("ప", "pa"),
                l("ఫ", "pha"), l("బ", "ba"), l("భ", "bha"), l("మ", "ma"), l("య", "ya"),
                l("ర", "ra"), l("ల", "la"), l("వ", "va"), l("శ", "sha"), l("ష", "sha"),
                l("స", "sa"), l("హ", "ha")
            )
        )
    )
)

// ── Gujarati ───────────────────────────────────────────────────────────────
private val gujaratiScript = AlphabetScript(
    "Gujarati",
    listOf(
        AlphabetSection(
            "Vowels",
            listOf(
                l("અ", "a"), l("આ", "aa"), l("ઇ", "i"), l("ઈ", "ee"), l("ઉ", "u"),
                l("ઊ", "oo"), l("એ", "e"), l("ઐ", "ai"), l("ઓ", "o"), l("ઔ", "au")
            )
        ),
        AlphabetSection(
            "Consonants",
            listOf(
                l("ક", "ka"), l("ખ", "kha"), l("ગ", "ga"), l("ઘ", "gha"), l("ચ", "cha"),
                l("છ", "chha"), l("જ", "ja"), l("ઝ", "jha"), l("ટ", "ta"), l("ઠ", "tha"),
                l("ડ", "da"), l("ઢ", "dha"), l("ણ", "na"), l("ત", "ta"), l("થ", "tha"),
                l("દ", "da"), l("ધ", "dha"), l("ન", "na"), l("પ", "pa"), l("ફ", "pha"),
                l("બ", "ba"), l("ભ", "bha"), l("મ", "ma"), l("ય", "ya"), l("ર", "ra"),
                l("લ", "la"), l("વ", "va"), l("શ", "sha"), l("ષ", "sha"), l("સ", "sa"),
                l("હ", "ha")
            )
        )
    )
)

// ── Kannada ────────────────────────────────────────────────────────────────
private val kannadaScript = AlphabetScript(
    "Kannada",
    listOf(
        AlphabetSection(
            "Vowels",
            listOf(
                l("ಅ", "a"), l("ಆ", "aa"), l("ಇ", "i"), l("ಈ", "ee"), l("ಉ", "u"),
                l("ಊ", "oo"), l("ಎ", "e"), l("ಏ", "ae"), l("ಐ", "ai"), l("ಒ", "o"),
                l("ಓ", "oo"), l("ಔ", "au")
            )
        ),
        AlphabetSection(
            "Consonants",
            listOf(
                l("ಕ", "ka"), l("ಖ", "kha"), l("ಗ", "ga"), l("ಘ", "gha"), l("ಚ", "cha"),
                l("ಛ", "chha"), l("ಜ", "ja"), l("ಟ", "ta"), l("ಠ", "tha"), l("ಡ", "da"),
                l("ಢ", "dha"), l("ಣ", "na"), l("ತ", "ta"), l("ಥ", "tha"), l("ದ", "da"),
                l("ಧ", "dha"), l("ನ", "na"), l("ಪ", "pa"), l("ಫ", "pha"), l("ಬ", "ba"),
                l("ಭ", "bha"), l("ಮ", "ma"), l("ಯ", "ya"), l("ರ", "ra"), l("ಲ", "la"),
                l("ವ", "va"), l("ಶ", "sha"), l("ಷ", "sha"), l("ಸ", "sa"), l("ಹ", "ha"),
                l("ಳ", "la")
            )
        )
    )
)

/**
 * Returns the curated alphabet for a language, or null if we don't yet ship a
 * hand-checked table for it (the LEARN screen then shows an honest note).
 */
fun alphabetScriptFor(lang: String): AlphabetScript? = when (lang) {
    "Hindi" -> hindiScript
    "Marathi" -> marathiScript
    "English" -> englishScript
    "Spanish" -> spanishScript
    "French" -> frenchScript
    "German" -> germanScript
    "Italian" -> italianScript
    "Portuguese" -> portugueseScript
    "Russian" -> russianScript
    "Arabic" -> arabicScript
    "Urdu" -> urduScript
    "Japanese" -> japaneseScript
    "Korean" -> koreanScript
    "Chinese" -> chineseScript
    "Bengali" -> bengaliScript
    "Tamil" -> tamilScript
    "Telugu" -> teluguScript
    "Gujarati" -> gujaratiScript
    "Kannada" -> kannadaScript
    else -> null
}
