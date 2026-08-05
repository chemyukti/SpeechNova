/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — WHICH LANGUAGE IS THIS?                                    ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions

/**
 * Works out what language a piece of text is written in.
 *
 * This runs entirely on the phone — no account, no API key, and no text ever
 * leaves the device. That matters here beyond the obvious: SpeechNova is in
 * the Families programme, so anything sent to a server would be a children's
 * data disclosure with everything that follows from it.
 *
 * The limit worth knowing: this identifies the language of *text*. There is no
 * on-device way to hear speech and identify its language before transcribing
 * it — Android's recognizer must be told which language to expect. So spoken
 * detection here means "transcribe with the selected language, then check
 * whether the words that came out actually look like that language", which
 * catches the common case of the wrong language being selected but is not true
 * spoken language identification.
 */
object LanguageDetection {

    /** ML Kit's answer when it isn't confident enough to name a language. */
    const val UNDETERMINED = "und"

    /**
     * Below this the guess is worse than no guess. ML Kit's default is 0.5;
     * this is deliberately stricter, because acting on a wrong detection
     * (switching the user's language for them) is more annoying than staying
     * quiet.
     */
    private const val CONFIDENCE_THRESHOLD = 0.65f

    /**
     * Short text is where identification goes wrong — "no" is a word in a
     * dozen languages. Anything shorter than this isn't worth guessing at.
     */
    private const val MIN_TEXT_LENGTH = 12

    private val client by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build()
        )
    }

    /**
     * Calls back with the app's own name for the detected language ("Hindi",
     * "Spanish"), or null when the text is too short, the language could not
     * be determined confidently, or it isn't one this app can translate.
     */
    fun detect(text: String, onResult: (String?) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.length < MIN_TEXT_LENGTH) {
            onResult(null)
            return
        }
        client.identifyLanguage(trimmed)
            .addOnSuccessListener { code ->
                if (code == UNDETERMINED) {
                    onResult(null)
                } else {
                    onResult(languageNameForCode(code))
                }
            }
            .addOnFailureListener { e ->
                Log.w("SpeechNova", "Language identification failed: ${e.message}")
                onResult(null)
            }
    }

    /**
     * ML Kit answers with a BCP-47 tag ("hi", "zh-Latn", "pt-BR"); the app
     * thinks in names. Only the base language matters for the mapping, and
     * only languages SpeechNova can actually translate are worth reporting —
     * naming a language it can't handle would just be a dead end for the user.
     */
    private fun languageNameForCode(code: String): String? {
        val base = code.substringBefore('-').lowercase()
        return codeToLanguageName[base]
    }

    /**
     * Kept in step with `langToMLKit` in MainActivity — every language the app
     * offers, keyed by the ISO code ML Kit reports.
     */
    private val codeToLanguageName = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "zh" to "Chinese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "ru" to "Russian",
        "pt" to "Portuguese",
        "it" to "Italian",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "ta" to "Tamil",
        "te" to "Telugu",
        "mr" to "Marathi",
        "gu" to "Gujarati",
        "kn" to "Kannada",
        "ur" to "Urdu"
    )
}
