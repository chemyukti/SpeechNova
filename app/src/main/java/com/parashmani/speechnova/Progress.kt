/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — LEARNER PROFILES, SCORES AND CUSTOM WORDS                  ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** One learner on this phone, with the score they've built up. */
data class Learner(val name: String, val points: Int)

/** A word the user added themselves, in English plus one target language. */
data class CustomWord(val english: String, val language: String, val translated: String)

/**
 * Everything the app remembers between runs: who is practising, how they're
 * doing, and the words they've added.
 *
 * This is stored on the device and nowhere else. A shared, online leaderboard
 * would mean uploading children's names and scores to a server — SpeechNova is
 * in the Families programme, so that is a data-collection disclosure and a
 * consent problem, not just a piece of plumbing. Everyone who uses this phone
 * can see and compete on the same board, which is what a family or a classroom
 * actually needs, and it keeps working with no connection.
 *
 * SharedPreferences with small JSON blobs is deliberate: the data is a handful
 * of names and a word list, and a database would be more machinery than the
 * problem deserves.
 */
object Progress {

    private const val PREFS = "speechnova_progress"
    private const val KEY_CURRENT = "current_learner"
    private const val KEY_LEARNERS = "learners"
    private const val KEY_WORDS = "custom_words"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Who is playing ────────────────────────────────────────────────────

    /**
     * The name entered on this device. Asked for once, on the first visit to
     * the game, and then never again — a child shouldn't have to type their
     * name to play.
     */
    fun currentLearner(context: Context): String? =
        prefs(context).getString(KEY_CURRENT, null)?.takeIf { it.isNotBlank() }

    fun setCurrentLearner(context: Context, name: String) {
        val clean = name.trim().take(20)
        if (clean.isEmpty()) return
        prefs(context).edit().putString(KEY_CURRENT, clean).apply()
        // Make sure they appear on the board straight away, on zero points.
        if (leaderboard(context).none { it.name.equals(clean, ignoreCase = true) }) {
            saveScores(context, leaderboard(context) + Learner(clean, 0))
        }
    }

    /** Lets a second person on the same phone play as themselves. */
    fun switchLearner(context: Context) {
        prefs(context).edit().remove(KEY_CURRENT).apply()
    }

    // ── Scores ────────────────────────────────────────────────────────────

    /** Everyone who has played on this device, best score first. */
    fun leaderboard(context: Context): List<Learner> = runCatching {
        val raw = prefs(context).getString(KEY_LEARNERS, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Learner(o.getString("name"), o.getInt("points"))
        }.sortedByDescending { it.points }
    }.onFailure {
        Log.w("SpeechNova", "Could not read the leaderboard", it)
    }.getOrDefault(emptyList())

    /** Adds points to whoever is currently playing. */
    fun addPoints(context: Context, points: Int) {
        val name = currentLearner(context) ?: return
        val existing = leaderboard(context)
        val updated = if (existing.any { it.name.equals(name, ignoreCase = true) }) {
            existing.map {
                if (it.name.equals(name, ignoreCase = true)) {
                    it.copy(points = (it.points + points).coerceAtLeast(0))
                } else {
                    it
                }
            }
        } else {
            existing + Learner(name, points.coerceAtLeast(0))
        }
        saveScores(context, updated)
    }

    fun pointsFor(context: Context, name: String?): Int {
        if (name == null) return 0
        return leaderboard(context)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }?.points ?: 0
    }

    private fun saveScores(context: Context, learners: List<Learner>) {
        runCatching {
            val array = JSONArray()
            learners.forEach { learner ->
                array.put(
                    JSONObject()
                        .put("name", learner.name)
                        .put("points", learner.points)
                )
            }
            prefs(context).edit().putString(KEY_LEARNERS, array.toString()).apply()
        }.onFailure { Log.w("SpeechNova", "Could not save scores", it) }
    }

    // ── Words the user added themselves ───────────────────────────────────

    /** Custom words for one language, oldest first. */
    fun customWords(context: Context, language: String): List<CustomWord> =
        allCustomWords(context).filter { it.language == language }

    fun addCustomWord(context: Context, word: CustomWord): Boolean {
        val english = word.english.trim()
        if (english.isEmpty()) return false
        val existing = allCustomWords(context)
        // Adding the same word twice is a mistake, not an intention.
        if (existing.any {
                it.language == word.language &&
                        it.english.equals(english, ignoreCase = true)
            }
        ) {
            return false
        }
        saveWords(context, existing + word.copy(english = english))
        return true
    }

    fun removeCustomWord(context: Context, language: String, english: String) {
        saveWords(
            context,
            allCustomWords(context).filterNot {
                it.language == language && it.english.equals(english, ignoreCase = true)
            }
        )
    }

    private fun allCustomWords(context: Context): List<CustomWord> = runCatching {
        val raw = prefs(context).getString(KEY_WORDS, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            CustomWord(
                english = o.getString("english"),
                language = o.getString("language"),
                translated = o.optString("translated", "")
            )
        }
    }.onFailure {
        Log.w("SpeechNova", "Could not read custom words", it)
    }.getOrDefault(emptyList())

    private fun saveWords(context: Context, words: List<CustomWord>) {
        runCatching {
            val array = JSONArray()
            words.forEach { word ->
                array.put(
                    JSONObject()
                        .put("english", word.english)
                        .put("language", word.language)
                        .put("translated", word.translated)
                )
            }
            prefs(context).edit().putString(KEY_WORDS, array.toString()).apply()
        }.onFailure { Log.w("SpeechNova", "Could not save custom words", it) }
    }
}
