/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — TIDYING UP WHAT PEOPLE ACTUALLY SAY                        ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

/**
 * Strips the noise out of a spoken transcript: hesitation sounds, filler
 * phrases and stutters.
 *
 * This matters most for a lecture. Nobody speaks for forty minutes in clean
 * sentences, and "um, so, basically, the the mitochondria" translates worse
 * than "the mitochondria" — the filler words get translated too, and they
 * arrive as nonsense in the target language.
 *
 * Deliberately conservative. Removing a word the speaker meant is worse than
 * leaving one in, so this only touches things that are filler in every
 * context.
 */
object TextCleaner {

    /** Hesitation noises: um, uhh, errr, hmm, and their stretched forms. */
    private val hesitations = Regex(
        "\\b(u+m+|u+h+|e+r+|a+h+|h+m+|m+h+m+|h+u+h+|e+u+h+)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Phrases that carry no meaning where they appear in speech.
     *
     * "actually" and "literally" are left in on purpose, unlike the web
     * version this came from: both change meaning often enough that dropping
     * them silently rewrites what the speaker said.
     */
    private val fillerPhrases = listOf(
        "you know", "i mean", "sort of", "kind of", "so yeah", "anyway", "anyways"
    ).map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    /** Hindi and Marathi conversational fillers. */
    private val devanagariFillers = Regex("\\b(मतलब|यार|अरे|वैसे)\\b")

    /** "the the", "I I" — the recognizer transcribes stutters literally. */
    private val stutter = Regex("\\b(\\w+)(\\s+\\1)\\b", RegexOption.IGNORE_CASE)

    fun clean(text: String, language: String = ""): String {
        if (text.isBlank()) return ""
        var out = text

        out = hesitations.replace(out, "")
        fillerPhrases.forEach { out = it.replace(out, "") }

        // Only for scripts where those words are actually filler.
        if (language == "Hindi" || language == "Marathi" ||
            out.any { it.code in 0x0900..0x097F }
        ) {
            out = devanagariFillers.replace(out, "")
        }

        out = stutter.replace(out) { it.groupValues[1] }

        // Tidy the punctuation the removals left behind.
        out = out
            .replace(Regex("\\s*,\\s*,"), ",")
            .replace(Regex("^\\s*,\\s*"), "")
            .replace(Regex("\\s*,\\s*\\."), ".")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        // Keep the original capitalisation of the sentence.
        if (out.isNotEmpty() && text.first().isUpperCase()) {
            out = out.replaceFirstChar { it.uppercase() }
        }
        return out
    }
}
