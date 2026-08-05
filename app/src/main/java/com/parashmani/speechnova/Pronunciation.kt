/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — PRONUNCIATION COACHING                                     ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

/**
 * Turns "wrong" into "here is what was wrong".
 *
 * The practice screen used to compare the whole word and say right or wrong.
 * That tells a learner nothing they can act on. A learner who says कल for काल
 * needs to be told they shortened the आ, not that they failed.
 *
 * This works at the level of the *akshara* — the syllable cluster that Indic
 * scripts are actually built from and taught in — rather than the Unicode
 * character, because a character-level diff produces nonsense for these
 * scripts. काल is three characters but two aksharas (का + ल), and it is the
 * aksharas a learner hears themselves saying.
 *
 * What this deliberately is *not*: phoneme-level scoring of the audio. That
 * needs an acoustic model nobody ships on-device for Indic languages. This
 * compares what the recognizer *heard* against what was asked for, which means
 * it can say "you shortened this vowel" honestly, and cannot say "your ट was
 * 40% retroflex". The first is useful. The second would be a lie.
 */

// ── Script structure ───────────────────────────────────────────────────────

/**
 * The two scripts this understands, described by the code points that matter
 * for splitting a word into aksharas.
 *
 * Devanagari and Bengali are structurally the same script with different
 * shapes: the same five vargas in the same order, the same virama mechanism,
 * the same matra system. Everything below exploits that, which is why adding
 * Gujarati or Kannada later is a table, not new logic.
 */
private data class IndicScript(
    val range: IntRange,
    val virama: Char,
    val nukta: Char,
    /** The 25 varga consonants, in the order every Indic primer teaches them. */
    val vargaConsonants: List<Char>,
    /** Independent vowels that pair as short/long. */
    val shortLongVowels: List<Pair<Char, Char>>,
    /** Matras (dependent vowel signs) that pair as short/long. */
    val shortLongMatras: List<Pair<Char, Char>>,
    val sibilants: List<Char>
)

private val devanagari = IndicScript(
    range = 0x0900..0x097F,
    virama = '्',
    nukta = '़',
    vargaConsonants = listOf(
        'क', 'ख', 'ग', 'घ', 'ङ',
        'च', 'छ', 'ज', 'झ', 'ञ',
        'ट', 'ठ', 'ड', 'ढ', 'ण',
        'त', 'थ', 'द', 'ध', 'न',
        'प', 'फ', 'ब', 'भ', 'म'
    ),
    shortLongVowels = listOf('अ' to 'आ', 'इ' to 'ई', 'उ' to 'ऊ'),
    shortLongMatras = listOf('ि' to 'ी', 'ु' to 'ू'),
    sibilants = listOf('श', 'ष', 'स')
)

private val bengali = IndicScript(
    range = 0x0980..0x09FF,
    virama = '্',
    nukta = '়',
    vargaConsonants = listOf(
        'ক', 'খ', 'গ', 'ঘ', 'ঙ',
        'চ', 'ছ', 'জ', 'ঝ', 'ঞ',
        'ট', 'ঠ', 'ড', 'ঢ', 'ণ',
        'ত', 'থ', 'দ', 'ধ', 'ন',
        'প', 'ফ', 'ব', 'ভ', 'ম'
    ),
    shortLongVowels = listOf('অ' to 'আ', 'ই' to 'ঈ', 'উ' to 'ঊ'),
    shortLongMatras = listOf('ি' to 'ী', 'ু' to 'ূ'),
    sibilants = listOf('শ', 'ষ', 'স')
)

private fun scriptFor(lang: String): IndicScript? = when (lang) {
    "Hindi", "Marathi" -> devanagari
    "Bengali" -> bengali
    else -> null
}

/** The five places of articulation, in varga order. */
private val placeNames = listOf(
    "from the throat", "from the middle of the mouth", "with the tongue curled back",
    "with the tongue on the teeth", "with the lips"
)
private val placeShort = listOf("throat", "palate", "retroflex", "dental", "lips")

// ── Splitting a word into aksharas ─────────────────────────────────────────

/**
 * Splits an Indic word into the clusters it is read and taught in.
 *
 * An akshara is a base letter plus everything that hangs off it: a nukta, any
 * matra, anusvara/visarga/chandrabindu, and — where a virama joins them — the
 * following consonant, so a conjunct like क्ष stays one unit rather than
 * becoming three meaningless pieces.
 */
fun splitAksharas(word: String, lang: String): List<String> {
    val script = scriptFor(lang) ?: return word.trim().split(" ").filter { it.isNotBlank() }
    val text = word.trim()
    if (text.isEmpty()) return emptyList()

    fun isInScript(c: Char) = c.code in script.range

    // Combining marks attach to whatever came before them.
    fun isCombining(c: Char): Boolean {
        if (!isInScript(c)) return false
        if (c == script.nukta) return true
        val offset = c.code - script.range.first
        // Matras and the anusvara/visarga/candrabindu signs, by their fixed
        // offsets within an Indic block.
        return offset in 0x01..0x03 || offset in 0x3E..0x4C || offset in 0x62..0x63
    }

    val out = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (!isInScript(c)) {
            // Spaces, punctuation, stray Latin: flush and pass through.
            if (current.isNotEmpty()) { out.add(current.toString()); current.clear() }
            if (!c.isWhitespace()) out.add(c.toString())
            i++
            continue
        }
        if (current.isEmpty()) {
            current.append(c); i++
            continue
        }
        when {
            isCombining(c) -> { current.append(c); i++ }
            // A virama binds this akshara to the consonant that follows.
            c == script.virama -> {
                current.append(c); i++
                if (i < text.length && isInScript(text[i])) { current.append(text[i]); i++ }
            }
            else -> {
                out.add(current.toString()); current.clear()
                current.append(c); i++
            }
        }
    }
    if (current.isNotEmpty()) out.add(current.toString())
    return out
}

// ── Comparing one akshara against another ──────────────────────────────────

/** What happened to one akshara of the target word. */
data class AksharaFeedback(
    val expected: String,
    val heard: String,
    val correct: Boolean,
    /** Plain-language coaching, or null when there is nothing specific to say. */
    val tip: String?
)

/** The whole verdict for one attempt. */
data class PronunciationReport(
    val perAkshara: List<AksharaFeedback>,
    /** The single most useful thing to fix, or null when it was all right. */
    val headline: String?
) {
    val allCorrect: Boolean get() = perAkshara.isNotEmpty() && perAkshara.all { it.correct }
}

/** The base consonant of an akshara — the first character in script range. */
private fun baseOf(akshara: String, script: IndicScript): Char? =
    akshara.firstOrNull { it.code in script.range }

/**
 * Explains the difference between two aksharas in words a learner can act on.
 *
 * The order matters: the checks run from the mistake that changes meaning most
 * to the one that changes it least, and the first that fires is the one worth
 * saying. Telling someone about three things at once teaches none of them.
 */
private fun explainDifference(
    expected: String,
    heard: String,
    script: IndicScript
): String? {
    val e = baseOf(expected, script) ?: return null
    val h = baseOf(heard, script) ?: return null

    val ei = script.vargaConsonants.indexOf(e)
    val hi = script.vargaConsonants.indexOf(h)

    if (ei >= 0 && hi >= 0 && ei != hi) {
        val ePlace = ei / 5
        val hPlace = hi / 5
        val eCol = ei % 5
        val hCol = hi % 5

        // Same sound, wrong place in the mouth — the classic Indic contrast,
        // and the one that actually changes which word you said.
        if (eCol == hCol && ePlace != hPlace) {
            return "You said $h (${placeShort[hPlace]}). $expected is said ${placeNames[ePlace]}."
        }
        // Aspiration: the puff of breath that distinguishes क from ख.
        val eAspirated = eCol == 1 || eCol == 3
        val hAspirated = hCol == 1 || hCol == 3
        if (ePlace == hPlace && eAspirated != hAspirated) {
            return if (eAspirated) {
                "Almost — $expected needs a puff of breath after it. You said it plain, like $h."
            } else {
                "Almost — $expected has no puff of breath. You added one, which makes it $h."
            }
        }
        // Voicing: क versus ग.
        val eVoiced = eCol >= 2
        val hVoiced = hCol >= 2
        if (ePlace == hPlace && eVoiced != hVoiced) {
            return if (eVoiced) {
                "$expected is voiced — your throat should buzz. You said $h."
            } else {
                "$expected is unvoiced — no buzz in the throat. You said $h."
            }
        }
        return "You said $h, but this is $expected."
    }

    // Sibilants: श ष स are a common muddle and worth naming as one.
    if (e in script.sibilants && h in script.sibilants && e != h) {
        return "These three sound close. This one is $expected, not $h — listen again and copy the hiss."
    }

    // Vowel length, on the base letter and on the matra.
    for ((short, long) in script.shortLongVowels) {
        if (e == long && h == short) return "Hold the $long longer — you said the short $short."
        if (e == short && h == long) return "Keep $short short — you stretched it into $long."
    }
    for ((short, long) in script.shortLongMatras) {
        val eHasLong = expected.contains(long)
        val eHasShort = expected.contains(short)
        val hHasLong = heard.contains(long)
        val hHasShort = heard.contains(short)
        if (eHasLong && hHasShort) return "Hold this vowel longer — it is the long sign, not the short one."
        if (eHasShort && hHasLong) return "Keep this vowel short — you stretched it."
    }

    if (e != h) return "You said $h, but this is $expected."

    // Same base letter, so the difference is in the matras hanging off it.
    if (expected != heard) return "The letter is right — the vowel sign on it is not."
    return null
}

// ── Aligning the two words ─────────────────────────────────────────────────

/**
 * Lines up what was said against what was asked for, so a missing or extra
 * syllable shifts the rest rather than making every akshara after it look
 * wrong. Standard edit-distance alignment, walked backwards for the actual
 * operations.
 */
private fun align(target: List<String>, heard: List<String>): List<Pair<String?, String?>> {
    val n = target.size
    val m = heard.size
    val cost = Array(n + 1) { IntArray(m + 1) }
    for (i in 0..n) cost[i][0] = i
    for (j in 0..m) cost[0][j] = j
    for (i in 1..n) {
        for (j in 1..m) {
            val same = if (target[i - 1] == heard[j - 1]) 0 else 1
            cost[i][j] = minOf(
                cost[i - 1][j] + 1,
                cost[i][j - 1] + 1,
                cost[i - 1][j - 1] + same
            )
        }
    }
    val out = mutableListOf<Pair<String?, String?>>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        val same = if (i > 0 && j > 0 && target[i - 1] == heard[j - 1]) 0 else 1
        when {
            i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + same -> {
                out.add(target[i - 1] to heard[j - 1]); i--; j--
            }
            i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> {
                out.add(target[i - 1] to null); i--
            }
            else -> {
                out.add(null to heard[j - 1]); j--
            }
        }
    }
    return out.reversed()
}

/**
 * Compares an attempt against the target word and produces per-syllable
 * feedback plus the one tip most worth acting on.
 *
 * Returns null for languages with no akshara structure to work with — the
 * caller falls back to the plain right/wrong it always did.
 */
fun analysePronunciation(target: String, heard: String, lang: String): PronunciationReport? {
    val script = scriptFor(lang) ?: return null
    if (target.isBlank()) return null

    val targetAksharas = splitAksharas(target, lang)
    val heardAksharas = splitAksharas(heard, lang)
    if (targetAksharas.isEmpty()) return null

    // The recognizer returned nothing usable in this script — probably the
    // learner said an English word, or was not heard at all.
    if (heardAksharas.isEmpty()) {
        return PronunciationReport(
            perAkshara = targetAksharas.map {
                AksharaFeedback(expected = it, heard = "", correct = false, tip = null)
            },
            headline = "We didn't catch that in $lang. Tap 🔊 to hear it, then try once more."
        )
    }

    val feedback = mutableListOf<AksharaFeedback>()
    var firstTip: String? = null

    for ((expected, said) in align(targetAksharas, heardAksharas)) {
        when {
            // Nothing extra to teach about a syllable that wasn't asked for.
            expected == null -> continue
            said == null -> {
                if (firstTip == null) firstTip = "You left out $expected — say every syllable."
                feedback.add(AksharaFeedback(expected, "", false, "This syllable was missing."))
            }
            expected == said -> feedback.add(AksharaFeedback(expected, said, true, null))
            else -> {
                val tip = explainDifference(expected, said, script)
                if (firstTip == null && tip != null) firstTip = tip
                feedback.add(AksharaFeedback(expected, said, false, tip))
            }
        }
    }

    return PronunciationReport(
        perAkshara = feedback,
        headline = if (feedback.all { it.correct }) null else firstTip
    )
}
