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
        // The full স্বরবর্ণ: eleven vowels. The earlier table was missing ঋ.
        AlphabetSection(
            "Vowels (স্বরবর্ণ)",
            listOf(
                l("অ", "o"), l("আ", "a"), l("ই", "i"), l("ঈ", "ee"), l("উ", "u"),
                l("ঊ", "oo"), l("ঋ", "ri"), l("এ", "e"), l("ঐ", "oi"), l("ও", "o"),
                l("ঔ", "ou")
            )
        ),
        // The full ব্যঞ্জনবর্ণ. The earlier table stopped at হ and skipped the
        // nasals ঙ ঞ ণ entirely, so five of the eight rows a child is
        // taught were incomplete.
        AlphabetSection(
            "Consonants (ব্যঞ্জনবর্ণ)",
            listOf(
                l("ক", "ka"), l("খ", "kha"), l("গ", "ga"), l("ঘ", "gha"), l("ঙ", "unga"),
                l("চ", "cha"), l("ছ", "chha"), l("জ", "ja"), l("ঝ", "jha"), l("ঞ", "iyan"),
                l("ট", "ta"), l("ঠ", "tha"), l("ড", "da"), l("ঢ", "dha"), l("ণ", "na"),
                l("ত", "ta"), l("থ", "tha"), l("দ", "da"), l("ধ", "dha"), l("ন", "na"),
                l("প", "pa"), l("ফ", "pha"), l("ব", "ba"), l("ভ", "bha"), l("ম", "ma"),
                l("য", "ja"), l("র", "ra"), l("ল", "la"),
                l("শ", "sha"), l("ষ", "sha"), l("স", "sa"), l("হ", "ha")
            )
        ),
        // Taught with the alphabet in Bengali primers, and needed to read even
        // simple words — বড় ("big") is unreadable without ড়.
        AlphabetSection(
            "Extra letters & signs",
            listOf(
                l("ড়", "ra"), l("ঢ়", "rha"), l("য়", "ya"), l("ৎ", "t"),
                l("ং", "ng"), l("ঃ", "ha"), l("ঁ", "n")
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


// ═══════════════════════════════════════════════════════════════════════════
// ALPHABETS FOR THE LANGUAGES ADDED IN v1.6
//
// Every table below is written out in full rather than trimmed to "the common
// letters" — a half alphabet is what made the Bengali table useless, and a
// learner cannot tell which letters are missing.
// ═══════════════════════════════════════════════════════════════════════════

// ── Greek ──────────────────────────────────────────────────────────────────
private val greekScript = AlphabetScript(
    "Greek",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("Α α", "alpha"), l("Β β", "vita"), l("Γ γ", "gamma"), l("Δ δ", "delta"),
                l("Ε ε", "epsilon"), l("Ζ ζ", "zita"), l("Η η", "ita"), l("Θ θ", "thita"),
                l("Ι ι", "iota"), l("Κ κ", "kappa"), l("Λ λ", "lamda"), l("Μ μ", "mi"),
                l("Ν ν", "ni"), l("Ξ ξ", "ksi"), l("Ο ο", "omicron"), l("Π π", "pi"),
                l("Ρ ρ", "ro"), l("Σ σ", "sigma"), l("Τ τ", "taf"), l("Υ υ", "ipsilon"),
                l("Φ φ", "fi"), l("Χ χ", "chi"), l("Ψ ψ", "psi"), l("Ω ω", "omega")
            )
        )
    )
)

// ── Ukrainian (Cyrillic) ───────────────────────────────────────────────────
private val ukrainianScript = AlphabetScript(
    "Cyrillic",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("А а", "a"), l("Б б", "be"), l("В в", "ve"), l("Г г", "he"),
                l("Ґ ґ", "ge"), l("Д д", "de"), l("Е е", "e"), l("Є є", "ye"),
                l("Ж ж", "zhe"), l("З з", "ze"), l("И и", "y"), l("І і", "i"),
                l("Ї ї", "yi"), l("Й й", "y kratke"), l("К к", "ka"), l("Л л", "el"),
                l("М м", "em"), l("Н н", "en"), l("О о", "o"), l("П п", "pe"),
                l("Р р", "er"), l("С с", "es"), l("Т т", "te"), l("У у", "u"),
                l("Ф ф", "ef"), l("Х х", "kha"), l("Ц ц", "tse"), l("Ч ч", "che"),
                l("Ш ш", "sha"), l("Щ щ", "shcha"), l("Ь ь", "soft sign"),
                l("Ю ю", "yu"), l("Я я", "ya")
            )
        )
    )
)

// ── Bulgarian (Cyrillic) ───────────────────────────────────────────────────
private val bulgarianScript = AlphabetScript(
    "Cyrillic",
    listOf(
        AlphabetSection(
            "Letters",
            listOf(
                l("А а", "a"), l("Б б", "be"), l("В в", "ve"), l("Г г", "ge"),
                l("Д д", "de"), l("Е е", "e"), l("Ж ж", "zhe"), l("З з", "ze"),
                l("И и", "i"), l("Й й", "i kratko"), l("К к", "ka"), l("Л л", "el"),
                l("М м", "em"), l("Н н", "en"), l("О о", "o"), l("П п", "pe"),
                l("Р р", "er"), l("С с", "es"), l("Т т", "te"), l("У у", "u"),
                l("Ф ф", "ef"), l("Х х", "ha"), l("Ц ц", "tse"), l("Ч ч", "che"),
                l("Ш ш", "sha"), l("Щ щ", "shta"), l("Ъ ъ", "er golyam"),
                l("Ь ь", "er malak"), l("Ю ю", "yu"), l("Я я", "ya")
            )
        )
    )
)

// ── Hebrew ─────────────────────────────────────────────────────────────────
private val hebrewScript = AlphabetScript(
    "Hebrew",
    listOf(
        AlphabetSection(
            "Letters (written right to left)",
            listOf(
                l("א", "alef"), l("ב", "bet"), l("ג", "gimel"), l("ד", "dalet"),
                l("ה", "he"), l("ו", "vav"), l("ז", "zayin"), l("ח", "khet"),
                l("ט", "tet"), l("י", "yod"), l("כ", "kaf"), l("ל", "lamed"),
                l("מ", "mem"), l("נ", "nun"), l("ס", "samekh"), l("ע", "ayin"),
                l("פ", "pe"), l("צ", "tsadi"), l("ק", "qof"), l("ר", "resh"),
                l("ש", "shin"), l("ת", "tav")
            )
        ),
        AlphabetSection(
            "Final forms (used at the end of a word)",
            listOf(
                l("ך", "final kaf"), l("ם", "final mem"), l("ן", "final nun"),
                l("ף", "final pe"), l("ץ", "final tsadi")
            )
        )
    )
)

// ── Persian (Arabic script) ────────────────────────────────────────────────
private val persianScript = AlphabetScript(
    "Perso-Arabic",
    listOf(
        AlphabetSection(
            "Letters (written right to left)",
            listOf(
                l("ا", "alef"), l("ب", "be"), l("پ", "pe"), l("ت", "te"),
                l("ث", "se"), l("ج", "jim"), l("چ", "che"), l("ح", "he"),
                l("خ", "khe"), l("د", "dal"), l("ذ", "zal"), l("ر", "re"),
                l("ز", "ze"), l("ژ", "zhe"), l("س", "sin"), l("ش", "shin"),
                l("ص", "sad"), l("ض", "zad"), l("ط", "ta"), l("ظ", "za"),
                l("ع", "eyn"), l("غ", "gheyn"), l("ف", "fe"), l("ق", "ghaf"),
                l("ک", "kaf"), l("گ", "gaf"), l("ل", "lam"), l("م", "mim"),
                l("ن", "nun"), l("و", "vav"), l("ه", "he"), l("ی", "ye")
            )
        )
    )
)

// ── Thai ───────────────────────────────────────────────────────────────────
// All forty-four consonants in the order they are taught, each with the word
// Thai children learn it by, and the vowel signs that go around them.
private val thaiScript = AlphabetScript(
    "Thai",
    listOf(
        AlphabetSection(
            "Consonants (พยัญชนะ)",
            listOf(
                l("ก", "ko kai"), l("ข", "kho khai"), l("ฃ", "kho khuat"),
                l("ค", "kho khwai"), l("ฅ", "kho khon"), l("ฆ", "kho rakhang"),
                l("ง", "ngo ngu"), l("จ", "cho chan"), l("ฉ", "cho ching"),
                l("ช", "cho chang"), l("ซ", "so so"), l("ฌ", "cho choe"),
                l("ญ", "yo ying"), l("ฎ", "do chada"), l("ฏ", "to patak"),
                l("ฐ", "tho santhan"), l("ฑ", "tho nangmontho"), l("ฒ", "tho phuthao"),
                l("ณ", "no nen"), l("ด", "do dek"), l("ต", "to tao"),
                l("ถ", "tho thung"), l("ท", "tho thahan"), l("ธ", "tho thong"),
                l("น", "no nu"), l("บ", "bo baimai"), l("ป", "po pla"),
                l("ผ", "pho phueng"), l("ฝ", "fo fa"), l("พ", "pho phan"),
                l("ฟ", "fo fan"), l("ภ", "pho samphao"), l("ม", "mo ma"),
                l("ย", "yo yak"), l("ร", "ro ruea"), l("ล", "lo ling"),
                l("ว", "wo waen"), l("ศ", "so sala"), l("ษ", "so ruesi"),
                l("ส", "so suea"), l("ห", "ho hip"), l("ฬ", "lo chula"),
                l("อ", "o ang"), l("ฮ", "ho nokhuk")
            )
        ),
        AlphabetSection(
            "Vowel signs (สระ)",
            listOf(
                l("ะ", "sara a"), l("า", "sara aa"), l("ิ", "sara i"),
                l("ี", "sara ii"), l("ึ", "sara ue"), l("ื", "sara uee"),
                l("ุ", "sara u"), l("ู", "sara uu"), l("เ", "sara e"),
                l("แ", "sara ae"), l("โ", "sara o"), l("ใ", "sara ai maimuan"),
                l("ไ", "sara ai maimalai"), l("ำ", "sara am")
            )
        )
    )
)

// ── Latin-script languages added in v1.6 ───────────────────────────────────
private val turkishScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "ce", "Ç" to "çe", "D" to "de", "E" to "e",
        "F" to "fe", "G" to "ge", "Ğ" to "yumuşak ge", "H" to "he", "I" to "ı",
        "İ" to "i", "J" to "je", "K" to "ke", "L" to "le", "M" to "me", "N" to "ne",
        "O" to "o", "Ö" to "ö", "P" to "pe", "R" to "re", "S" to "se", "Ş" to "şe",
        "T" to "te", "U" to "u", "Ü" to "ü", "V" to "ve", "Y" to "ye", "Z" to "ze"
    )
)

private val polishScript = latinScript(
    listOf(
        "A" to "a", "Ą" to "ą", "B" to "be", "C" to "ce", "Ć" to "cie", "D" to "de",
        "E" to "e", "Ę" to "ę", "F" to "ef", "G" to "gie", "H" to "ha", "I" to "i",
        "J" to "jot", "K" to "ka", "L" to "el", "Ł" to "eł", "M" to "em", "N" to "en",
        "Ń" to "eń", "O" to "o", "Ó" to "o kreskowane", "P" to "pe", "R" to "er",
        "S" to "es", "Ś" to "eś", "T" to "te", "U" to "u", "W" to "wu", "Y" to "igrek",
        "Z" to "zet", "Ź" to "ziet", "Ż" to "żet"
    )
)

private val czechScript = latinScript(
    listOf(
        "A" to "á", "Á" to "dlouhé á", "B" to "bé", "C" to "cé", "Č" to "čé",
        "D" to "dé", "Ď" to "ďé", "E" to "é", "É" to "dlouhé é", "Ě" to "ije",
        "F" to "ef", "G" to "gé", "H" to "há", "Ch" to "chá", "I" to "í",
        "Í" to "dlouhé í", "J" to "jé", "K" to "ká", "L" to "el", "M" to "em",
        "N" to "en", "Ň" to "eň", "O" to "ó", "Ó" to "dlouhé ó", "P" to "pé",
        "Q" to "kvé", "R" to "er", "Ř" to "eř", "S" to "es", "Š" to "eš",
        "T" to "té", "Ť" to "ťé", "U" to "ú", "Ú" to "dlouhé ú", "Ů" to "ú kroužkované",
        "V" to "vé", "W" to "dvojité vé", "X" to "iks", "Y" to "ypsilon",
        "Ý" to "dlouhé ypsilon", "Z" to "zet", "Ž" to "žet"
    )
)

private val slovakScript = latinScript(
    listOf(
        "A" to "a", "Á" to "dlhé á", "Ä" to "široké e", "B" to "bé", "C" to "cé",
        "Č" to "čé", "D" to "dé", "Ď" to "ďé", "Dz" to "dzé", "Dž" to "džé",
        "E" to "é", "É" to "dlhé é", "F" to "ef", "G" to "gé", "H" to "há",
        "Ch" to "chá", "I" to "í", "Í" to "dlhé í", "J" to "jé", "K" to "ká",
        "L" to "el", "Ĺ" to "dlhé el", "Ľ" to "mäkké el", "M" to "em", "N" to "en",
        "Ň" to "eň", "O" to "ó", "Ó" to "dlhé ó", "Ô" to "ó vokáň", "P" to "pé",
        "Q" to "kvé", "R" to "er", "Ŕ" to "dlhé er", "S" to "es", "Š" to "eš",
        "T" to "té", "Ť" to "ťé", "U" to "ú", "Ú" to "dlhé ú", "V" to "vé",
        "W" to "dvojité vé", "X" to "iks", "Y" to "ypsilon", "Ý" to "dlhé ypsilon",
        "Z" to "zet", "Ž" to "žet"
    )
)

private val croatianScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "ce", "Č" to "če", "Ć" to "će", "D" to "de",
        "Dž" to "dže", "Đ" to "đe", "E" to "e", "F" to "ef", "G" to "ge", "H" to "ha",
        "I" to "i", "J" to "je", "K" to "ka", "L" to "el", "Lj" to "elj", "M" to "em",
        "N" to "en", "Nj" to "enj", "O" to "o", "P" to "pe", "R" to "er", "S" to "es",
        "Š" to "eš", "T" to "te", "U" to "u", "V" to "ve", "Z" to "ze", "Ž" to "že"
    )
)

private val romanianScript = latinScript(
    listOf(
        "A" to "a", "Ă" to "ă", "Â" to "â din a", "B" to "be", "C" to "ce", "D" to "de",
        "E" to "e", "F" to "ef", "G" to "ge", "H" to "haș", "I" to "i", "Î" to "î din i",
        "J" to "je", "K" to "ka", "L" to "el", "M" to "em", "N" to "en", "O" to "o",
        "P" to "pe", "Q" to "chiu", "R" to "er", "S" to "es", "Ș" to "șe", "T" to "te",
        "Ț" to "țe", "U" to "u", "V" to "ve", "W" to "dublu ve", "X" to "ics",
        "Y" to "igrec", "Z" to "zet"
    )
)

private val hungarianScript = latinScript(
    listOf(
        "A" to "a", "Á" to "á", "B" to "bé", "C" to "cé", "Cs" to "csé", "D" to "dé",
        "Dz" to "dzé", "Dzs" to "dzsé", "E" to "e", "É" to "é", "F" to "ef",
        "G" to "gé", "Gy" to "gyé", "H" to "há", "I" to "i", "Í" to "í", "J" to "jé",
        "K" to "ká", "L" to "el", "Ly" to "elipszilon", "M" to "em", "N" to "en",
        "Ny" to "eny", "O" to "o", "Ó" to "ó", "Ö" to "ö", "Ő" to "ő", "P" to "pé",
        "R" to "er", "S" to "es", "Sz" to "esz", "T" to "té", "Ty" to "tyé",
        "U" to "u", "Ú" to "ú", "Ü" to "ü", "Ű" to "ű", "V" to "vé", "Z" to "zé",
        "Zs" to "zsé"
    )
)

private val dutchScript = latinScript(
    listOf(
        "A" to "aa", "B" to "bee", "C" to "see", "D" to "dee", "E" to "ee",
        "F" to "ef", "G" to "gee", "H" to "haa", "I" to "ie", "J" to "jee",
        "K" to "kaa", "L" to "el", "M" to "em", "N" to "en", "O" to "oo",
        "P" to "pee", "Q" to "kuu", "R" to "er", "S" to "es", "T" to "tee",
        "U" to "uu", "V" to "vee", "W" to "wee", "X" to "iks", "Y" to "ei",
        "Z" to "zet"
    )
)

private val swedishScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "se", "D" to "de", "E" to "e", "F" to "ef",
        "G" to "ge", "H" to "hå", "I" to "i", "J" to "ji", "K" to "kå", "L" to "el",
        "M" to "em", "N" to "en", "O" to "o", "P" to "pe", "Q" to "ku", "R" to "är",
        "S" to "es", "T" to "te", "U" to "u", "V" to "ve", "W" to "dubbel-ve",
        "X" to "eks", "Y" to "y", "Z" to "säta", "Å" to "å", "Ä" to "ä", "Ö" to "ö"
    )
)

private val danishScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "se", "D" to "de", "E" to "e", "F" to "ef",
        "G" to "ge", "H" to "hå", "I" to "i", "J" to "jåd", "K" to "kå", "L" to "el",
        "M" to "em", "N" to "en", "O" to "o", "P" to "pe", "Q" to "ku", "R" to "ær",
        "S" to "es", "T" to "te", "U" to "u", "V" to "ve", "W" to "dobbelt-ve",
        "X" to "eks", "Y" to "y", "Z" to "set", "Æ" to "æ", "Ø" to "ø", "Å" to "å"
    )
)

private val norwegianScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "se", "D" to "de", "E" to "e", "F" to "eff",
        "G" to "ge", "H" to "hå", "I" to "i", "J" to "je", "K" to "kå", "L" to "ell",
        "M" to "emm", "N" to "enn", "O" to "o", "P" to "pe", "Q" to "ku", "R" to "ærr",
        "S" to "ess", "T" to "te", "U" to "u", "V" to "ve", "W" to "dobbelt-ve",
        "X" to "eks", "Y" to "y", "Z" to "sett", "Æ" to "æ", "Ø" to "ø", "Å" to "å"
    )
)

private val finnishScript = latinScript(
    listOf(
        "A" to "aa", "B" to "bee", "C" to "see", "D" to "dee", "E" to "ee",
        "F" to "äf", "G" to "gee", "H" to "hoo", "I" to "ii", "J" to "jii",
        "K" to "koo", "L" to "äl", "M" to "äm", "N" to "än", "O" to "oo",
        "P" to "pee", "Q" to "kuu", "R" to "är", "S" to "äs", "T" to "tee",
        "U" to "uu", "V" to "vee", "W" to "kaksois-vee", "X" to "äks", "Y" to "yy",
        "Z" to "tset", "Å" to "ruotsalainen oo", "Ä" to "ää", "Ö" to "öö"
    )
)

private val vietnameseScript = latinScript(
    listOf(
        "A" to "a", "Ă" to "á", "Â" to "ớ", "B" to "bê", "C" to "xê", "D" to "dê",
        "Đ" to "đê", "E" to "e", "Ê" to "ê", "G" to "giê", "H" to "hát", "I" to "i ngắn",
        "K" to "ca", "L" to "e-lờ", "M" to "em-mờ", "N" to "en-nờ", "O" to "o",
        "Ô" to "ô", "Ơ" to "ơ", "P" to "pê", "Q" to "cu", "R" to "e-rờ", "S" to "ét-sì",
        "T" to "tê", "U" to "u", "Ư" to "ư", "V" to "vê", "X" to "ích-xì", "Y" to "i dài"
    )
)

private val indonesianScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "ce", "D" to "de", "E" to "e", "F" to "ef",
        "G" to "ge", "H" to "ha", "I" to "i", "J" to "je", "K" to "ka", "L" to "el",
        "M" to "em", "N" to "en", "O" to "o", "P" to "pe", "Q" to "ki", "R" to "er",
        "S" to "es", "T" to "te", "U" to "u", "V" to "ve", "W" to "we", "X" to "eks",
        "Y" to "ye", "Z" to "zet"
    )
)

private val malayScript = latinScript(
    listOf(
        "A" to "a", "B" to "bi", "C" to "si", "D" to "di", "E" to "i", "F" to "ef",
        "G" to "ji", "H" to "ech", "I" to "ai", "J" to "je", "K" to "ke", "L" to "el",
        "M" to "em", "N" to "en", "O" to "o", "P" to "pi", "Q" to "kiu", "R" to "ar",
        "S" to "es", "T" to "ti", "U" to "yu", "V" to "vi", "W" to "dabliu",
        "X" to "eks", "Y" to "wai", "Z" to "zet"
    )
)

private val filipinoScript = latinScript(
    listOf(
        "A" to "ey", "B" to "bi", "C" to "si", "D" to "di", "E" to "i", "F" to "ef",
        "G" to "dyi", "H" to "eyts", "I" to "ay", "J" to "dyey", "K" to "key",
        "L" to "el", "M" to "em", "N" to "en", "Ñ" to "enye", "Ng" to "en-dyi",
        "O" to "o", "P" to "pi", "Q" to "kyu", "R" to "ar", "S" to "es", "T" to "ti",
        "U" to "yu", "V" to "vi", "W" to "dobolyu", "X" to "eks", "Y" to "way",
        "Z" to "zi"
    )
)

private val swahiliScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "Ch" to "che", "D" to "de", "E" to "e", "F" to "fe",
        "G" to "ge", "H" to "he", "I" to "i", "J" to "je", "K" to "ke", "L" to "le",
        "M" to "me", "N" to "ne", "Ng'" to "nge", "Ny" to "nye", "O" to "o",
        "P" to "pe", "R" to "re", "S" to "se", "Sh" to "she", "T" to "te", "U" to "u",
        "V" to "ve", "W" to "we", "Y" to "ye", "Z" to "ze"
    )
)

private val afrikaansScript = latinScript(
    listOf(
        "A" to "aa", "B" to "bee", "C" to "see", "D" to "dee", "E" to "ee",
        "F" to "ef", "G" to "gee", "H" to "haa", "I" to "ie", "J" to "jee",
        "K" to "kaa", "L" to "el", "M" to "em", "N" to "en", "O" to "oo",
        "P" to "pee", "Q" to "kuu", "R" to "er", "S" to "es", "T" to "tee",
        "U" to "uu", "V" to "fee", "W" to "vee", "X" to "eks", "Y" to "ei",
        "Z" to "zet"
    )
)

private val catalanScript = latinScript(
    listOf(
        "A" to "a", "B" to "be", "C" to "ce", "Ç" to "ce trencada", "D" to "de",
        "E" to "e", "F" to "efa", "G" to "ge", "H" to "hac", "I" to "i", "J" to "jota",
        "K" to "ca", "L" to "ela", "M" to "ema", "N" to "ena", "O" to "o", "P" to "pe",
        "Q" to "cu", "R" to "erra", "S" to "essa", "T" to "te", "U" to "u", "V" to "ve",
        "W" to "ve doble", "X" to "ics", "Y" to "i grega", "Z" to "zeta"
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
    "Greek" -> greekScript
    "Ukrainian" -> ukrainianScript
    "Bulgarian" -> bulgarianScript
    "Hebrew" -> hebrewScript
    "Persian" -> persianScript
    "Thai" -> thaiScript
    "Turkish" -> turkishScript
    "Polish" -> polishScript
    "Czech" -> czechScript
    "Slovak" -> slovakScript
    "Croatian" -> croatianScript
    "Romanian" -> romanianScript
    "Hungarian" -> hungarianScript
    "Dutch" -> dutchScript
    "Swedish" -> swedishScript
    "Danish" -> danishScript
    "Norwegian" -> norwegianScript
    "Finnish" -> finnishScript
    "Vietnamese" -> vietnameseScript
    "Indonesian" -> indonesianScript
    "Malay" -> malayScript
    "Filipino" -> filipinoScript
    "Swahili" -> swahiliScript
    "Afrikaans" -> afrikaansScript
    "Catalan" -> catalanScript
    else -> null
}
