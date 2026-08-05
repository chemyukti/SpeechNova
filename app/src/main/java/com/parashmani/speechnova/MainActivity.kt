/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                         SPEECHNOVA v1.5                                  ║
 * ║                  Real-Time Speech Translation                            ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ║                                                                          ║
 * ║  LICENCE: Proprietary software. Unauthorized copying, modification,      ║
 * ║  or distribution is strictly prohibited.                                 ║
 * ║                                                                          ║
 * ║  v1.5 — reorganised into four screens driven by a bottom navigation      ║
 * ║  bar:  HOME (translate) · LEARN (alphabet) · PHRASES · FACE-TO-FACE.     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

private const val APP_ENCODED = "U3BlZWNoTm92YSB8IERldmVsb3BlZCBieSBNci5QYXJhc2htYW5p"
private val APP_IDENTITY: String by lazy {
    String(Base64.decode(APP_ENCODED, Base64.DEFAULT))
}

private const val APP_NAME = "SpeechNova"
private const val APP_AUTHOR = "Mr.Parashmani"
private const val APP_CREDIT = "Built by Mr.Parashmani"
private const val APP_COPYRIGHT = "© 2025 Parashmani. All rights reserved."
private const val APP_PACKAGE = "com.parashmani.speechnova"

// ── Ad unit IDs ────────────────────────────────────────────────────────────
// Banner and native only. SpeechNova is in the Families programme, where
// full-screen formats that cannot be dismissed within five seconds are
// prohibited — a rewarded ad is unclosable for its whole run by design, so
// there is no rewarded unit here and no gate for one to sit behind.
private const val AD_UNIT_BANNER = "ca-app-pub-8499432704301966/9196802502"
private const val AD_UNIT_NATIVE = "ca-app-pub-8499432704301966/9457225173"

// ── Face-to-Face echo control ──────────────────────────────────────────────
// Face-to-Face speaks the translation out of the same phone that is listening
// for the next sentence. Left alone, the recognizer hears the phone's own
// voice, translates that, speaks the result, hears itself again — a loop the
// two humans can't get a word into. Three things break it: the mic is never
// live while the phone is talking, a short guard covers the speaker's tail,
// and anything that comes back sounding like what we just said is discarded.

/** Utterance ID marking a Face-to-Face reply, so the progress listener can
 *  tell the conversational voice from everything else the app speaks. */
private const val FACE_UTTERANCE_ID = "face2face"

/** Silence after the phone stops talking before the mic is trusted again —
 *  long enough for the speaker's tail and the room's echo to die away. */
private const val FACE_ECHO_GUARD_MS = 400L

/** How often the re-arm loop re-checks whether the phone has finished. */
private const val FACE_SPEAK_POLL_MS = 200L

/** Fallback ceiling on how long a reply is assumed to take, in case the TTS
 *  engine never reports that it finished. Without it a missed callback would
 *  leave the mic switched off for the rest of the conversation. */
private fun faceSpeechWatchdogMs(text: String): Long =
    (text.length * 150L + 4000L).coerceAtMost(60_000L)

/** Strips case, punctuation and spacing so a heard sentence can be compared
 *  with what the phone just said without tripping over formatting. */
private fun normalizeForEchoCheck(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }

/** True when [heard] looks like the phone's own [spoken] reply coming back.
 *  Recognition of an echo is rarely word-perfect, so containment either way
 *  counts — but only for text long enough that the match means something. */
private fun soundsLikeOurOwnVoice(heard: String, spoken: String): Boolean {
    if (spoken.isBlank()) return false
    val h = normalizeForEchoCheck(heard)
    val s = normalizeForEchoCheck(spoken)
    if (h.isEmpty() || s.isEmpty()) return false
    if (h == s) return true
    // A three-character overlap would match half the language; require enough
    // substance that a real reply from the other person can't trip it.
    val shorter = minOf(h.length, s.length)
    if (shorter < 8) return false
    return h.contains(s) || s.contains(h)
}

class MainActivity : ComponentActivity() {

    private var appUpdates: AppUpdates? = null

    /** Flipped when a downloaded update is waiting for the app to restart. */
    private var updateReadyToInstall by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 makes apps targeting SDK 35+ edge-to-edge whether they ask
        // or not. enableEdgeToEdge() is the supported way in: it sets the
        // system-bar handling this app needs and keeps behaving on older
        // releases. It has to run before super.onCreate to apply to the first
        // frame. Insets are consumed by systemBarsPadding() on the root layout.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        check(packageName == APP_PACKAGE) {
            "License violation: $APP_NAME was created by $APP_AUTHOR. " +
                    "Unauthorized distribution violates copyright law."
        }

        Log.i("SpeechNova", "=== $APP_NAME ===")
        Log.i("SpeechNova", APP_CREDIT)
        Log.i("SpeechNova", APP_COPYRIGHT)
        Log.i("SpeechNova", "Identity: $APP_IDENTITY")

        // Restricts served ads to general-audience (G) content *before* the
        // SDK starts, so the ads stay consistent with the store rating.
        AdPolicy.initialize(this)

        // Offer a newer version if Play has one. Downloads in the background;
        // the user is only interrupted at the end, to restart.
        appUpdates = AppUpdates(this).also { updates ->
            updates.onReadyToInstall = { updateReadyToInstall = true }
            updates.check()
        }

        setContent {
            MaterialTheme {
                SpeechNovaApp(
                    updateReadyToInstall = updateReadyToInstall,
                    onInstallUpdate = { appUpdates?.completeUpdate() },
                    onDismissUpdate = { updateReadyToInstall = false }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // An update that finished downloading while the app was away still
        // needs its "restart to finish" prompt.
        appUpdates?.resume()
    }

    override fun onDestroy() {
        appUpdates?.dispose()
        appUpdates = null
        super.onDestroy()
    }
}

/** The four top-level destinations reachable from the bottom navigation bar. */
enum class Screen { HOME, LEARN, PHRASES, FACE2FACE, QUIZ }

data class TranslationMessage(
    val displayText: String,
    val originalText: String = "",
    val type: String,
    val translatedText: String = "",
    val isFavorite: MutableState<Boolean> = mutableStateOf(false),
    // Pronunciation-guide content for Learning Mode, kept separate from
    // displayText so it can be rendered as its own clearly labeled card
    // instead of being crammed into one paragraph.
    val learningExtra: String = ""
)

data class PhraseCategory(
    val name: String,
    val emoji: String,
    val phrases: List<String>
)

private val phraseCategories = listOf(
    PhraseCategory(
        "Daily Expressions", "💬", listOf(
            "Hello", "Thank you", "Please", "Excuse me", "Yes", "No",
            "I don't understand", "Nice to meet you", "How are you?", "Goodbye"
        )
    ),
    PhraseCategory(
        "Airport & Travel", "✈️", listOf(
            "Where is the boarding gate?", "I need to check in",
            "My flight is delayed", "Where is baggage claim?",
            "I lost my luggage", "Where is the taxi stand?",
            "How much is a ticket to the city center?", "I am a tourist"
        )
    ),
    PhraseCategory(
        "Restaurant", "🍽️", listOf(
            "Can I see the menu?", "I would like water, please",
            "The bill, please", "I am vegetarian", "This is delicious",
            "Do you have any recommendations?", "Is this spicy?",
            "Can I pay by card?"
        )
    ),
    PhraseCategory(
        "Accommodation", "🏨", listOf(
            "I have a reservation", "What time is check-out?",
            "Can I get an extra towel?", "Is breakfast included?",
            "The Wi-Fi is not working", "Can you call a taxi for me?",
            "I would like a room for two nights"
        )
    ),
    PhraseCategory(
        "Transportation", "🚕", listOf(
            "How much is this?", "Please take me to this address",
            "Where is the nearest bus stop?", "Does this train go downtown?",
            "Please stop here", "How far is the train station?"
        )
    ),
    PhraseCategory(
        "Shopping", "🛍️", listOf(
            "How much does this cost?", "Do you have a smaller size?",
            "Can I try this on?", "Is there a discount?",
            "I am just looking, thank you", "Can I get a receipt?"
        )
    ),
    PhraseCategory(
        "Emergency", "🆘", listOf(
            "I need a doctor", "Please call the police",
            "I am lost", "This is an emergency",
            "I need help right now", "Where is the nearest hospital?",
            "I am allergic to this"
        )
    )
)

// ═══════════════════════════════════════════════════════════════════════
// DEVANAGARI → LATIN TRANSLITERATION (Hindi & Marathi). Simplified,
// phonetically-honest romanization used by Learning Mode. See Alphabets.kt
// for the LEARN-screen letter tables.
// ═══════════════════════════════════════════════════════════════════════

private val devanagariVowels = mapOf(
    'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
    'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
)

private val devanagariMatras = mapOf(
    'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
    'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
)

private val devanagariConsonants = mapOf(
    'क' to "ka", 'ख' to "kha", 'ग' to "ga", 'घ' to "gha", 'ङ' to "nga",
    'च' to "cha", 'छ' to "chha", 'ज' to "ja", 'झ' to "jha", 'ञ' to "nya",
    'ट' to "ta", 'ठ' to "tha", 'ड' to "da", 'ढ' to "dha", 'ण' to "na",
    'त' to "ta", 'थ' to "tha", 'द' to "da", 'ध' to "dha", 'न' to "na",
    'प' to "pa", 'फ' to "pha", 'ब' to "ba", 'भ' to "bha", 'म' to "ma",
    'य' to "ya", 'र' to "ra", 'ल' to "la", 'व' to "va",
    'श' to "sha", 'ष' to "sha", 'स' to "sa", 'ह' to "ha",
    'ळ' to "la",
    // Nukta (borrowed-sound) consonants, common in Hindi/Marathi/Urdu loanwords.
    // Written as explicit \u escapes (each is ONE precomposed Devanagari code
    // point, U+0958–U+095F) so this can never become a multi-code-point Char.
    '\u0958' to "qa", '\u0959' to "kha", '\u095A' to "gha", '\u095B' to "za",
    '\u095C' to "ra", '\u095D' to "rha", '\u095E' to "fa", '\u095F' to "ya"
)
// Note: क्ष and ज्ञ are not hardcoded here — they're each a two-consonant
// sequence joined by a virama, so the general algorithm renders them correctly.

private const val DEVANAGARI_VIRAMA = '्'
private val devanagariAnusvara = setOf('ं', 'ँ')
private const val DEVANAGARI_VISARGA = 'ः'

private fun devanagariToLatin(text: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val ch = text[i]

        when {
            devanagariConsonants.containsKey(ch) -> {
                val base = devanagariConsonants.getValue(ch).dropLast(1) // strip inherent "a"
                val next = text.getOrNull(i + 1)
                when {
                    next != null && devanagariMatras.containsKey(next) -> {
                        out.append(base).append(devanagariMatras.getValue(next))
                        i++ // consume the matra too
                    }
                    next == DEVANAGARI_VIRAMA -> {
                        out.append(base)
                        i++ // consume the virama, no vowel sound
                    }
                    else -> out.append(base).append("a")
                }
            }
            devanagariVowels.containsKey(ch) -> out.append(devanagariVowels.getValue(ch))
            ch in devanagariAnusvara -> out.append("n")
            ch == DEVANAGARI_VISARGA -> out.append("h")
            ch == '।' || ch == '॥' -> out.append(".")
            ch.isWhitespace() -> out.append(" ")
            else -> out.append(ch) // punctuation, digits, anything else: pass through
        }
        i++
    }
    return out.toString().trim()
}

/** True for languages where SpeechNova can show a real letter-by-letter
 *  pronunciation guide (currently Devanagari script: Hindi, Marathi). */
private fun hasLetterLevelGuide(lang: String): Boolean = lang == "Hindi" || lang == "Marathi"

private val wordSplitIndicLanguages = setOf(
    "Bengali", "Tamil", "Telugu", "Gujarati", "Kannada", "Urdu"
)

/** Builds the Learning Mode pronunciation-guide text for a translated
 *  string, honestly labeled depending on how much detail we can actually
 *  give for that script. */
private fun buildLearningGuide(translated: String, targetLang: String, original: String): String {
    val guide = StringBuilder()
    if (hasLetterLevelGuide(targetLang)) {
        guide.append("🔤 Pronunciation: ").append(devanagariToLatin(translated))
    } else if (targetLang in wordSplitIndicLanguages) {
        guide.append("🔤 Word-by-word: ").append(translated.split(" ").joinToString("  •  "))
    } else {
        guide.append("🔤 Say it like this: ").append(translated.lowercase().split(" ").joinToString("  •  "))
    }
    guide.append("\n📝 You said: ").append(original)
    return guide.toString()
}


// The header's colour scheme. Each shortcut gets its own accent rather than
// five identical translucent circles — colour is the fastest thing to aim for
// on a crowded row, and it gives each action a constant identity.
private val HEADER_GRADIENT = listOf(
    Color(0xFF4f46e5), // indigo
    Color(0xFF7c3aed), // violet
    Color(0xFFa855f7), // purple
    Color(0xFFdb2777)  // rose
)
private val ACCENT_HELP = Color(0xFFfbbf24)      // amber
private val ACCENT_TEXT = Color(0xFF38bdf8)      // sky
private val ACCENT_SCAN = Color(0xFF34d399)      // emerald
private val ACCENT_SAVED = Color(0xFFf472b6)     // pink
private val ACCENT_SETTINGS = Color(0xFFc4b5fd)  // light violet

// A header icon that always carries a real text label underneath it, so
// nothing in the top bar depends on a beginner correctly guessing an emoji.
@Composable
private fun HeaderShortcut(
    emoji: String,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                // A solid accent under the emoji, lifted off the gradient by a
                // pale ring so it stays legible whatever colour it sits over.
                .background(Color.White.copy(alpha = 0.35f))
                .padding(2.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// A very compact icon+label pair — used for Save/Copy/Share so those three
// actions are never just bare icons a beginner has to guess at.
@Composable
private fun MiniLabeledIcon(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// NATIVE AD — built programmatically (no XML layout needed) so it can
// live inside Compose via AndroidView. Renders nothing until an ad has
// actually loaded, so there's never an empty/broken-looking card.
// ═══════════════════════════════════════════════════════════════════════
private fun buildNativeAdView(context: android.content.Context): NativeAdView {
    val density = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * density).toInt()

    val nativeAdView = NativeAdView(context)

    val root = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(android.graphics.Color.parseColor("#1e293b"))
    }

    // The Families rules require an ad to be obviously an ad — a child must
    // never mistake it for part of the app. A 10sp grey "Ad" did not clear
    // that bar, so this is a high-contrast badge instead.
    val adLabel = android.widget.TextView(context).apply {
        text = "  ADVERTISEMENT  "
        setTextColor(android.graphics.Color.parseColor("#0b1220"))
        setBackgroundColor(android.graphics.Color.parseColor("#fbbf24"))
        textSize = 11f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
    }
    root.addView(adLabel)

    val topRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, dp(4))
    }

    val iconView = android.widget.ImageView(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(40))
    }
    topRow.addView(iconView)

    val textCol = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(10) }
    }
    val headlineView = android.widget.TextView(context).apply {
        setTextColor(android.graphics.Color.WHITE)
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    val bodyView = android.widget.TextView(context).apply {
        setTextColor(android.graphics.Color.parseColor("#cbd5e1"))
        textSize = 12f
        maxLines = 2
    }
    textCol.addView(headlineView)
    textCol.addView(bodyView)
    topRow.addView(textCol)
    root.addView(topRow)

    val mediaView = MediaView(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
        )
    }
    root.addView(mediaView)

    val ctaButton = android.widget.Button(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setBackgroundColor(android.graphics.Color.parseColor("#6366f1"))
        setTextColor(android.graphics.Color.WHITE)
        isAllCaps = false
    }
    root.addView(ctaButton)

    nativeAdView.addView(root)
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.iconView = iconView
    nativeAdView.mediaView = mediaView
    nativeAdView.callToActionView = ctaButton

    return nativeAdView
}

private fun populateNativeAdView(nativeAdView: NativeAdView, ad: NativeAd) {
    (nativeAdView.headlineView as? android.widget.TextView)?.text = ad.headline

    val bodyTextView = nativeAdView.bodyView as? android.widget.TextView
    if (ad.body.isNullOrEmpty()) {
        bodyTextView?.visibility = android.view.View.GONE
    } else {
        bodyTextView?.visibility = android.view.View.VISIBLE
        bodyTextView?.text = ad.body
    }

    val iconImageView = nativeAdView.iconView as? android.widget.ImageView
    val icon = ad.icon
    if (icon != null) {
        iconImageView?.setImageDrawable(icon.drawable)
        iconImageView?.visibility = android.view.View.VISIBLE
    } else {
        iconImageView?.visibility = android.view.View.GONE
    }

    ad.mediaContent?.let { nativeAdView.mediaView?.setMediaContent(it) }

    val cta = ad.callToAction
    (nativeAdView.callToActionView as? android.widget.Button)?.text = cta
    nativeAdView.callToActionView?.visibility =
        if (cta.isNullOrEmpty()) android.view.View.INVISIBLE else android.view.View.VISIBLE

    nativeAdView.setNativeAd(ad)
}

@Composable
private fun NativeAdCard(adUnitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("SpeechNova", "Native ad failed to load: ${error.message}")
                }
            })
            // A native ad whose video starts talking over the user is an ad
            // that interferes with app use. Video in this card always starts
            // muted; the ad's own control is the only way to turn sound on.
            .withNativeAdOptions(AdPolicy.nativeAdOptions())
            .build()
        adLoader.loadAd(AdPolicy.request())

        onDispose { nativeAd?.destroy() }
    }

    val ad = nativeAd
    if (ad != null) {
        Surface(
            modifier = modifier,
            color = Color(0xFF1e293b),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 3.dp
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> buildNativeAdView(ctx) },
                update = { view -> populateNativeAdView(view, ad) }
            )
        }
    }
}

// A settings toggle that always explains what it does in a full sentence,
// not just a two-word label — this is what the Settings dialog is built from.
@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        color = Color(0xFF334155),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(description, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = activeColor,
                    checkedTrackColor = activeColor.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// A heading that groups the How-to-Use steps, so a long guide can be skimmed
// for the one thing you came looking for instead of read end to end.
@Composable
private fun HelpSection(title: String) {
    Text(
        title.uppercase(),
        color = Color(0xFFa5b4fc),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

// ═══════════════════════════════════════════════════════════════════════
// VOICE SELECTION
//
// Two things were making the app sound robotic even on phones that have a
// good voice installed:
//
//  1. Voices were matched with `voice.locale == locale`. A Voice's locale
//     usually carries a variant the plain Locale doesn't — en_IN vs en-IN-…,
//     hi_IN with a script tag — so exact equality often matched nothing at
//     all, and the engine fell back to its default robotic voice.
//  2. Among whatever did match, `firstOrNull` took the first in an unordered
//     set rather than the best. A phone with a natural neural voice would
//     happily hand back a low-quality one.
//
// This matches on language (and prefers the same country), skips voices that
// aren't actually installed, and takes the highest quality that is left.
// ═══════════════════════════════════════════════════════════════════════
private fun pickBestVoice(
    voices: Set<android.speech.tts.Voice>?,
    locale: Locale,
    preferFemale: Boolean,
    allowNetworkVoices: Boolean
): android.speech.tts.Voice? {
    if (voices.isNullOrEmpty()) return null

    fun isFemaleName(name: String) =
        name.contains("female", true) || name.contains("woman", true) ||
                // Most engines don't say "female" — Google's voices end in a
                // letter, of which #a and #c are the female ones.
                name.contains("#female", true) || Regex("-[a-z]{2}-[a-z]-(a|c)$")
                    .containsMatchIn(name.lowercase())

    fun isMaleName(name: String) =
        name.contains("male", true) && !name.contains("female", true) ||
                name.contains("#male", true)

    val candidates = voices.filter { voice ->
        val vl = voice.locale ?: return@filter false
        vl.language.equals(locale.language, ignoreCase = true) &&
                // A voice the user hasn't downloaded will silently fail.
                !voice.features.orEmpty()
                    .contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) &&
                // With no network, a voice that needs one can't say anything at
                // all — however natural it sounds when there is one. Dropping
                // it here is what keeps the app speaking offline.
                (allowNetworkVoices || !voice.isNetworkConnectionRequired)
    }
    if (candidates.isEmpty()) return null

    // Rank rather than filter, so a preference that can't be met degrades to
    // the next best voice instead of to no voice at all.
    return candidates.maxWithOrNull(
        compareBy<android.speech.tts.Voice> { voice ->
            val female = isFemaleName(voice.name)
            val male = isMaleName(voice.name)
            when {
                preferFemale && female -> 2
                !preferFemale && male -> 2
                !female && !male -> 1   // unlabelled: acceptable either way
                else -> 0               // the gender the user didn't ask for
            }
        }
            // Same country beats a different accent of the same language.
            .thenBy { if (it.locale.country.equals(locale.country, true)) 1 else 0 }
            // Prefer a voice that keeps working when the network drops mid
            // conversation, ahead of raw quality — this app is offline-first.
            .thenBy { if (it.isNetworkConnectionRequired) 0 else 1 }
            // QUALITY_VERY_HIGH (500) down to QUALITY_VERY_LOW (100) — this is
            // what actually decides whether it sounds human.
            .thenBy { it.quality }
    )
}

// One numbered step in the How-to-Use guide.
@Composable
private fun HelpStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF6366f1)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

// ── Bottom navigation bar (the spine of the v1.5 4-screen architecture) ──
@Composable
private fun SpeechNovaBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.HOME, "🏠", "Home"),
        Triple(Screen.LEARN, "📚", "Learn"),
        Triple(Screen.PHRASES, "📖", "Phrases"),
        Triple(Screen.FACE2FACE, "🎭", "Face"),
        Triple(Screen.QUIZ, "🎮", "Quiz")
    )
    NavigationBar(
        containerColor = Color(0xFF0b1220),
        contentColor = Color.White,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        items.forEach { (screen, emoji, label) ->
            val selected = current == screen
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(screen) },
                icon = { Text(emoji, fontSize = if (selected) 20.sp else 17.sp) },
                label = {
                    Text(
                        label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color(0xFFa5b4fc),
                    unselectedIconColor = Color.White.copy(alpha = 0.55f),
                    unselectedTextColor = Color.White.copy(alpha = 0.55f),
                    indicatorColor = Color(0xFF4338ca)
                )
            )
        }
    }
}

// ── One tappable alphabet card (LEARN screen). ──
@Composable
private fun LetterCard(letter: AlphabetLetter, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF334155),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(letter.glyph, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                letter.roman,
                color = Color(0xFFa5b4fc),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text("🔊", fontSize = 12.sp)
        }
    }
}

// ── Animated microphone waveform for the Face-to-Face screen. ──
// Seven bars that gently pulse on their own (so the screen feels alive even
// in silence) and jump with the live mic level while someone is speaking.
@Composable
private fun WaveformBars(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    Row(
        modifier = modifier.height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val bars = 7
        for (i in 0 until bars) {
            val idle = 0.25f + 0.20f * abs(sin(phase + i * 0.6f))
            val fraction = if (active) (idle + level * 0.75f).coerceIn(0.12f, 1f) else 0.18f
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) Color(0xFF34d399) else Color.White.copy(alpha = 0.25f))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LEARN — vocabulary & speak-and-check pronunciation practice
// One English "master" word is translated on-device into the language being
// learned, so the same list works for every supported language.
// ═══════════════════════════════════════════════════════════════════════
data class VocabItem(
    val english: String,       // meaning shown to the learner
    val translated: String,    // the word in the target language ("" until loaded)
    val roman: String          // read-aloud romanization (Devanagari only, else "")
)

/** One multiple-choice question: an English word and four candidate
 *  translations, exactly one of which is right. */
data class QuizQuestion(
    val english: String,
    val correct: String,
    val options: List<String>
)

/** Points for a right answer. Wrong answers cost nothing — this is meant to
 *  encourage a learner, not to punish one. */
private const val QUIZ_POINTS_PER_ANSWER = 10

/** Kept short enough to finish in a spare minute. */
private const val QUIZ_LENGTH = 8

/**
 * Builds a round from whatever words are loaded. The wrong answers are drawn
 * from the same language, so the choice is a real test of the word rather than
 * of which option looks out of place.
 */
private fun buildQuiz(words: List<VocabItem>): List<QuizQuestion> {
    val usable = words.filter { it.translated.isNotBlank() && it.english.isNotBlank() }
        .distinctBy { it.english }
    // Four options need four distinct translations to choose between.
    if (usable.size < 4) return emptyList()
    return usable.shuffled().take(QUIZ_LENGTH).map { item ->
        val distractors = usable
            .filter { it.translated != item.translated }
            .shuffled()
            .take(3)
            .map { it.translated }
        QuizQuestion(
            english = item.english,
            correct = item.translated,
            options = (distractors + item.translated).shuffled()
        )
    }
}

/** Result of one pronunciation attempt. */
data class PracticeResult(
    val target: String,   // the word the learner was asked to say
    val correct: Boolean,
    val heard: String     // what speech recognition actually understood
)

// A short, high-utility set of everyday words. Kept in English and translated
// on demand, so we never hand-author (and mis-spell) 19 languages.
private val vocabularyMaster = listOf(
    "Hello", "Thank you", "Yes", "No", "Please", "Sorry",
    "Water", "Food", "Friend", "Good", "Today", "Tomorrow",
    "Money", "Help", "Doctor", "Hotel", "Airport", "Train",
    "Left", "Right", "One", "Two", "Three", "Big",
    "Small", "Hot", "Cold", "Where", "Welcome", "Goodbye"
)

/** Strip to comparable letters/digits (drops spaces, punctuation, case). */
private fun normalizeForCompare(s: String): String =
    s.lowercase().filter { it.isLetterOrDigit() }

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length]
}

private fun similar(a: String, b: String, threshold: Double): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b || a.contains(b) || b.contains(a)) return true
    val dist = levenshtein(a, b)
    val ratio = 1.0 - dist.toDouble() / maxOf(a.length, b.length)
    return ratio >= threshold
}

/** Lenient pronunciation check — speech recognition is never letter-perfect,
 *  so we accept close matches (and, for Devanagari, romanized matches too). */
private fun pronunciationMatches(target: String, heard: String, lang: String): Boolean {
    val t = normalizeForCompare(target)
    val h = normalizeForCompare(heard)
    if (similar(t, h, 0.7)) return true
    if (hasLetterLevelGuide(lang)) {
        val tr = normalizeForCompare(devanagariToLatin(target))
        val hr = normalizeForCompare(devanagariToLatin(heard))
        if (similar(tr, hr, 0.7)) return true
    }
    return false
}

@Composable
fun SpeechNovaApp(
    updateReadyToInstall: Boolean = false,
    onInstallUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val clipboardManager = LocalClipboardManager.current

    // ── Navigation ──
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    var status by remember { mutableStateOf("Initializing...") }
    var isRecording by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var learningMode by remember { mutableStateOf(false) }
    var recordingMode by remember { mutableStateOf(false) } // Instant vs Record-then-translate
    var recordedText by remember { mutableStateOf("") }
    var useFemaleVoice by remember { mutableStateOf(true) } // Toggle male/female
    var romanticMode by remember { mutableStateOf(false) } // Romantic tone
    var speechSpeed by remember { mutableFloatStateOf(0.75f) } // Slower = more natural (AI4Bharat style)
    var speechPitch by remember { mutableFloatStateOf(1.15f) }  // Warmer, friendlier pitch
    var fromLang by remember { mutableStateOf("English") }
    var toLang by remember { mutableStateOf("Hindi") }
    var isDownloading by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<TranslationMessage>() }

    // Phrasebook drill-down (now a full screen instead of a dialog)
    var selectedCategory by remember { mutableStateOf<PhraseCategory?>(null) }

    // Settings / Help / Favorites still live as dialogs launched from Home's top bar
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }

    // ── Type / paste text and translate it ──
    var showTextTranslate by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }
    var pastedDetectedLang by remember { mutableStateOf<String?>(null) }
    var pastedResult by remember { mutableStateOf("") }
    var pastedBusy by remember { mutableStateOf(false) }
    var pastedError by remember { mutableStateOf("") }

    // LEARN screen
    var learnLang by remember { mutableStateOf(toLang) }
    var learnAdDismissed by remember { mutableStateOf(false) }
    // Vocabulary (English master list, translated on-device into learnLang) plus
    // the speak-and-check pronunciation practice built on top of it.
    val vocabulary = remember { mutableStateListOf<VocabItem>() }
    var vocabLoading by remember { mutableStateOf(false) }
    var vocabLoadedLang by remember { mutableStateOf("") }
    var practicingWord by remember { mutableStateOf<String?>(null) }
    var practiceResult by remember { mutableStateOf<PracticeResult?>(null) }
    var practiceRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // ── Quiz game & the device's learners ──
    var quizQuestions by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var quizIndex by remember { mutableIntStateOf(0) }
    var quizScore by remember { mutableIntStateOf(0) }
    var quizChosen by remember { mutableStateOf<String?>(null) }
    var learnerName by remember { mutableStateOf<String?>(null) }
    var nameEntry by remember { mutableStateOf("") }
    var leaderboard by remember { mutableStateOf<List<Learner>>(emptyList()) }

    // ── Words the learner added themselves ──
    var showAddWord by remember { mutableStateOf(false) }
    var newWordEnglish by remember { mutableStateOf("") }
    var newWordTranslated by remember { mutableStateOf("") }
    var newWordBusy by remember { mutableStateOf(false) }
    var newWordError by remember { mutableStateOf("") }

    // FACE-TO-FACE (continuous conversation — free, no ad gate)
    var topBubble by remember { mutableStateOf("") }
    var bottomBubble by remember { mutableStateOf("") }
    var isFaceListening by remember { mutableStateOf(false) }
    // True from the moment a heard sentence goes off to be translated until
    // the spoken reply has finished and the echo guard has elapsed. The mic
    // stays shut for that whole window — covering the translation too, so a
    // slow translation can't let the mic open just in time to hear the reply.
    var isFaceReplying by remember { mutableStateOf(false) }
    // The last thing the phone said out loud, kept so it can be recognised
    // and discarded if the mic picks it up anyway.
    var lastFaceReply by remember { mutableStateOf("") }
    // Counts replies, so a timer belonging to an earlier one can't unlatch the
    // mic in the middle of a later one.
    var faceReplySeq by remember { mutableIntStateOf(0) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    var face2faceRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var mlTranslator by remember {
        mutableStateOf<com.google.mlkit.nl.translate.Translator?>(null)
    }
    var reverseTranslator by remember {
        mutableStateOf<com.google.mlkit.nl.translate.Translator?>(null)
    }

    val langToMLKit = mapOf(
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
        // Every language below is one ML Kit can actually translate offline.
        // Malayalam, Punjabi, Odia, Assamese and Nepali are deliberately absent
        // — ML Kit ships no model for them, and offering a language the app
        // cannot translate would be worse than not listing it.
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

    val langToSTT = mapOf(
        "English" to "en-IN",
        "Spanish" to "es-ES",
        "French" to "fr-FR",
        "German" to "de-DE",
        "Chinese" to "zh-CN",
        "Japanese" to "ja-JP",
        "Korean" to "ko-KR",
        "Arabic" to "ar-SA",
        "Russian" to "ru-RU",
        "Portuguese" to "pt-BR",
        "Italian" to "it-IT",
        "Hindi" to "hi-IN",
        "Bengali" to "bn-IN",
        "Tamil" to "ta-IN",
        "Telugu" to "te-IN",
        "Marathi" to "mr-IN",
        "Gujarati" to "gu-IN",
        "Kannada" to "kn-IN",
        "Urdu" to "ur-PK",
        "Indonesian" to "id-ID",
        "Malay" to "ms-MY",
        "Thai" to "th-TH",
        "Vietnamese" to "vi-VN",
        "Turkish" to "tr-TR",
        "Persian" to "fa-IR",
        "Hebrew" to "iw-IL",
        "Dutch" to "nl-NL",
        "Polish" to "pl-PL",
        "Ukrainian" to "uk-UA",
        "Greek" to "el-GR",
        "Swedish" to "sv-SE",
        "Danish" to "da-DK",
        "Norwegian" to "nb-NO",
        "Finnish" to "fi-FI",
        "Czech" to "cs-CZ",
        "Romanian" to "ro-RO",
        "Hungarian" to "hu-HU",
        "Swahili" to "sw-KE",
        "Filipino" to "fil-PH",
        "Afrikaans" to "af-ZA",
        "Croatian" to "hr-HR",
        "Bulgarian" to "bg-BG",
        "Slovak" to "sk-SK",
        "Catalan" to "ca-ES"
    )

    val langToTTS = mapOf(
        "English" to Locale.Builder().setLanguage("en").setRegion("IN").build(),
        "Spanish" to Locale.Builder().setLanguage("es").setRegion("ES").build(),
        "French" to Locale.FRENCH,
        "German" to Locale.GERMAN,
        "Chinese" to Locale.SIMPLIFIED_CHINESE,
        "Japanese" to Locale.JAPANESE,
        "Korean" to Locale.KOREAN,
        "Arabic" to Locale.Builder().setLanguage("ar").setRegion("SA").build(),
        "Russian" to Locale.Builder().setLanguage("ru").setRegion("RU").build(),
        "Portuguese" to Locale.Builder().setLanguage("pt").setRegion("BR").build(),
        "Italian" to Locale.ITALIAN,
        "Hindi" to Locale.Builder().setLanguage("hi").setRegion("IN").build(),
        "Bengali" to Locale.Builder().setLanguage("bn").setRegion("IN").build(),
        "Tamil" to Locale.Builder().setLanguage("ta").setRegion("IN").build(),
        "Telugu" to Locale.Builder().setLanguage("te").setRegion("IN").build(),
        "Marathi" to Locale.Builder().setLanguage("mr").setRegion("IN").build(),
        "Gujarati" to Locale.Builder().setLanguage("gu").setRegion("IN").build(),
        "Kannada" to Locale.Builder().setLanguage("kn").setRegion("IN").build(),
        "Urdu" to Locale.Builder().setLanguage("ur").setRegion("PK").build(),
        "Indonesian" to Locale.Builder().setLanguage("id").setRegion("ID").build(),
        "Malay" to Locale.Builder().setLanguage("ms").setRegion("MY").build(),
        "Thai" to Locale.Builder().setLanguage("th").setRegion("TH").build(),
        "Vietnamese" to Locale.Builder().setLanguage("vi").setRegion("VN").build(),
        "Turkish" to Locale.Builder().setLanguage("tr").setRegion("TR").build(),
        "Persian" to Locale.Builder().setLanguage("fa").setRegion("IR").build(),
        "Hebrew" to Locale.Builder().setLanguage("iw").setRegion("IL").build(),
        "Dutch" to Locale.Builder().setLanguage("nl").setRegion("NL").build(),
        "Polish" to Locale.Builder().setLanguage("pl").setRegion("PL").build(),
        "Ukrainian" to Locale.Builder().setLanguage("uk").setRegion("UA").build(),
        "Greek" to Locale.Builder().setLanguage("el").setRegion("GR").build(),
        "Swedish" to Locale.Builder().setLanguage("sv").setRegion("SE").build(),
        "Danish" to Locale.Builder().setLanguage("da").setRegion("DK").build(),
        "Norwegian" to Locale.Builder().setLanguage("nb").setRegion("NO").build(),
        "Finnish" to Locale.Builder().setLanguage("fi").setRegion("FI").build(),
        "Czech" to Locale.Builder().setLanguage("cs").setRegion("CZ").build(),
        "Romanian" to Locale.Builder().setLanguage("ro").setRegion("RO").build(),
        "Hungarian" to Locale.Builder().setLanguage("hu").setRegion("HU").build(),
        "Swahili" to Locale.Builder().setLanguage("sw").setRegion("KE").build(),
        "Filipino" to Locale.Builder().setLanguage("fil").setRegion("PH").build(),
        "Afrikaans" to Locale.Builder().setLanguage("af").setRegion("ZA").build(),
        "Croatian" to Locale.Builder().setLanguage("hr").setRegion("HR").build(),
        "Bulgarian" to Locale.Builder().setLanguage("bg").setRegion("BG").build(),
        "Slovak" to Locale.Builder().setLanguage("sk").setRegion("SK").build(),
        "Catalan" to Locale.Builder().setLanguage("ca").setRegion("ES").build()
    )

    // Indian languages first — this app's main audience — then the rest
    // alphabetically so a long list stays scannable.
    val langList = listOf(
        "Hindi", "Bengali", "Tamil", "Telugu", "Marathi",
        "Gujarati", "Kannada", "Urdu",
        "English",
        "Afrikaans", "Arabic", "Bulgarian", "Catalan", "Chinese", "Croatian",
        "Czech", "Danish", "Dutch", "Filipino", "Finnish", "French", "German",
        "Greek", "Hebrew", "Hungarian", "Indonesian", "Italian", "Japanese",
        "Korean", "Malay", "Norwegian", "Persian", "Polish", "Portuguese",
        "Romanian", "Russian", "Slovak", "Spanish", "Swahili", "Swedish",
        "Thai", "Turkish", "Ukrainian", "Vietnamese"
    )

    // Function to select appropriate voice (male/female/romantic)
    fun selectVoice(targetLang: String) {
        val locale = langToTTS[targetLang] ?: Locale.US
        tts?.language = locale

        val selectedVoice =
            pickBestVoice(tts?.voices, locale, useFemaleVoice, isOnline(context))

        if (selectedVoice != null) {
            tts?.voice = selectedVoice
            Log.i(
                "SpeechNova",
                "Selected voice: ${selectedVoice.name} quality=${selectedVoice.quality} " +
                        "(${if (useFemaleVoice) "Female" else "Male"})"
            )
        } else {
            Log.w("SpeechNova", "No installed voice matched $locale — using engine default")
        }

        if (romanticMode) {
            tts?.setSpeechRate(0.65f)
            tts?.setPitch(if (useFemaleVoice) 1.2f else 0.95f)
        } else if (learningMode || recordingMode) {
            tts?.setSpeechRate(0.65f)
            tts?.setPitch(speechPitch)
        } else {
            tts?.setSpeechRate(speechSpeed)
            tts?.setPitch(speechPitch)
        }
    }

    // LEARN: speak a single letter/syllable slowly and clearly.
    fun speakLetter(glyph: String, lang: String) {
        val locale = langToTTS[lang] ?: Locale.US
        tts?.language = locale
        tts?.setSpeechRate(0.6f)
        tts?.setPitch(1.0f)
        tts?.speak(glyph, TextToSpeech.QUEUE_FLUSH, null, "letter")
    }

    // OFFLINE-CAPABLE DOWNLOAD - Downloads BOTH directions
    fun downloadModel(from: String, to: String) {
        isReady = false
        isDownloading = true
        status = "📥 Downloading $from ↔ $to..."
        mlTranslator?.close()
        reverseTranslator?.close()

        val fromCode = langToMLKit[from] ?: TranslateLanguage.ENGLISH
        val toCode = langToMLKit[to] ?: TranslateLanguage.HINDI

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(fromCode)
            .setTargetLanguage(toCode)
            .build()
        val newTranslator = Translation.getClient(options)
        mlTranslator = newTranslator

        val reverseOptions = TranslatorOptions.Builder()
            .setSourceLanguage(toCode)
            .setTargetLanguage(fromCode)
            .build()
        val newReverseTranslator = Translation.getClient(reverseOptions)
        reverseTranslator = newReverseTranslator

        val downloadConditions = DownloadConditions.Builder().build()

        fun checkOfflineAvailability() {
            isDownloading = false
            handler.postDelayed({
                newTranslator.translate("test")
                    .addOnSuccessListener {
                        Log.i("SpeechNova", "✓ Offline: $from → $to")
                        newReverseTranslator.translate("test")
                            .addOnSuccessListener {
                                Log.i("SpeechNova", "✓ Offline: $to → $from")
                                isReady = true
                                status = "✅ Offline (both ways)!"
                                messages.clear()
                                messages.add(
                                    TranslationMessage(
                                        displayText = "✅ Offline mode: $from ↔ $to both work!",
                                        type = "system"
                                    )
                                )
                            }
                            .addOnFailureListener {
                                Log.w("SpeechNova", "✗ Missing: $to → $from")
                                isReady = true
                                status = "⚠️ Only $from → $to offline"
                                messages.clear()
                                messages.add(
                                    TranslationMessage(
                                        displayText = "⚠️ Only $from → $to works offline. Connect internet to download $to → $from.",
                                        type = "system"
                                    )
                                )
                            }
                    }
                    .addOnFailureListener {
                        Log.e("SpeechNova", "✗ No offline models")
                        isReady = false
                        status = "⚠️ Connect internet"
                        messages.clear()
                        messages.add(
                            TranslationMessage(
                                displayText = "⚠️ No offline models. Connect internet to download $from ↔ $to (both directions).",
                                type = "system"
                            )
                        )
                    }
            }, 800)
        }

        newTranslator.downloadModelIfNeeded(downloadConditions)
            .addOnSuccessListener {
                Log.i("SpeechNova", "✓ Downloaded: $from → $to")
                newReverseTranslator.downloadModelIfNeeded(downloadConditions)
                    .addOnSuccessListener {
                        Log.i("SpeechNova", "✓ Downloaded: $to → $from")
                        isDownloading = false
                        status = "✅ Both ways work offline!"
                        isReady = true
                        messages.clear()
                        messages.add(
                            TranslationMessage(
                                displayText = "✅ $from ↔ $to ready! Works offline both ways.",
                                type = "system"
                            )
                        )
                    }
                    .addOnFailureListener { reverseError ->
                        Log.w("SpeechNova", "Reverse download failed: ${reverseError.message}")
                        checkOfflineAvailability()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SpeechNova", "Primary download failed: ${e.message}")
                checkOfflineAvailability()
            }
    }

    fun stopRecording() {
        isRecording = false
        isSpeaking = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
        handler.removeCallbacksAndMessages(null)
        status = "✅ Stopped"
    }

    fun repeatSpeech(text: String, targetLang: String) {
        if (isRecording) stopRecording()
        selectVoice(targetLang)

        var processedText = text.trim()
        if (!processedText.endsWith(".") && !processedText.endsWith("?") &&
            !processedText.endsWith("!") && !processedText.endsWith("।")) {
            processedText += "."
        }

        val textWithPauses = processedText
            .replace(". ", ".   ")
            .replace("। ", "।   ")
            .replace("? ", "?   ")
            .replace("! ", "!   ")
            .replace(", ", ",  ")
            .trim()

        tts?.speak(textWithPauses, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun repeatAllConversation() {
        if (isRecording) stopRecording()
        val translationMessages = messages.filter { it.type == "translation" }
        if (translationMessages.isEmpty()) return

        isSpeaking = true
        var delayMs = 0L

        translationMessages.forEach { msg ->
            handler.postDelayed({
                repeatSpeech(msg.translatedText, toLang)
            }, delayMs)
            delayMs += (msg.translatedText.length * 80L) + 1000
        }

        handler.postDelayed({
            isSpeaking = false
        }, delayMs)
    }

    // ── Copy / Share helpers (used by message bubbles & Favorites dialog) ──
    fun copyToClipboard(text: String) {
        clipboardManager.setText(AnnotatedString(text))
        status = "📋 Copied to clipboard"
    }

    fun shareText(text: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Share translation").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (_: Exception) { /* no-op */ }
    }

    // ── PHRASEBOOK: translate & speak a pre-written phrase (English master list) ──
    fun finalizePhraseTranslation(original: String, translatedRaw: String) {
        var t = translatedRaw.trim()
        if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!") && !t.endsWith("।")) t += "."
        val learningExtra = if (learningMode) buildLearningGuide(t, toLang, original) else ""
        messages.add(
            TranslationMessage(
                displayText = t,
                originalText = original,
                type = "translation",
                translatedText = t,
                learningExtra = learningExtra
            )
        )
        selectVoice(toLang)
        val textWithPauses = t
            .replace(". ", ".   ")
            .replace("। ", "।   ")
            .replace("? ", "?   ")
            .replace("! ", "!   ")
            .replace(", ", ",  ")
            .trim()
        tts?.speak(textWithPauses, TextToSpeech.QUEUE_FLUSH, null, null)
        status = "✅ Phrase spoken"
    }

    // ── TYPE / PASTE TEXT: work out what language it is, then translate it ──
    // The source language is detected rather than assumed, so you can paste
    // something in a language you can't even name and still get it translated.
    fun translatePastedText() {
        val source = pastedText.trim()
        if (source.isEmpty()) {
            pastedError = "Type or paste something first"
            return
        }
        pastedBusy = true
        pastedError = ""
        pastedResult = ""

        LanguageDetection.detect(source) { detected ->
            pastedDetectedLang = detected
            // Detection declines on very short or ambiguous text; the language
            // picked on Home is the sensible fallback.
            val sourceLang = detected ?: fromLang

            if (sourceLang == toLang) {
                pastedResult = source
                pastedBusy = false
                return@detect
            }

            val fromCode = langToMLKit[sourceLang]
            val toCode = langToMLKit[toLang]
            if (fromCode == null || toCode == null) {
                pastedError = "Can't translate $sourceLang → $toLang yet"
                pastedBusy = false
                return@detect
            }

            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(fromCode)
                    .setTargetLanguage(toCode)
                    .build()
            )
            // The detected language may be one whose pack was never downloaded
            // — this pair is chosen by the text, not by the Home pickers.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    translator.translate(source)
                        .addOnSuccessListener { out ->
                            pastedResult = out.trim()
                            pastedBusy = false
                            translator.close()
                        }
                        .addOnFailureListener {
                            pastedError = "Translation failed — please try again"
                            pastedBusy = false
                            translator.close()
                        }
                }
                .addOnFailureListener {
                    pastedError =
                        "Couldn't download the $sourceLang language pack — connect to the internet once and try again"
                    pastedBusy = false
                    translator.close()
                }
        }
    }

    fun translatePhraseAndSpeak(englishPhrase: String) {
        if (isRecording) stopRecording()
        messages.add(TranslationMessage(displayText = englishPhrase, type = "you"))
        status = "🌍 Translating phrase..."

        when {
            fromLang == "English" -> {
                mlTranslator?.translate(englishPhrase)
                    ?.addOnSuccessListener { translated -> finalizePhraseTranslation(englishPhrase, translated) }
                    ?.addOnFailureListener { status = "❌ Phrase translation failed" }
            }
            toLang == "English" -> {
                // Phrase is already in the target language — no translation needed.
                finalizePhraseTranslation(englishPhrase, englishPhrase)
            }
            else -> {
                // Neither side is English: spin up a one-off English → toLang translator.
                val oneOffOptions = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(langToMLKit[toLang] ?: TranslateLanguage.HINDI)
                    .build()
                val oneOff = Translation.getClient(oneOffOptions)
                oneOff.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        oneOff.translate(englishPhrase)
                            .addOnSuccessListener { translated ->
                                finalizePhraseTranslation(englishPhrase, translated)
                                oneOff.close()
                            }
                            .addOnFailureListener {
                                status = "❌ Phrase translation failed"
                                oneOff.close()
                            }
                    }
                    .addOnFailureListener {
                        status = "❌ Couldn't download phrase language pack"
                        oneOff.close()
                    }
            }
        }
    }

    // ── CAMERA OCR: scan printed text and translate it ──
    // Google's on-device text recognizer only ships accurate models for a
    // subset of scripts. We check honestly rather than silently failing.
    fun scriptSupportsOcr(lang: String): Boolean = lang in setOf(
        "English", "Spanish", "French", "German", "Portuguese", "Italian", // Latin script
        "Hindi", "Marathi",   // Devanagari
        "Chinese", "Japanese", "Korean"
    )

    fun getOcrRecognizer(lang: String): TextRecognizer = when (lang) {
        "Hindi", "Marathi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        "Chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        "Japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        "Korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun scanTextAndTranslate(scan: CapturedScan) {
        if (!scriptSupportsOcr(fromLang)) {
            messages.add(
                TranslationMessage(
                    displayText = "📷 Camera scan isn't available for $fromLang yet — the on-device reader doesn't support that script. Try speaking instead, or use 📖 Phrases.",
                    type = "system"
                )
            )
            scan.bitmap.recycle()
            return
        }
        status = "📷 Reading text..."
        // The recognizer applies the rotation itself, so a photo taken with the
        // phone on its side still reads as upright text.
        val image = InputImage.fromBitmap(scan.bitmap, scan.rotationDegrees)
        val recognizer = getOcrRecognizer(fromLang)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.toReadableText()
                if (recognizedText.isEmpty()) {
                    status = "❌ No text found — move closer and try again"
                    return@addOnSuccessListener
                }
                messages.add(TranslationMessage(displayText = recognizedText, type = "you"))
                status = "🌍 Translating scanned text..."
                if (fromLang == toLang) {
                    finalizePhraseTranslation(recognizedText, recognizedText)
                } else {
                    mlTranslator?.translate(recognizedText)
                        ?.addOnSuccessListener { translated -> finalizePhraseTranslation(recognizedText, translated) }
                        ?.addOnFailureListener { status = "❌ Translation failed" }
                }
            }
            .addOnFailureListener {
                status = "❌ Couldn't read the image, try again"
            }
            // One recognizer per scan, closed once the scan is done — the
            // native model it holds is far too big to leak per photo.
            .addOnCompleteListener {
                recognizer.close()
                scan.bitmap.recycle()
            }
    }

    // ── FACE-TO-FACE: continuous, hands-free conversation ──
    // Should we keep the continuous loop alive right now?
    fun shouldFaceListen(): Boolean =
        currentScreen == Screen.FACE2FACE &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun stopFaceToFaceListening() {
        isFaceListening = false
        micLevel = 0f
        face2faceRecognizer?.destroy()
        face2faceRecognizer = null
        // Leaving the screen mid-sentence shouldn't leave the phone talking to
        // an empty room, and the reply state must not stay latched or the loop
        // would refuse to listen when we come back. Only our own reply is cut
        // short — the Home screen speaks through the same engine and must not
        // be silenced by someone switching tabs.
        if (isFaceReplying) {
            tts?.stop()
            isFaceReplying = false
        }
        faceReplySeq++
        lastFaceReply = ""
    }

    fun startFaceToFaceListening() {
        if (!shouldFaceListen()) { stopFaceToFaceListening(); return }
        // Never open the mic while the phone is mid-reply — this is the guard
        // that stops the app hearing and re-translating its own voice.
        if (isFaceReplying) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "❌ Speech recognition not available"
            return
        }
        face2faceRecognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        face2faceRecognizer = sr
        isFaceListening = true

        // Re-arm the continuous loop (recursively) unless we've left the
        // screen. While the phone is replying this waits rather than giving
        // up, so the conversation resumes the moment it stops talking.
        fun reArm(delayMs: Long) {
            handler.postDelayed({
                when {
                    !shouldFaceListen() -> stopFaceToFaceListening()
                    isFaceReplying -> reArm(FACE_SPEAK_POLL_MS)
                    else -> startFaceToFaceListening()
                }
            }, delayMs)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isFaceListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                micLevel = (rmsdB.coerceIn(0f, 10f)) / 10f
            }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { micLevel = 0f }
            override fun onError(error: Int) {
                micLevel = 0f
                sr.destroy()
                if (face2faceRecognizer === sr) face2faceRecognizer = null
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        isFaceListening = false
                        status = "❌ Microphone permission denied"
                    }
                    else -> reArm(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 700 else 350)
                }
            }

            override fun onResults(results: Bundle?) {
                micLevel = 0f
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()

                sr.destroy()
                if (face2faceRecognizer === sr) face2faceRecognizer = null

                // Did we just hear ourselves? The mic is shut while the phone
                // talks, but a loud speaker in a small room can still bleed
                // into the tail of a recognition that was already running.
                if (!text.isNullOrBlank() && soundsLikeOurOwnVoice(text, lastFaceReply)) {
                    Log.i("SpeechNova", "Face-to-Face: ignored our own voice echoing back")
                    reArm(FACE_ECHO_GUARD_MS)
                    return
                }

                if (!text.isNullOrBlank()) {
                    bottomBubble = text
                    // Latch *before* translating: a slow translation must not
                    // leave a window where the mic reopens just in time to
                    // hear the reply it is about to produce.
                    isFaceReplying = true
                    faceReplySeq++
                    val replySeq = faceReplySeq
                    val translator = mlTranslator
                    // No translator configured — don't latch the mic shut.
                    if (translator == null) isFaceReplying = false
                    translator?.translate(text)
                        ?.addOnSuccessListener { translated ->
                            var t = translated.trim()
                            if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!")) t += "."
                            topBubble = t
                            lastFaceReply = t
                            selectVoice(toLang)
                            tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, FACE_UTTERANCE_ID)
                            // If the engine never reports that it finished,
                            // release the mic anyway rather than going deaf —
                            // but only if this is still the reply in progress.
                            handler.postDelayed({
                                if (isFaceReplying && faceReplySeq == replySeq) {
                                    Log.w("SpeechNova", "Face-to-Face: TTS never reported done")
                                    isFaceReplying = false
                                }
                            }, faceSpeechWatchdogMs(t))
                        }
                        ?.addOnFailureListener {
                            status = "❌ Translation failed"
                            if (faceReplySeq == replySeq) isFaceReplying = false
                        }
                }
                reArm(600)
            }

            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                // Don't echo our own reply back into the speaker's bubble.
                if (!t.isNullOrBlank() && !soundsLikeOurOwnVoice(t, lastFaceReply)) {
                    bottomBubble = t
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langToSTT[fromLang] ?: "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            // Without this the recognizer streams audio to Google's servers
            // and just fails when there's no network. Only asked for when
            // actually offline: preferring offline while online would give up
            // the better online model for nothing.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline(context))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, true)
            }
        }
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            isFaceListening = false
            reArm(800)
        }
    }

    val appLogic = remember {
        object {
            fun startRecognizer(currentFromLang: String, currentToLang: String) {
                if (isSpeaking) return

                recognizer?.destroy()
                recognizer = null

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    status = "❌ Speech recognition not available"
                    isRecording = false
                    return
                }

                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer = sr

                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        status = if (recordingMode) "🔴 Recording..." else "🎤 Speak now!"
                    }

                    override fun onBeginningOfSpeech() {
                        status = if (recordingMode) "🔴 Recording..." else "🎤 Hearing you..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        when {
                            rmsdB < 2f -> return
                            rmsdB > 20f -> {
                                status = "🔊 Too loud"
                                return
                            }
                            else -> {
                                val bars = "█".repeat(rmsdB.toInt().coerceIn(0, 8))
                                val empty = "░".repeat(8 - bars.length)
                                status = if (recordingMode) "🔴 [$bars$empty]" else "🎤 [$bars$empty]"
                            }
                        }
                    }

                    override fun onEndOfSpeech() {
                        status = "⚡ Processing..."
                    }

                    override fun onError(error: Int) {
                        when (error) {
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                if (isRecording && !isSpeaking) handler.postDelayed({
                                    startRecognizer(currentFromLang, currentToLang)
                                }, 1500)
                            }
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                if (isRecording && !isSpeaking) handler.postDelayed({
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE,
                                            langToSTT[currentFromLang] ?: "en-IN"
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                        putExtra("android.speech.extra.DICTATION_MODE", true)
                                        putExtra(
                                            RecognizerIntent.EXTRA_PREFER_OFFLINE,
                                            !isOnline(context)
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, true)
                                        }
                                    }
                                    try {
                                        sr.startListening(intent)
                                    } catch (_: Exception) {
                                    }
                                }, if (recordingMode) 100 else 300)
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                status = "❌ Microphone permission denied"
                                isRecording = false
                            }
                            else -> {
                                if (isRecording && !isSpeaking) handler.postDelayed({
                                    startRecognizer(currentFromLang, currentToLang)
                                }, 1000)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim()

                        if (!text.isNullOrBlank() && !isSpeaking) {
                            if (recordingMode) {
                                recordedText = if (recordedText.isEmpty()) text else "$recordedText $text"
                                status = "🔴 Recording... Tap STOP when done"

                                if (messages.isNotEmpty() && messages.last().type == "you") {
                                    messages.removeAt(messages.lastIndex)
                                }
                                messages.add(
                                    TranslationMessage(
                                        displayText = recordedText,
                                        type = "you"
                                    )
                                )

                                if (isRecording) {
                                    handler.postDelayed({
                                        startRecognizer(currentFromLang, currentToLang)
                                    }, 100)
                                }
                            } else {
                                translateAndSpeak(text, currentToLang)
                            }
                        }
                    }

                    override fun onPartialResults(partial: Bundle?) {
                        if (!isSpeaking) {
                            val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                            if (!t.isNullOrBlank()) {
                                status = if (recordingMode) "🔴 \"$t\"" else "🎤 \"$t\""
                            }
                        }
                    }

                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEvent(type: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langToSTT[currentFromLang] ?: "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline(context))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, true)
                    }
                }
                try {
                    sr.startListening(intent)
                } catch (e: Exception) {
                    status = "❌ Failed to start: ${e.message}"
                    isRecording = false
                }
            }

            fun translateAndSpeak(spoken: String, targetLang: String) {
                if (!recordingMode) {
                    messages.add(
                        TranslationMessage(
                            displayText = spoken,
                            type = "you"
                        )
                    )
                }

                // Android's recognizer has to be told which language to expect,
                // so it will happily transcribe the wrong one into nonsense.
                // Checking what language the words actually came out as catches
                // the common "I picked the wrong source language" mistake.
                LanguageDetection.detect(spoken) { detected ->
                    if (detected != null && detected != fromLang) {
                        messages.add(
                            TranslationMessage(
                                displayText = "🔎 That sounded like $detected, not $fromLang. " +
                                        "If the translation looks wrong, change the language on the left to $detected.",
                                type = "hint"
                            )
                        )
                    }
                }

                status = "🌍 Translating..."
                recognizer?.stopListening()

                mlTranslator?.translate(spoken)
                    ?.addOnSuccessListener { translated ->
                        var translatedWithPunc = translated.trim()
                        if (!translatedWithPunc.endsWith(".") && !translatedWithPunc.endsWith("?") &&
                            !translatedWithPunc.endsWith("!") && !translatedWithPunc.endsWith("।") &&
                            !translatedWithPunc.endsWith("।।")) {
                            translatedWithPunc += "."
                        }

                        val learningExtra = if (learningMode) {
                            buildLearningGuide(translatedWithPunc, targetLang, spoken)
                        } else ""

                        messages.add(
                            TranslationMessage(
                                displayText = translatedWithPunc,
                                originalText = spoken,
                                type = "translation",
                                translatedText = translatedWithPunc,
                                learningExtra = learningExtra
                            )
                        )

                        selectVoice(targetLang)

                        if (romanticMode) {
                            messages.add(
                                TranslationMessage(
                                    displayText = "✨ Tone mode: Soft and clear voice",
                                    type = "hint"
                                )
                            )
                        }

                        val textWithPauses = translatedWithPunc
                            .replace(". ", ".   ")
                            .replace("। ", "।   ")
                            .replace("? ", "?   ")
                            .replace("! ", "!   ")
                            .replace(", ", ",  ")
                            .trim()

                        isSpeaking = true
                        tts?.speak(textWithPauses, TextToSpeech.QUEUE_FLUSH, null, null)

                        // Continuous mode re-opens the mic after the reply. The
                        // duration here is only an estimate, and reopening
                        // early means the phone hears itself and translates
                        // its own voice — so if the engine says it is still
                        // speaking, wait and ask again instead of guessing.
                        val speechDuration = textWithPauses.length * 120L

                        // Bounded so a stuck engine can't leave the mic off
                        // for the rest of the session.
                        fun resumeListeningWhenQuiet(attemptsLeft: Int) {
                            if (attemptsLeft > 0 && tts?.isSpeaking == true) {
                                handler.postDelayed(
                                    { resumeListeningWhenQuiet(attemptsLeft - 1) }, 200L
                                )
                                return
                            }
                            isSpeaking = false
                            if (isRecording && !recordingMode) {
                                status = "🎤 Listening..."
                                startRecognizer(fromLang, toLang)
                            }
                        }

                        handler.postDelayed(
                            { resumeListeningWhenQuiet(150) }, speechDuration + 500
                        )
                    }
                    ?.addOnFailureListener { e ->
                        Log.e("SpeechNova", "Translation failed: ${e.message}")
                        messages.add(
                            TranslationMessage(
                                displayText = "Translation failed: ${e.message}",
                                type = "translation"
                            )
                        )
                        isSpeaking = false
                        if (isRecording && !recordingMode) {
                            status = "🎤 Listening..."
                            startRecognizer(fromLang, toLang)
                        }
                    }
            }
        }
    }

    fun startRecognizer(f: String, t: String) = appLogic.startRecognizer(f, t)
    fun translateAndSpeak(s: String, t: String) = appLogic.translateAndSpeak(s, t)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) status = "✅ Permission granted!"
        else {
            status = "❌ Microphone permission denied"
            isRecording = false
        }
    }

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // TakePicture writes the full-resolution photo to a file we own. The old
    // TakePicturePreview contract handed back the camera's thumbnail instead,
    // which is far too small for the recognizer to read more than the single
    // largest word on the page.
    var pendingScanUri by remember { mutableStateOf<Uri?>(null) }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured: Boolean ->
        val uri = pendingScanUri
        pendingScanUri = null
        when {
            !captured || uri == null -> status = "📷 Scan cancelled"
            else -> {
                val scan = decodeCapturedScan(context, uri)
                if (scan == null) status = "❌ Couldn't read the photo, try again"
                else scanTextAndTranslate(scan)
            }
        }
    }

    fun launchCameraScan() {
        val uri = createScanCaptureUri(context)
        if (uri == null) {
            status = "❌ Couldn't open the camera — no space to save the photo"
            return
        }
        pendingScanUri = uri
        cameraCaptureLauncher.launch(uri)
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraScan()
        } else {
            status = "❌ Camera permission denied"
        }
    }

    // ── LEARN: build the vocabulary list for a language (translate on-device) ──
    fun loadVocabulary(lang: String) {
        if (vocabLoadedLang == lang && vocabulary.isNotEmpty()) return
        vocabLoadedLang = lang
        practiceResult = null
        vocabulary.clear()
        // The learner's own words sit alongside the built-in list, so anything
        // they added is practised and quizzed exactly like the rest.
        val custom = Progress.customWords(context, lang)
        if (lang == "English") {
            vocabulary.addAll(vocabularyMaster.map { VocabItem(it, it, "") })
            vocabulary.addAll(custom.map { VocabItem(it.english, it.english, "") })
            vocabLoading = false
            return
        }
        vocabLoading = true
        vocabulary.addAll(vocabularyMaster.map { VocabItem(it, "", "") })
        // These are already translated — they were translated when added.
        vocabulary.addAll(custom.map { VocabItem(it.english, it.translated, "") })
        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(langToMLKit[lang] ?: TranslateLanguage.HINDI)
            .build()
        val tr = Translation.getClient(opts)
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                var remaining = vocabularyMaster.size
                vocabularyMaster.forEachIndexed { i, word ->
                    tr.translate(word)
                        .addOnSuccessListener { translated ->
                            // Ignore stale results if the learner switched language.
                            if (vocabLoadedLang == lang && i < vocabulary.size) {
                                val t = translated.trim()
                                val roman = if (hasLetterLevelGuide(lang)) devanagariToLatin(t) else ""
                                vocabulary[i] = VocabItem(word, t, roman)
                            }
                            if (--remaining == 0) { vocabLoading = false; tr.close() }
                        }
                        .addOnFailureListener {
                            if (--remaining == 0) { vocabLoading = false; tr.close() }
                        }
                }
            }
            .addOnFailureListener {
                vocabLoading = false
                tr.close()
            }
    }

    // ── QUIZ GAME ──
    fun refreshLeaderboard() {
        leaderboard = Progress.leaderboard(context)
    }

    fun startQuiz() {
        learnerName = Progress.currentLearner(context)
        refreshLeaderboard()
        quizQuestions = buildQuiz(vocabulary)
        quizIndex = 0
        quizScore = 0
        quizChosen = null
    }

    /** Scores the tapped option and moves on. The choice is remembered so the
     *  answer can be shown as right or wrong before the next question. */
    fun answerQuiz(option: String) {
        if (quizChosen != null) return // already answered this one
        quizChosen = option
        val question = quizQuestions.getOrNull(quizIndex) ?: return
        if (option == question.correct) {
            quizScore += QUIZ_POINTS_PER_ANSWER
        }
    }

    fun nextQuizQuestion() {
        quizChosen = null
        if (quizIndex < quizQuestions.size - 1) {
            quizIndex++
        } else {
            // Round over — bank the points and refresh the board.
            quizIndex = quizQuestions.size
            Progress.addPoints(context, quizScore)
            refreshLeaderboard()
        }
    }

    fun saveLearnerName() {
        val name = nameEntry.trim()
        if (name.isEmpty()) return
        Progress.setCurrentLearner(context, name)
        learnerName = Progress.currentLearner(context)
        nameEntry = ""
        refreshLeaderboard()
    }

    // ── ADD YOUR OWN WORD ──
    // Translated once, when it is added, so it can be practised offline
    // afterwards exactly like a built-in word.
    fun saveNewWord() {
        val english = newWordEnglish.trim()
        if (english.isEmpty()) {
            newWordError = "Type a word first"
            return
        }
        newWordBusy = true
        newWordError = ""

        fun store(translated: String) {
            val added = Progress.addCustomWord(
                context,
                CustomWord(english = english, language = learnLang, translated = translated)
            )
            newWordBusy = false
            if (!added) {
                newWordError = "\"$english\" is already in your list"
                return
            }
            newWordTranslated = translated
            newWordEnglish = ""
            // Force a rebuild so the new word shows up in the list right away.
            vocabLoadedLang = ""
            loadVocabulary(learnLang)
        }

        if (learnLang == "English") {
            store(english)
            return
        }
        val code = langToMLKit[learnLang]
        if (code == null) {
            newWordBusy = false
            newWordError = "Can't translate into $learnLang yet"
            return
        }
        val tr = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(code)
                .build()
        )
        tr.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                tr.translate(english)
                    .addOnSuccessListener { translated ->
                        store(translated.trim())
                        tr.close()
                    }
                    .addOnFailureListener {
                        newWordBusy = false
                        newWordError = "Couldn't translate that word"
                        tr.close()
                    }
            }
            .addOnFailureListener {
                newWordBusy = false
                newWordError =
                    "The $learnLang language pack isn't downloaded — connect to the internet once and try again"
                tr.close()
            }
    }

    fun deleteCustomWord(english: String) {
        Progress.removeCustomWord(context, learnLang, english)
        vocabLoadedLang = ""
        loadVocabulary(learnLang)
    }

    fun stopPractice() {
        practiceRecognizer?.destroy()
        practiceRecognizer = null
        practicingWord = null
    }

    // ── LEARN: listen to the learner say a word and tell them if it was right ──
    fun practiceWord(target: String, lang: String) {
        if (target.isBlank()) return
        if (!hasMicPermission()) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            practiceResult = PracticeResult(target, false, "(speech recognition unavailable)")
            return
        }
        if (isRecording) stopRecording()
        practiceRecognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        practiceRecognizer = sr
        practicingWord = target
        practiceResult = null

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (practiceRecognizer === sr) practiceRecognizer = null
                practicingWord = null
                practiceResult = PracticeResult(target, false, "(didn't catch that — try again)")
                sr.destroy()
            }

            override fun onResults(results: Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                val correct = pronunciationMatches(target, heard, lang)
                practicingWord = null
                practiceResult = PracticeResult(target, correct, heard)
                if (practiceRecognizer === sr) practiceRecognizer = null
                sr.destroy()
                // Always play the correct pronunciation back so they can learn it.
                selectVoice(lang)
                tts?.setSpeechRate(0.6f)
                tts?.speak(target, TextToSpeech.QUEUE_FLUSH, null, "practice")
            }

            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langToSTT[lang] ?: "en-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline(context))
        }
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            practicingWord = null
            if (practiceRecognizer === sr) practiceRecognizer = null
        }
    }

    // The phone has finished (or given up on) speaking a Face-to-Face reply.
    // The mic stays shut for a moment longer so the speaker's tail and the
    // room's echo aren't heard as the next sentence.
    fun onFaceReplyFinished(utteranceId: String?) {
        if (utteranceId != FACE_UTTERANCE_ID) return
        // If a newer reply starts during the guard, leave the mic shut for it.
        val finishedSeq = faceReplySeq
        handler.postDelayed({
            if (faceReplySeq == finishedSeq) isFaceReplying = false
        }, FACE_ECHO_GUARD_MS)
    }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) {
                Log.i("SpeechNova", "TTS initialized successfully")
            }
        }

        // Callbacks arrive on a binder thread, so every one of these hops back
        // to the main thread before touching state.
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                handler.post { onFaceReplyFinished(utteranceId) }
            }

            @Deprecated("Required by UtteranceProgressListener; superseded by onError(String, Int)")
            override fun onError(utteranceId: String?) {
                handler.post { onFaceReplyFinished(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handler.post { onFaceReplyFinished(utteranceId) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                handler.post { onFaceReplyFinished(utteranceId) }
            }
        })

        if (!hasMicPermission()) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        downloadModel(fromLang, toLang)
        showHelp = true // first thing a beginner sees: how to use the app
    }

    // Drive the continuous Face-to-Face loop from the current screen *and* the
    // language pair. Keying on the languages is what makes swapping work
    // without leaving the screen: the recognizer is created for one specific
    // language, so a swap has to tear the old one down and start a fresh one.
    LaunchedEffect(currentScreen, fromLang, toLang) {
        if (currentScreen == Screen.FACE2FACE && hasMicPermission()) {
            topBubble = ""
            bottomBubble = ""
            // Drop the recognizer listening in the previous language, then give
            // the speech service a moment before asking for a new one — back to
            // back destroy/create otherwise comes back RECOGNIZER_BUSY.
            stopFaceToFaceListening()
            delay(250)
            startFaceToFaceListening()
        } else {
            stopFaceToFaceListening()
        }
    }

    // Build the vocabulary list the first time LEARN is opened for a language.
    LaunchedEffect(currentScreen, learnLang) {
        if (currentScreen == Screen.LEARN) loadVocabulary(learnLang)
    }

    DisposableEffect(Unit) {
        onDispose {
            isRecording = false
            isSpeaking = false
            tts?.shutdown()
            recognizer?.destroy()
            face2faceRecognizer?.destroy()
            practiceRecognizer?.destroy()
            mlTranslator?.close()
            reverseTranslator?.close()
            handler.removeCallbacksAndMessages(null)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ROOT LAYOUT: gradient background · scrollable screen (weight 1f)
    // · banner ad · bottom navigation. The ad sits above the nav so the
    // four destinations are always the very last, easy-to-reach row.
    // ═══════════════════════════════════════════════════════════
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0f172a),
                        Color(0xFF1e293b)
                    )
                )
            )
            .systemBarsPadding()
    ) {

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentScreen) {
                Screen.HOME -> HomeScreenContent(
                    status = status,
                    isReady = isReady,
                    isDownloading = isDownloading,
                    isRecording = isRecording,
                    recordingMode = recordingMode,
                    recordedText = recordedText,
                    fromLang = fromLang,
                    toLang = toLang,
                    langList = langList,
                    messages = messages,
                    onShowHelp = { showHelp = true },
                    onShowFavorites = { showFavorites = true },
                    onShowSettings = { showSettings = true },
                    onPickFrom = { lang ->
                        fromLang = lang
                        if (isRecording) stopRecording()
                        downloadModel(fromLang, toLang)
                    },
                    onPickTo = { lang ->
                        toLang = lang
                        learnLang = lang
                        if (isRecording) stopRecording()
                        downloadModel(fromLang, toLang)
                    },
                    onSwapLangs = {
                        val f = fromLang; fromLang = toLang; toLang = f
                        learnLang = toLang
                        if (isRecording) stopRecording()
                        downloadModel(fromLang, toLang)
                    },
                    onScanCamera = {
                        if (!hasCameraPermission()) cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        else launchCameraScan()
                    },
                    onTranslateText = {
                        pastedResult = ""
                        pastedError = ""
                        pastedDetectedLang = null
                        showTextTranslate = true
                    },
                    onPlayAll = { repeatAllConversation() },
                    onListen = { text -> repeatSpeech(text, toLang) },
                    onCopy = { text -> copyToClipboard(text) },
                    onShare = { text -> shareText(text) },
                    onPrimaryButton = {
                        if (isRecording) {
                            stopRecording()
                        } else {
                            if (!hasMicPermission()) {
                                permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                isRecording = true
                                if (!recordingMode) messages.clear()
                                recordedText = ""
                                status = if (recordingMode) "🔴 Recording..." else "🎤 Starting..."
                                startRecognizer(fromLang, toLang)
                            }
                        }
                    },
                    onTranslateRecorded = {
                        if (recordedText.isNotEmpty()) {
                            stopRecording()
                            translateAndSpeak(recordedText, toLang)
                            recordedText = ""
                        }
                    }
                )

                Screen.LEARN -> LearnScreenContent(
                    learnLang = learnLang,
                    langList = langList,
                    adDismissed = learnAdDismissed,
                    vocabulary = vocabulary,
                    vocabLoading = vocabLoading,
                    practicingWord = practicingWord,
                    practiceResult = practiceResult,
                    onDismissAd = { learnAdDismissed = true },
                    onPickLang = {
                        stopPractice()
                        learnLang = it
                    },
                    onSpeakLetter = { glyph -> speakLetter(glyph, learnLang) },
                    onHearWord = { word -> speakLetter(word, learnLang) },
                    onPracticeWord = { word -> practiceWord(word, learnLang) },
                    onPlayQuiz = { startQuiz(); currentScreen = Screen.QUIZ },
                    onAddWord = {
                        newWordTranslated = ""
                        newWordError = ""
                        showAddWord = true
                    },
                    customWords = remember(learnLang, vocabulary.size) {
                        Progress.customWords(context, learnLang)
                            .map { it.english }
                            .toSet()
                    },
                    onDeleteWord = { word -> deleteCustomWord(word) }
                )

                Screen.PHRASES -> PhrasesScreenContent(
                    toLang = toLang,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    onBack = { selectedCategory = null },
                    onPickPhrase = { phrase ->
                        translatePhraseAndSpeak(phrase)
                        selectedCategory = null
                        currentScreen = Screen.HOME
                    }
                )

                Screen.QUIZ -> QuizScreenBody {
                    Text(
                        "🎮 $learnLang quiz",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    when {
                        // Asked once per device, then never again.
                        learnerName == null -> {
                            Text(
                                "What shall we call you? This is asked once and stays on this phone \u2014 it is never sent anywhere.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = nameEntry,
                                onValueChange = { nameEntry = it.take(20) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        "Your name",
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                },
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontSize = 16.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10b981),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    cursorColor = Color(0xFF10b981)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { saveLearnerName() },
                                enabled = nameEntry.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10b981),
                                    disabledContainerColor = Color(0xFF374151)
                                )
                            ) {
                                Text("Start playing", fontWeight = FontWeight.Bold)
                            }
                        }

                        quizQuestions.isEmpty() -> {
                            Text(
                                "There aren't enough words loaded yet to make a quiz. Wait for the word list to finish loading, or add a few of your own words first.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        // Round finished: score, then who's ahead.
                        quizIndex >= quizQuestions.size -> {
                            Text("\uD83C\uDF89", fontSize = 44.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "You scored $quizScore",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "out of ${quizQuestions.size * QUIZ_POINTS_PER_ANSWER} this round",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "\uD83C\uDFC6 SCORES ON THIS PHONE",
                                color = Color(0xFFa5b4fc),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            leaderboard.forEachIndexed { position, learner ->
                                val isYou = learner.name.equals(learnerName, ignoreCase = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        when (position) {
                                            0 -> "\uD83E\uDD47"
                                            1 -> "\uD83E\uDD48"
                                            2 -> "\uD83E\uDD49"
                                            else -> "  "
                                        },
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isYou) "${learner.name} (you)" else learner.name,
                                        color = if (isYou) Color(0xFF6ee7b7) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${learner.points}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { startQuiz() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10b981)
                                )
                            ) {
                                Text("Play again", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = {
                                    Progress.switchLearner(context)
                                    learnerName = null
                                    nameEntry = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Someone else wants to play",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // A question.
                        else -> {
                            val question = quizQuestions[quizIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Question ${quizIndex + 1} of ${quizQuestions.size}",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    "$quizScore points",
                                    color = Color(0xFF6ee7b7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "How do you say this in $learnLang?",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                question.english,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))

                            question.options.forEach { option ->
                                val answered = quizChosen != null
                                val isCorrect = option == question.correct
                                val isChosen = option == quizChosen
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !answered) { answerQuiz(option) },
                                    color = when {
                                        !answered -> Color(0xFF334155)
                                        isCorrect -> Color(0xFF065f46)
                                        isChosen -> Color(0xFF7f1d1d)
                                        else -> Color(0xFF334155)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            option,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (answered && isCorrect) {
                                            Text("\u2705", fontSize = 16.sp)
                                        } else if (answered && isChosen) {
                                            Text("\u274C", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }

                            if (quizChosen != null) {
                                Spacer(Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { repeatSpeech(question.correct, learnLang) },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF334155)
                                        )
                                    ) {
                                        Text("\uD83D\uDD0A Hear it", fontSize = 14.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { nextQuizQuestion() },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF6366f1)
                                        )
                                    ) {
                                        Text(
                                            if (quizIndex < quizQuestions.size - 1) "Next" else "Finish",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Screen.FACE2FACE -> FaceToFaceScreenContent(
                    fromLang = fromLang,
                    toLang = toLang,
                    topBubble = topBubble,
                    bottomBubble = bottomBubble,
                    isListening = isFaceListening,
                    micLevel = micLevel,
                    onSwapLangs = {
                        // Just change the languages — the effect above owns the
                        // recognizer's lifecycle and restarts it in the new
                        // direction, so the conversation keeps running.
                        val f = fromLang; fromLang = toLang; toLang = f
                        learnLang = toLang
                        topBubble = ""; bottomBubble = ""
                        downloadModel(fromLang, toLang)
                    }
                )
            }
        }

        // ── Banner ad (all screens), above the navigation bar ──
        // It used to sit flush against the nav bar, so a thumb aimed at a tab
        // and landing slightly high hit the ad. It is now labelled and held
        // clear of both the content above and the tabs below, so no tap
        // intended for a control can land on it by accident.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0b1220),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Deliberately generous below the ad: this dead space is
                    // what a tap aimed at a nav tab but landing high hits.
                    .padding(top = 10.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ADVERTISEMENT",
                    color = Color(0xFF94a3b8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = AD_UNIT_BANNER
                            loadAd(AdPolicy.request())
                        }
                    }
                )
            }
        }

        // ── Bottom navigation ──
        SpeechNovaBottomBar(current = currentScreen) { target ->
            // Whenever we change tabs, stop any mic that belongs to the tab we leave.
            if (target != Screen.FACE2FACE) stopFaceToFaceListening()
            if (target != Screen.LEARN) stopPractice()
            if (target != Screen.HOME && isRecording) stopRecording()
            // Arriving on the Quiz tab always starts a fresh round rather than
            // dropping the player back into a half-finished one.
            if (target == Screen.QUIZ && currentScreen != Screen.QUIZ) startQuiz()
            currentScreen = target
        }
    }

    // ═══════════════════════════════════════════════════════════
    // FAVORITES DIALOG
    // ═══════════════════════════════════════════════════════════
    if (showFavorites) {
        val favorites = messages.filter { it.type == "translation" && it.isFavorite.value }
        Dialog(
            onDismissRequest = { showFavorites = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp)
                    .padding(vertical = 24.dp),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐ Favorites", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showFavorites = false }, modifier = Modifier.size(28.dp)) {
                            Text("✕", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (favorites.isEmpty()) {
                        Text(
                            "No favorites yet — tap ☆ on any translation to save it here.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            favorites.forEach { msg ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = Color(0xFF334155),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (msg.originalText.isNotEmpty()) {
                                            Text(
                                                msg.originalText,
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                            Spacer(Modifier.height(2.dp))
                                        }
                                        Text(msg.translatedText, color = Color.White, fontSize = 15.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Row {
                                            MiniLabeledIcon(emoji = "🔊", label = "Listen") {
                                                repeatSpeech(msg.translatedText, toLang)
                                            }
                                            MiniLabeledIcon(emoji = "📋", label = "Copy") {
                                                copyToClipboard(msg.translatedText)
                                            }
                                            MiniLabeledIcon(emoji = "📤", label = "Share") {
                                                shareText(msg.translatedText)
                                            }
                                            MiniLabeledIcon(emoji = "⭐", label = "Remove") {
                                                msg.isFavorite.value = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SETTINGS DIALOG — every mode explained in plain words.
    // ═══════════════════════════════════════════════════════════
    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 600.dp)
                    .padding(vertical = 24.dp),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚙️ Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showSettings = false }, modifier = Modifier.size(28.dp)) {
                            Text("✕", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    SettingRow(
                        title = "Learning Mode",
                        description = "Shows the pronunciation and spelling of every word, so you can learn as you translate.",
                        checked = learningMode,
                        onCheckedChange = { learningMode = it },
                        activeColor = Color(0xFF10b981)
                    )
                    SettingRow(
                        title = "Speak Multiple Sentences",
                        description = "Keeps listening while you talk, then translates everything together when you tap TRANSLATE — instead of after every sentence.",
                        checked = recordingMode,
                        onCheckedChange = { recordingMode = it },
                        activeColor = Color(0xFFef4444)
                    )
                    SettingRow(
                        title = "Speaking Style: " + if (romanticMode) "Soft & Gentle" else "Normal",
                        description = "Makes the spoken translation slower and softer — good for calm, warm conversations.",
                        checked = romanticMode,
                        onCheckedChange = { romanticMode = it },
                        activeColor = Color(0xFFec4899)
                    )
                    SettingRow(
                        title = "Speaker Voice: " + if (useFemaleVoice) "Female" else "Male",
                        description = "Choose whether translations are spoken in a female or male voice.",
                        checked = useFemaleVoice,
                        onCheckedChange = { useFemaleVoice = it },
                        activeColor = Color(0xFFf472b6)
                    )

                    // Which voice is actually being used, in plain words. How
                    // human the app sounds is decided almost entirely by what
                    // the phone has installed, and until now there was no way
                    // to tell whether a good voice was being picked or the
                    // engine had quietly fallen back to its robotic default.
                    Spacer(Modifier.height(6.dp))
                    val activeVoice = remember(toLang, useFemaleVoice, showSettings) {
                        pickBestVoice(
                            tts?.voices,
                            langToTTS[toLang] ?: Locale.US,
                            useFemaleVoice,
                            isOnline(context)
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0f2e2a),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "🔈 Voice in use for $toLang",
                                color = Color(0xFF6ee7b7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    activeVoice == null ->
                                        "Your phone has no $toLang voice installed, so it's using a basic fallback. Tap below to add one — that is the single biggest thing you can do to make it sound human."
                                    activeVoice.quality >= 500 ->
                                        "Very high quality — this is the best your phone offers."
                                    activeVoice.quality >= 400 ->
                                        "High quality. A better one may be available to download below."
                                    activeVoice.quality >= 300 ->
                                        "Normal quality. Your phone can probably sound much better — try downloading a higher-quality $toLang voice below."
                                    else ->
                                        "Low quality. This is why it sounds robotic — download a better $toLang voice below."
                                },
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS")
                                    ttsSettingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(ttsSettingsIntent)
                                } catch (_: Exception) { /* no-op */ }
                            },
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎙️", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("More natural voices", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Opens your phone's voice settings so you can download higher-quality voices, if your phone offers them.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    // ═══════════════════════════════════════════════════════════
    // ADD YOUR OWN WORD — translated once, then practised offline.
    // ═══════════════════════════════════════════════════════════
    if (showAddWord) {
        Dialog(onDismissRequest = { showAddWord = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "\u2795 Add your own word",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Type a word in English. It is translated into $learnLang once and saved, so you can hear it and practise it whenever you like \u2014 with or without internet.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newWordEnglish,
                        onValueChange = { newWordEnglish = it; newWordError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "For example: Mother",
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10b981),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color(0xFF10b981)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (newWordError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "\u26A0\uFE0F $newWordError",
                            color = Color(0xFFfca5a5),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    if (newWordTranslated.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0f2e2a),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    newWordTranslated,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                MiniLabeledIcon(emoji = "\uD83D\uDD0A", label = "Hear") {
                                    repeatSpeech(newWordTranslated, learnLang)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                showAddWord = false
                                newWordTranslated = ""
                                newWordError = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done", color = Color.White.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { saveNewWord() },
                            enabled = !newWordBusy && newWordEnglish.isNotBlank(),
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10b981),
                                disabledContainerColor = Color(0xFF374151)
                            )
                        ) {
                            Text(
                                if (newWordBusy) "\u23F3 Saving\u2026" else "Add word",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE READY — a newer version finished downloading in the
    // background and only needs a restart to take effect.
    // ═══════════════════════════════════════════════════════════
    if (updateReadyToInstall) {
        AlertDialog(
            onDismissRequest = onDismissUpdate,
            containerColor = Color(0xFF1e293b),
            title = {
                Text("🎉 Update ready", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "A new version of SpeechNova has downloaded. Restart now to start using it — it only takes a moment.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onInstallUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) {
                    Text("Restart now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUpdate) {
                    Text("Later", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════════
    // TEXT TRANSLATE — type or paste anything, in any language.
    // ═══════════════════════════════════════════════════════════
    if (showTextTranslate) {
        Dialog(
            onDismissRequest = { showTextTranslate = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 16.dp),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📝 Translate text",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showTextTranslate = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("✕", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Paste or type anything. SpeechNova works out which language it is, then translates it into $toLang.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = {
                            pastedText = it
                            pastedResult = ""
                            pastedError = ""
                            pastedDetectedLang = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp),
                        placeholder = {
                            Text(
                                "Type here, or tap Paste below",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366f1),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            cursorColor = Color(0xFF6366f1)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (clip.isNullOrBlank()) {
                                    pastedError = "Nothing to paste — copy some text first"
                                } else {
                                    pastedText = clip
                                    pastedResult = ""
                                    pastedError = ""
                                    pastedDetectedLang = null
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155)
                            )
                        ) {
                            Text("📋 Paste", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { translatePastedText() },
                            enabled = !pastedBusy && pastedText.isNotBlank(),
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10b981),
                                disabledContainerColor = Color(0xFF374151)
                            )
                        ) {
                            Text(
                                if (pastedBusy) "⏳ Working…" else "🌍 Translate",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (pastedDetectedLang != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFF312e81),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                "🔎 That looks like $pastedDetectedLang",
                                color = Color(0xFFc7d2fe),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (pastedError.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "⚠️ $pastedError",
                            color = Color(0xFFfca5a5),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    if (pastedResult.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0f2e2a),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    toLang.uppercase(),
                                    color = Color(0xFF6ee7b7),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    pastedResult,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    lineHeight = 24.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Row {
                                    MiniLabeledIcon(emoji = "🔊", label = "Listen") {
                                        repeatSpeech(pastedResult, toLang)
                                    }
                                    MiniLabeledIcon(emoji = "📋", label = "Copy") {
                                        copyToClipboard(pastedResult)
                                    }
                                    MiniLabeledIcon(emoji = "📤", label = "Share") {
                                        shareText(pastedResult)
                                    }
                                    MiniLabeledIcon(emoji = "⭐", label = "Save") {
                                        messages.add(
                                            TranslationMessage(
                                                displayText = pastedResult,
                                                originalText = pastedText.trim(),
                                                type = "translation",
                                                translatedText = pastedResult,
                                                isFavorite = mutableStateOf(true)
                                            )
                                        )
                                        status = "⭐ Saved"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELP DIALOG — plain-language guide, shown on first open.
    // ═══════════════════════════════════════════════════════════
    if (showHelp) {
        Dialog(
            onDismissRequest = { showHelp = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.9f)
                    .padding(vertical = 16.dp),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("❓ How to Use SpeechNova", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showHelp = false }, modifier = Modifier.size(28.dp)) {
                            Text("✕", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Flexes to fill the space above the pinned button, so the
                    // "Got it" button is always visible no matter the screen size.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        HelpSection("Getting started")
                        HelpStep("1", "Five simple tabs", "Use the bar at the bottom: 🏠 Home to translate, 📚 Learn the alphabet, 📖 Phrases for ready-made sentences, 🎭 Face for talking with someone, and 🎮 Quiz to test yourself.")
                        HelpStep("2", "Pick your languages", "On Home, tap the two boxes near the top — for example English → Hindi. Tap the ⇄ arrow between them to swap the direction.")
                        HelpStep("3", "First time with a language pair?", "The app downloads a small language pack — usually under a minute. After that translating works even with no internet.")

                        HelpSection("Translating by voice")
                        HelpStep("4", "🎤 START", "Tap the big green button and talk clearly. It translates each sentence as you finish it. Tap ⏹ STOP when you're done.")
                        HelpStep("5", "🔴 RECORD, then 🌍 TRANSLATE", "Turn on \"Speak Multiple Sentences\" in ⚙️ Settings and the button becomes 🔴 RECORD. Say as much as you like, then tap 🌍 TRANSLATE to do it all in one go.")
                        HelpStep("6", "🔊 Listen", "Every translation is spoken out loud automatically. Tap 🔊 Listen beside it to hear it again as many times as you need.")
                        HelpStep("7", "🔊 Play all", "At the top of the conversation, replays the whole conversation from the beginning — handy for going back over what was said.")

                        HelpSection("Keeping what you translate")
                        HelpStep("8", "☆ Save", "Tap the star beside a translation to save it. It fills in ⭐ to show it's kept.")
                        HelpStep("9", "⭐ Saved", "The Saved button at the top of Home lists everything you starred, so your useful phrases are one tap away. From there you can listen, copy, share, or remove any of them.")
                        HelpStep("10", "📋 Copy", "Copies the translation to your clipboard, ready to paste into a message, an email, or anywhere else.")
                        HelpStep("11", "📤 Share", "Sends the translation straight to WhatsApp, SMS, email — whatever you have installed.")

                        HelpSection("Other ways to translate")
                        HelpStep("12", "📝 Text", "Type or paste anything — a message, an email, a website — and SpeechNova works out which language it's in on its own, then translates it. You don't have to know what language it was.")
                        HelpStep("13", "📷 Scan", "Point the camera at printed text — a sign, a menu, a form — and take the photo. The app reads the text and translates it. Hold steady and fill the frame for the best results.")
                        HelpStep("14", "📖 Phrases", "Ready-made sentences grouped by situation, for when you'd rather not speak at all. Tap one to have it translated and read aloud.")
                        HelpStep("15", "🎭 Face-to-Face", "Lay the phone flat between you and the other person. It listens and translates continuously, with no buttons to press — your words appear on your side and the translation on theirs, the right way up for each of you.")
                        HelpStep("16", "⇄ swap, mid-conversation", "In Face-to-Face, tap ⇄ swap when it's the other person's turn to speak. It switches direction straight away and keeps listening — you don't have to leave the screen and come back.")

                        HelpSection("Learning as you go")
                        HelpStep("17", "📚 The alphabet", "Open 📚 Learn to see the letters of your language. Tap any letter to hear exactly how it sounds.")
                        HelpStep("18", "🗣️ Words to practice", "Below the alphabet is a word list. Tap 🔊 to hear a word, or 🎤 to say it yourself — the app listens and tells you whether you got it right.")
                        HelpStep("19", "\uD83C\uDFAE Quiz", "Its own tab at the bottom, next to \uD83C\uDFAD Face. It asks you eight words and gives you 10 points for each one you get right. Wrong answers cost nothing \u2014 you can hear the right word and try again next round.")
                        HelpStep("20", "\uD83C\uDFC6 Scores", "Everyone who plays on this phone gets their own score, so a family or a class can compete. You type your name once and it's remembered. Scores stay on the phone \u2014 nothing is sent anywhere.")
                        HelpStep("21", "\u2795 Add word", "The word list not long enough? Tap Add word in \uD83D\uDCDA Learn, type any English word, and it's translated and saved. From then on you can hear it, practise saying it, and be quizzed on it \u2014 offline.")
                        HelpStep("22", "Learning Mode", "Turn it on in ⚙️ Settings to see the spelling and pronunciation of every translation, so you pick the language up while you use it.")

                        HelpSection("Making it yours")
                        HelpStep("23", "⚙️ Settings", "Choose a male or female speaking voice, switch to a slower and softer speaking style, turn Learning Mode on, and open your phone's own voice settings for finer control.")
                        HelpStep("24", "❓ How to use", "This guide. It's always at the top of the Home screen if you need it again.")
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showHelp = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366f1))
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// ══════════════════════════════════════════════════════════════════════════
// SCREEN: QUIZ (checks what has actually stuck, and keeps score)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun QuizScreenBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        content = content
    )
}

// ══════════════════════════════════════════════════════════════════════════
// SCREEN: HOME (translate)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun HomeScreenContent(
    status: String,
    isReady: Boolean,
    isDownloading: Boolean,
    isRecording: Boolean,
    recordingMode: Boolean,
    recordedText: String,
    fromLang: String,
    toLang: String,
    langList: List<String>,
    messages: List<TranslationMessage>,
    onShowHelp: () -> Unit,
    onShowFavorites: () -> Unit,
    onShowSettings: () -> Unit,
    onPickFrom: (String) -> Unit,
    onPickTo: (String) -> Unit,
    onSwapLangs: () -> Unit,
    onScanCamera: () -> Unit,
    onTranslateText: () -> Unit,
    onPlayAll: () -> Unit,
    onListen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onPrimaryButton: () -> Unit,
    onTranslateRecorded: () -> Unit
) {
    // Two layers: everything scrolls except the speak button, which is pinned
    // to the bottom. It used to sit at the end of the scrolling column, so
    // once a conversation grew past one screen the one control the whole app
    // depends on was somewhere off-screen, and people had no reason to know
    // to scroll for it.
    Column(modifier = Modifier.fillMaxSize()) {

    // ── Layer 1: everything that scrolls ──
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // ── HEADER ──
        // A four-stop diagonal sweep rather than the old two-stop indigo, and
        // a soft rounded base so the colour reads as a band rather than a slab.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.linearGradient(colors = HEADER_GRADIENT))
                .padding(vertical = 16.dp, horizontal = 14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.28f))
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF22d3ee),
                                            Color(0xFF818cf8)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌐", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "SpeechNova",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Speak. Translate. Be understood.",
                                color = Color(0xFFfde68a),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeaderShortcut("❓", "How to use", ACCENT_HELP, onShowHelp)
                    HeaderShortcut("📝", "Text", ACCENT_TEXT, onTranslateText)
                    HeaderShortcut("📷", "Scan", ACCENT_SCAN, onScanCamera)
                    HeaderShortcut("⭐", "Saved", ACCENT_SAVED, onShowFavorites)
                    HeaderShortcut("⚙️", "Settings", ACCENT_SETTINGS, onShowSettings)
                }
            }
        }

        // ── Mode banner ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1e293b),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (recordingMode) "Mode: Speak several sentences, then translate"
                        else "Mode: Translate after every sentence",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(status, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }

                if (messages.any { it.type == "translation" }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF334155))
                            .clickable(onClick = onPlayAll)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🔊", fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Play all",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "Choose your languages",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            var fromExp by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { fromExp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(fromLang, fontSize = 13.sp, maxLines = 1)
                }
                DropdownMenu(
                    fromExp, { fromExp = false },
                    modifier = Modifier
                        .background(Color(0xFF1e293b))
                        .heightIn(max = 400.dp)
                ) {
                    langList.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = Color.White, fontSize = 13.sp) },
                            onClick = { fromExp = false; onPickFrom(lang) }
                        )
                    }
                }
            }

            Text(
                "⇄",
                color = Color(0xFF8b5cf6),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSwapLangs)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            var toExp by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { toExp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(toLang, fontSize = 13.sp, maxLines = 1)
                }
                DropdownMenu(
                    toExp, { toExp = false },
                    modifier = Modifier
                        .background(Color(0xFF1e293b))
                        .heightIn(max = 400.dp)
                ) {
                    langList.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = Color.White, fontSize = 13.sp) },
                            onClick = { toExp = false; onPickTo(lang) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // (Camera OCR now lives in the header menu as 📷 Scan, keeping the
        // Home body focused on speak-and-translate.)

        // ── Messages ──
        Surface(
            modifier = Modifier
                .heightIn(min = 200.dp, max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = Color(0xFF1e293b),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                color = Color(0xFF8b5cf6),
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(bottom = 14.dp)
                            )
                            Text(
                                "Setting up $fromLang ↔ $toLang…",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "This only happens once for this language pair,\nusually takes under a minute. After this,\nit works even with no internet.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                if (recordingMode) "🔴" else "🎤",
                                fontSize = 48.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                if (isReady) {
                                    if (recordingMode) "Tap RECORD below, then speak"
                                    else "Tap the green button below, then speak"
                                } else "Getting ready…",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "New here? Tap ❓ How to use above the language pickers.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Or tap 📖 Phrases in the bottom bar for ready-made sentences",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                messages.forEach { msg ->
                    val (bg, label) = when (msg.type) {
                        "you" -> Color(0xFF2563eb) to "You said:"
                        "translation" -> Color(0xFF7c3aed) to "Translation:"
                        "hint" -> Color(0xFFf59e0b) to ""
                        else -> Color(0xFF059669) to "ℹ️"
                    }

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = bg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (label.isNotEmpty()) {
                                        Text(
                                            label,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(msg.displayText, color = Color.White, fontSize = 16.sp)
                                }
                            }

                            if (msg.type == "translation" && msg.translatedText.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onListen(msg.translatedText) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Text("🔊", fontSize = 20.sp)
                                    }
                                    Text(
                                        "Listen",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Row {
                                        MiniLabeledIcon(
                                            emoji = if (msg.isFavorite.value) "⭐" else "☆",
                                            label = "Save"
                                        ) { msg.isFavorite.value = !msg.isFavorite.value }
                                        MiniLabeledIcon(emoji = "📋", label = "Copy") {
                                            onCopy(msg.translatedText)
                                        }
                                        MiniLabeledIcon(emoji = "📤", label = "Share") {
                                            onShare(msg.translatedText)
                                        }
                                    }
                                }
                            }
                        }

                        if (msg.learningExtra.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0f2e2a),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        "📚 Learn this phrase",
                                        color = Color(0xFF6ee7b7),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    msg.learningExtra.split("\n").forEach { line ->
                                        Text(
                                            line,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = if (line.startsWith("🔤")) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            "$APP_CREDIT  •  $APP_COPYRIGHT",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    } // end of the scrolling layer

    // ── Layer 2: START / RECORD / TRANSLATE — pinned, never scrolls away ──
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0f172a),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = if (recordingMode && isRecording) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            Button(
                onClick = onPrimaryButton,
                enabled = isReady && !isDownloading,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFef4444) else if (recordingMode) Color(0xFFef4444) else Color(0xFF10b981),
                    disabledContainerColor = Color(0xFF374151)
                )
            ) {
                Text(
                    when {
                        isDownloading -> "⏳ Downloading..."
                        !isReady -> "⏳ Wait..."
                        isRecording -> "⏹ STOP"
                        recordingMode -> "🔴 RECORD"
                        else -> "🎤 START"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recordingMode && isRecording) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onTranslateRecorded,
                    enabled = recordedText.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8b5cf6),
                        disabledContainerColor = Color(0xFF374151)
                    )
                ) {
                    Text("🌍 TRANSLATE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } // end of the pinned action bar

    } // end of the two-layer column
}

// ══════════════════════════════════════════════════════════════════════════
// SCREEN: LEARN (alphabet & pronunciation)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun LearnScreenContent(
    learnLang: String,
    langList: List<String>,
    adDismissed: Boolean,
    vocabulary: List<VocabItem>,
    vocabLoading: Boolean,
    practicingWord: String?,
    practiceResult: PracticeResult?,
    onDismissAd: () -> Unit,
    onPickLang: (String) -> Unit,
    onSpeakLetter: (String) -> Unit,
    onHearWord: (String) -> Unit,
    onPracticeWord: (String) -> Unit,
    onPlayQuiz: () -> Unit,
    onAddWord: () -> Unit,
    customWords: Set<String>,
    onDeleteWord: (String) -> Unit
) {
    val script = alphabetScriptFor(learnLang)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF0891b2), Color(0xFF6366f1))
                    )
                )
                .padding(vertical = 18.dp, horizontal = 16.dp)
        ) {
            Column {
                Text("📚 Learn & practice", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tap a letter or word to hear it — then speak it and get checked",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Language selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Language:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.width(10.dp))
            var exp by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { exp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(learnLang, fontSize = 14.sp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("▾", fontSize = 14.sp)
                }
                DropdownMenu(
                    exp, { exp = false },
                    modifier = Modifier
                        .background(Color(0xFF1e293b))
                        .heightIn(max = 400.dp)
                ) {
                    langList.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = Color.White, fontSize = 13.sp) },
                            onClick = { exp = false; onPickLang(lang) }
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            if (script != null) {
                Text(
                    script.scriptName,
                    color = Color(0xFFa5b4fc),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Dismissible native ad.
        // The close button used to sit *on top of* the ad's top-right corner
        // at 26dp. A child aiming for it and missing landed on the ad instead
        // — exactly the inadvertent click the Families rules prohibit. It now
        // lives in its own row above the ad, well clear of anything clickable,
        // at a full 48dp touch target.
        if (!adDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissAd,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            NativeAdCard(
                adUnitId = AD_UNIT_NATIVE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

        if (script == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "An alphabet guide for $learnLang is coming soon. Pick another language above, or use 🏠 Home to translate and hear $learnLang out loud.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            script.sections.forEach { section ->
                Text(
                    section.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                // 4-per-row grid (built from Rows so we avoid experimental FlowRow).
                section.letters.chunked(4).forEach { rowLetters ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowLetters.forEach { letter ->
                            LetterCard(
                                letter = letter,
                                modifier = Modifier.weight(1f)
                            ) { onSpeakLetter(letter.glyph) }
                        }
                        // Pad the final row so cards keep an even width.
                        repeat(4 - rowLetters.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        // ── Vocabulary & speak-and-check practice ──
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🗣️ Words to practice",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            if (vocabLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF34d399),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            "Tap 🔊 to hear a word, then 🎤 to say it — we'll tell you if it's right.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // The two things a learner can do beyond the fixed list: test what has
        // actually stuck, and grow the list with words they care about.
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Button(
                onClick = onPlayQuiz,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8b5cf6))
            ) {
                Text("\uD83C\uDFAE Play quiz", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAddWord,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0ea5e9))
            ) {
                Text("\u2795 Add word", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        vocabulary.forEach { item ->
            VocabCard(
                item = item,
                isListening = practicingWord == item.translated && item.translated.isNotEmpty(),
                result = practiceResult?.takeIf { it.target == item.translated && item.translated.isNotEmpty() },
                // Only words the learner added can be removed; the built-in
                // list stays put so the app can't be emptied by accident.
                isCustom = item.english in customWords,
                onDelete = { onDeleteWord(item.english) },
                onHear = { onHearWord(item.translated) },
                onPractice = { onPracticeWord(item.translated) }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ── One vocabulary word: shows meaning + word, hear it, and practice saying it. ──
@Composable
private fun VocabCard(
    item: VocabItem,
    isListening: Boolean,
    result: PracticeResult?,
    isCustom: Boolean,
    onDelete: () -> Unit,
    onHear: () -> Unit,
    onPractice: () -> Unit
) {
    val ready = item.translated.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        color = Color(0xFF1e293b),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.english, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        if (isCustom) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF0369a1),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "yours",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                                Text("\u2715", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (ready) item.translated else "…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.roman.isNotEmpty()) {
                        Text(item.roman, color = Color(0xFFa5b4fc), fontSize = 13.sp)
                    }
                }
                // Hear
                IconButton(
                    onClick = onHear,
                    enabled = ready,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    Text("🔊", fontSize = 20.sp)
                }
                Spacer(Modifier.width(10.dp))
                // Practice (speak)
                IconButton(
                    onClick = onPractice,
                    enabled = ready,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isListening) Color(0xFFef4444) else Color(0xFF10b981))
                ) {
                    Text(if (isListening) "🔴" else "🎤", fontSize = 20.sp)
                }
            }

            if (isListening) {
                Spacer(Modifier.height(8.dp))
                Text("🎙️ Listening… say the word now", color = Color(0xFF34d399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else if (result != null) {
                Spacer(Modifier.height(8.dp))
                if (result.correct) {
                    Text("✅ Correct! Nicely done.", color = Color(0xFF6ee7b7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Column {
                        Text("❌ Not quite.", color = Color(0xFFfca5a5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "You said: \"${result.heard}\"",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            "Tap 🔊 to hear the correct pronunciation and try again.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SCREEN: PHRASES (ready-made sentences)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun PhrasesScreenContent(
    toLang: String,
    selectedCategory: PhraseCategory?,
    onSelectCategory: (PhraseCategory) -> Unit,
    onBack: () -> Unit,
    onPickPhrase: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF7c3aed), Color(0xFF8b5cf6))
                    )
                )
                .padding(vertical = 18.dp, horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedCategory != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Column {
                    Text(
                        selectedCategory?.let { "${it.emoji}  ${it.name}" } ?: "📖 Phrases",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap a phrase to translate & hear it in $toLang",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val category = selectedCategory
        if (category == null) {
            phraseCategories.forEach { cat ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCategory(cat) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${cat.phrases.size} phrases",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp
                            )
                        }
                        Text("›", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp)
                    }
                }
            }
        } else {
            category.phrases.forEach { phrase ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickPhrase(phrase) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(phrase, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        Text("🔊", fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════════
// SCREEN: FACE-TO-FACE (continuous conversation — open to everyone)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun FaceToFaceScreenContent(
    fromLang: String,
    toLang: String,
    topBubble: String,
    bottomBubble: String,
    isListening: Boolean,
    micLevel: Float,
    onSwapLangs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎭 Face-to-Face", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Surface(color = Color(0xFF334155), shape = RoundedCornerShape(20.dp)) {
                Text(
                    "Lay the phone flat between you",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            // TOP half — rotated 180° for the person opposite (reads toLang)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .rotate(180f),
                color = Color(0xFF312e81),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(toLang, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        topBubble.ifEmpty { "The translation will appear here" },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                }
            }

            // Middle strip — waveform + swap
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "⇄  swap",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onSwapLangs)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
                WaveformBars(
                    level = micLevel,
                    active = isListening,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                )
            }

            // BOTTOM half — normal orientation for the phone holder (speaks fromLang)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (isListening) "🎙️ Listening…" else "…",
                        color = Color(0xFF34d399),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        bottomBubble.ifEmpty { "Just start speaking in $fromLang" },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(fromLang, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            "Speaks $fromLang → $toLang. Tap ⇄ swap to flip. Lay the phone flat between you.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
