/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — OFFLINE VOICE INPUT PACKS                                  ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.Executor

/**
 * The *other* half of working offline, and the one that actually stops
 * non-English translation from working with no signal.
 *
 * Translation packs and speech packs are completely separate things:
 *
 *  - A **translation pack** is ML Kit's, downloaded by the app, managed in
 *    Settings → Offline languages.
 *  - A **speech pack** belongs to the phone's speech recognition service
 *    (usually Google's). The app cannot bundle it and, before Android 13,
 *    cannot download it either — only the user can, from system settings.
 *
 * English speech recognition is installed on essentially every Android phone.
 * Hindi, Bengali, Tamil and the rest usually are not. So with every ML Kit
 * pack downloaded, English → anything still works offline while anything else
 * fails at the microphone: the recognizer plays its start beep, finds no
 * on-device model for that language, and ends with nothing captured.
 *
 * That is a missing system component, not a bug in the app — but silently
 * beeping and giving up is, so this exists to detect it and to fix it where
 * the platform allows.
 */
object SpeechPacks {

    private const val TAG = "SpeechNova"

    /** Runs the callback on the caller's thread; the API demands an Executor. */
    private val directExecutor = Executor { it.run() }

    /**
     * Whether this phone can recognise [languageTag] with no network.
     *
     * Calls back `null` below Android 13, where the platform offers no way to
     * ask — the app must not claim a language is missing when it cannot tell.
     */
    fun isInstalledOnDevice(
        context: Context,
        languageTag: String,
        onResult: (Boolean?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(null)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(false)
            return
        }
        runCatching {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            recognizer.checkRecognitionSupport(
                intent,
                directExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        // Tags come back in mixed forms ("hi", "hi-IN"), so
                        // compare on the base language rather than exactly.
                        val wanted = languageTag.substringBefore('-').lowercase()
                        val installed = support.installedOnDeviceLanguages.any {
                            it.substringBefore('-').lowercase() == wanted
                        }
                        onResult(installed)
                        recognizer.destroy()
                    }

                    override fun onError(error: Int) {
                        Log.i(TAG, "Recognition support check failed: $error")
                        onResult(null)
                        recognizer.destroy()
                    }
                }
            )
        }.onFailure {
            Log.i(TAG, "Could not check on-device recognition: ${it.message}")
            onResult(null)
        }
    }

    /**
     * Asks the recognition service to fetch the offline model for a language.
     *
     * Android 13 and up only — this is the first release that let an app ask
     * at all. It needs a connection: the point is to prepare before losing one.
     */
    fun triggerDownload(context: Context, languageTag: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return runCatching {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            recognizer.triggerModelDownload(intent)
            // Left alive briefly: destroying immediately can cancel the request
            // on some implementations.
            true
        }.onFailure {
            Log.w(TAG, "Could not trigger speech model download: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * Opens the phone's voice-input settings, which is the only route on
     * Android 12 and below. Returns false when no such screen can be opened,
     * so the caller can fall back to written instructions.
     */
    fun openVoiceInputSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.android.settings.VOICE_INPUT_SETTINGS"),
            Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val result = runCatching {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (result) return true
        }
        return false
    }

    /**
     * True when a recognizer error is the shape of "this language has no
     * offline model here" rather than a passing glitch.
     */
    fun looksLikeMissingLanguage(error: Int): Boolean = when (error) {
        // Android 13 named these; older releases just say NO_MATCH or SERVER.
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> true
        else -> false
    }
}
