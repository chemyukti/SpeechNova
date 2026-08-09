/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — LECTURE TRANSCRIPTS                                        ║
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One stretch of speech, translated, with where it fell in the talk.
 *
 * [afterPauseSeconds] is set when the speaker stopped long enough that this
 * is plainly a new point rather than a continuation. That is what turns a wall
 * of text into something a student can find their place in afterwards.
 */
data class LectureChunk(
    val clockTime: String,
    val offsetLabel: String,
    val original: String,
    val translated: String,
    val afterPauseSeconds: Int = 0
)

/** A saved talk. */
data class LectureSession(
    val id: Long,
    val title: String,
    val date: String,
    val fromLang: String,
    val toLang: String,
    val durationSeconds: Int,
    val chunks: List<LectureChunk>
)

/**
 * Saving and re-reading lecture transcripts.
 *
 * Sessions live on the device, like everything else this app stores. A
 * transcript of a class is exactly the sort of thing that should not be
 * uploaded anywhere by an app whose audience includes children.
 */
object Lectures {

    private const val PREFS = "speechnova_lectures"
    private const val KEY_SESSIONS = "sessions"

    /** More than this and the oldest is dropped, so storage can't grow forever. */
    private const val MAX_SESSIONS = 20

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<LectureSession> = runCatching {
        val raw = prefs(context).getString(KEY_SESSIONS, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val chunkArray = o.getJSONArray("chunks")
            LectureSession(
                id = o.getLong("id"),
                title = o.getString("title"),
                date = o.getString("date"),
                fromLang = o.getString("fromLang"),
                toLang = o.getString("toLang"),
                durationSeconds = o.optInt("durationSeconds", 0),
                chunks = (0 until chunkArray.length()).map { j ->
                    val c = chunkArray.getJSONObject(j)
                    LectureChunk(
                        clockTime = c.optString("clockTime", ""),
                        offsetLabel = c.optString("offsetLabel", ""),
                        original = c.optString("original", ""),
                        translated = c.optString("translated", ""),
                        afterPauseSeconds = c.optInt("afterPauseSeconds", 0)
                    )
                }
            )
        }.sortedByDescending { it.id }
    }.onFailure {
        Log.w("SpeechNova", "Could not read lecture sessions", it)
    }.getOrDefault(emptyList())

    fun save(context: Context, session: LectureSession) {
        val kept = (listOf(session) + all(context).filter { it.id != session.id })
            .take(MAX_SESSIONS)
        write(context, kept)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, sessions: List<LectureSession>) {
        runCatching {
            val array = JSONArray()
            sessions.forEach { session ->
                val chunks = JSONArray()
                session.chunks.forEach { chunk ->
                    chunks.put(
                        JSONObject()
                            .put("clockTime", chunk.clockTime)
                            .put("offsetLabel", chunk.offsetLabel)
                            .put("original", chunk.original)
                            .put("translated", chunk.translated)
                            .put("afterPauseSeconds", chunk.afterPauseSeconds)
                    )
                }
                array.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("title", session.title)
                        .put("date", session.date)
                        .put("fromLang", session.fromLang)
                        .put("toLang", session.toLang)
                        .put("durationSeconds", session.durationSeconds)
                        .put("chunks", chunks)
                )
            }
            prefs(context).edit().putString(KEY_SESSIONS, array.toString()).apply()
        }.onFailure { Log.w("SpeechNova", "Could not save lecture session", it) }
    }

    /** Plain text, for sharing or pasting into notes. */
    fun asText(session: LectureSession): String = buildString {
        appendLine(session.title)
        appendLine(session.date)
        appendLine("${session.fromLang} → ${session.toLang}")
        appendLine("Length: ${formatDuration(session.durationSeconds)}")
        appendLine()
        session.chunks.forEach { chunk ->
            if (chunk.afterPauseSeconds > 0) {
                appendLine("--- pause, ${chunk.afterPauseSeconds}s ---")
            }
            appendLine("[${chunk.offsetLabel}] ${chunk.original}")
            appendLine("        ${chunk.translated}")
            appendLine()
        }
        appendLine("Transcribed with SpeechNova")
    }

    fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun clockNow(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    fun dateNow(): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
}
