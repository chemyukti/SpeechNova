/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — OFFLINE LANGUAGE PACKS                                     ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

/**
 * Every language SpeechNova offers, mapped to the ML Kit code for it.
 *
 * This lives at the top level rather than inside the screen so the offline
 * pack manager and the translator agree on one list. Two copies of a language
 * table drift, and a drifted table is how the Bengali alphabet ended up
 * missing ten letters.
 */
val LANGUAGE_TO_MLKIT: Map<String, String> = mapOf(
    "English" to TranslateLanguage.ENGLISH,
    "Spanish" to TranslateLanguage.SPANISH,
    "French" to TranslateLanguage.FRENCH,
    "German" to TranslateLanguage.GERMAN,
    "Chinese" to TranslateLanguage.CHINESE,
    "Japanese" to TranslateLanguage.JAPANESE,
    "Korean" to TranslateLanguage.KOREAN,
    "Arabic" to TranslateLanguage.ARABIC,
    "Russian" to TranslateLanguage.RUSSIAN,
    "Portuguese" to TranslateLanguage.PORTUGUESE,
    "Italian" to TranslateLanguage.ITALIAN,
    "Hindi" to TranslateLanguage.HINDI,
    "Bengali" to TranslateLanguage.BENGALI,
    "Tamil" to TranslateLanguage.TAMIL,
    "Telugu" to TranslateLanguage.TELUGU,
    "Marathi" to TranslateLanguage.MARATHI,
    "Gujarati" to TranslateLanguage.GUJARATI,
    "Kannada" to TranslateLanguage.KANNADA,
    "Urdu" to TranslateLanguage.URDU,
    "Indonesian" to TranslateLanguage.INDONESIAN,
    "Malay" to TranslateLanguage.MALAY,
    "Thai" to TranslateLanguage.THAI,
    "Vietnamese" to TranslateLanguage.VIETNAMESE,
    "Turkish" to TranslateLanguage.TURKISH,
    "Persian" to TranslateLanguage.PERSIAN,
    "Hebrew" to TranslateLanguage.HEBREW,
    "Dutch" to TranslateLanguage.DUTCH,
    "Polish" to TranslateLanguage.POLISH,
    "Ukrainian" to TranslateLanguage.UKRAINIAN,
    "Greek" to TranslateLanguage.GREEK,
    "Swedish" to TranslateLanguage.SWEDISH,
    "Danish" to TranslateLanguage.DANISH,
    "Norwegian" to TranslateLanguage.NORWEGIAN,
    "Finnish" to TranslateLanguage.FINNISH,
    "Czech" to TranslateLanguage.CZECH,
    "Romanian" to TranslateLanguage.ROMANIAN,
    "Hungarian" to TranslateLanguage.HUNGARIAN,
    "Swahili" to TranslateLanguage.SWAHILI,
    "Filipino" to TranslateLanguage.TAGALOG,
    "Afrikaans" to TranslateLanguage.AFRIKAANS,
    "Croatian" to TranslateLanguage.CROATIAN,
    "Bulgarian" to TranslateLanguage.BULGARIAN,
    "Slovak" to TranslateLanguage.SLOVAK,
    "Catalan" to TranslateLanguage.CATALAN
)

/**
 * Downloading, listing and deleting the on-device translation packs.
 *
 * The thing worth understanding here, because it explains a bug that looks
 * like nonsense from the outside:
 *
 * **ML Kit translates everything through English.** There is no Hindi→Bengali
 * model. There is a Hindi pack and a Bengali pack, and Hindi→Bengali runs
 * Hindi→English→Bengali using both of them.
 *
 * So English→Bengali needs *one* pack, and Hindi→Bengali needs *two*. Someone
 * who has only ever translated from English has exactly one pack per language
 * they used, and every English pair works offline — while the first
 * non-English pair they try offline fails, because the second pack was never
 * fetched and cannot be fetched now.
 *
 * That is why the app previously said "connect to the internet" for a pair the
 * user reasonably believed was already downloaded. The fix is to name the
 * missing pack, and to let people fetch packs deliberately before they travel
 * rather than discovering the gap when they are already offline.
 */
object OfflineLanguages {

    private val manager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }

    private fun modelFor(language: String): TranslateRemoteModel? {
        val code = LANGUAGE_TO_MLKIT[language] ?: return null
        return TranslateRemoteModel.Builder(code).build()
    }

    /**
     * The packs a translation between these two actually needs.
     *
     * English is the pivot and is built into the SDK, so a pair involving it
     * needs only the other side.
     */
    fun packsRequiredFor(from: String, to: String): List<String> =
        listOf(from, to).distinct().filter { it != "English" }

    /** Calls back with the names of the languages the user already has. */
    fun downloadedLanguages(onResult: (Set<String>) -> Unit) {
        manager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val codes = models.map { it.language }.toSet()
                onResult(
                    LANGUAGE_TO_MLKIT.filterValues { it in codes }.keys.toSet()
                )
            }
            .addOnFailureListener { e ->
                Log.w("SpeechNova", "Could not list downloaded packs: ${e.message}")
                onResult(emptySet())
            }
    }

    /**
     * Calls back with the packs needed for this pair that are *not* on the
     * device, so the app can name them instead of saying "connect to the
     * internet" about a language the user thinks they already downloaded.
     */
    fun missingPacksFor(from: String, to: String, onResult: (List<String>) -> Unit) {
        val needed = packsRequiredFor(from, to)
        if (needed.isEmpty()) {
            onResult(emptyList())
            return
        }
        downloadedLanguages { have ->
            onResult(needed.filter { it !in have })
        }
    }

    fun download(language: String, onResult: (Boolean) -> Unit) {
        val model = modelFor(language)
        if (model == null) {
            onResult(false)
            return
        }
        manager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                Log.w("SpeechNova", "Pack download failed for $language: ${e.message}")
                onResult(false)
            }
    }

    fun delete(language: String, onResult: (Boolean) -> Unit) {
        val model = modelFor(language)
        if (model == null) {
            onResult(false)
            return
        }
        manager.deleteDownloadedModel(model)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { e ->
                Log.w("SpeechNova", "Pack delete failed for $language: ${e.message}")
                onResult(false)
            }
    }
}
