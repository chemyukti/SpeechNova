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
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

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
private const val AD_UNIT_BANNER = "ca-app-pub-8499432704301966/9196802502"
private const val AD_UNIT_NATIVE = "ca-app-pub-8499432704301966/9457225173"
private const val AD_UNIT_REWARDED = "ca-app-pub-8499432704301966/2705659156"

// Face-to-Face unlock length granted per rewarded ad.
private const val FACE_UNLOCK_MS = 20 * 60 * 1000L

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for Android 15+ compatibility
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

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

        setContent {
            MaterialTheme {
                SpeechNovaApp()
            }
        }
    }
}

/** The four top-level destinations reachable from the bottom navigation bar. */
enum class Screen { HOME, LEARN, PHRASES, FACE2FACE }

/** Walk up the Context chain to find the hosting Activity (needed to show a
 *  full-screen rewarded ad from inside a Compose tree). */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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


// A header icon that always carries a real text label underneath it, so
// nothing in the top bar depends on a beginner correctly guessing an emoji.
@Composable
private fun HeaderShortcut(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 15.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
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

    val adLabel = android.widget.TextView(context).apply {
        text = "Ad"
        setTextColor(android.graphics.Color.parseColor("#94a3b8"))
        textSize = 10f
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
        Triple(Screen.FACE2FACE, "🎭", "Face-to-Face")
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
fun SpeechNovaApp() {
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

    // FACE-TO-FACE (continuous conversation, gated behind a rewarded ad)
    var faceToFaceUnlockedUntil by remember { mutableLongStateOf(0L) }
    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var rewardedLoading by remember { mutableStateOf(false) }
    var topBubble by remember { mutableStateOf("") }
    var bottomBubble by remember { mutableStateOf("") }
    var isFaceListening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableFloatStateOf(0f) }
    var face2faceRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    // Ticks once a second so the unlock countdown re-renders and expiry is noticed.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val faceUnlocked = faceToFaceUnlockedUntil > nowTick

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
        "Urdu" to TranslateLanguage.URDU
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
        "Urdu" to "ur-PK"
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
        "Urdu" to Locale.Builder().setLanguage("ur").setRegion("PK").build()
    )

    val langList = listOf(
        "Hindi", "Bengali", "Tamil", "Telugu", "Marathi",
        "Gujarati", "Kannada", "Urdu",
        "English", "Spanish", "French", "German", "Chinese",
        "Japanese", "Korean", "Arabic", "Russian", "Portuguese", "Italian"
    )

    // Function to select appropriate voice (male/female/romantic)
    fun selectVoice(targetLang: String) {
        val locale = langToTTS[targetLang] ?: Locale.US
        tts?.language = locale

        val voices = tts?.voices
        val selectedVoice = if (useFemaleVoice) {
            voices?.firstOrNull { voice ->
                voice.locale == locale && (
                        voice.name.contains("female", ignoreCase = true) ||
                                voice.name.contains("woman", ignoreCase = true) ||
                                voice.name.contains("myra", ignoreCase = true) ||
                                (!voice.name.contains("male", ignoreCase = true) && voice.quality >= 400)
                        )
            }
        } else {
            voices?.firstOrNull { voice ->
                voice.locale == locale && (
                        voice.name.contains("male", ignoreCase = true) ||
                                voice.name.contains("man", ignoreCase = true) ||
                                voice.quality >= 400
                        )
            }
        }

        if (selectedVoice != null) {
            tts?.voice = selectedVoice
            Log.i("SpeechNova", "Selected voice: ${selectedVoice.name} (${if (useFemaleVoice) "Female" else "Male"})")
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
                faceToFaceUnlockedUntil > System.currentTimeMillis() &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun stopFaceToFaceListening() {
        isFaceListening = false
        micLevel = 0f
        face2faceRecognizer?.destroy()
        face2faceRecognizer = null
    }

    fun startFaceToFaceListening() {
        if (!shouldFaceListen()) { stopFaceToFaceListening(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "❌ Speech recognition not available"
            return
        }
        face2faceRecognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        face2faceRecognizer = sr
        isFaceListening = true

        // Re-arm the continuous loop (recursively) unless we've left the screen.
        fun reArm(delayMs: Long) {
            handler.postDelayed({
                if (shouldFaceListen()) startFaceToFaceListening() else stopFaceToFaceListening()
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

                if (!text.isNullOrBlank()) {
                    bottomBubble = text
                    mlTranslator?.translate(text)
                        ?.addOnSuccessListener { translated ->
                            var t = translated.trim()
                            if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!")) t += "."
                            topBubble = t
                            selectVoice(toLang)
                            tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        ?.addOnFailureListener { status = "❌ Translation failed" }
                }
                reArm(600)
            }

            override fun onPartialResults(partial: Bundle?) {
                val t = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!t.isNullOrBlank()) bottomBubble = t
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langToSTT[fromLang] ?: "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra("android.speech.extra.DICTATION_MODE", true)
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

    // ── REWARDED AD (gate for Face-to-Face) ──
    fun loadRewardedAd() {
        if (rewardedLoading || rewardedAd != null) return
        rewardedLoading = true
        RewardedAd.load(
            context,
            AD_UNIT_REWARDED,
            AdPolicy.request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardedLoading = false
                    Log.i("SpeechNova", "Rewarded ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    rewardedLoading = false
                    Log.w("SpeechNova", "Rewarded ad failed: ${error.message}")
                }
            }
        )
    }

    fun showRewardedAd() {
        val activity = context.findActivity()
        val ad = rewardedAd
        if (activity == null || ad == null) {
            status = "⏳ Ad not ready yet — please try again in a moment"
            loadRewardedAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd() // preload the next one
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                status = "❌ Couldn't show the ad — try again"
                loadRewardedAd()
            }
        }
        ad.show(activity) { _ ->
            // Reward earned → unlock (or extend) Face-to-Face for 20 minutes.
            val base = maxOf(faceToFaceUnlockedUntil, System.currentTimeMillis())
            faceToFaceUnlockedUntil = base + FACE_UNLOCK_MS
            nowTick = System.currentTimeMillis()
            status = "✅ Face-to-Face unlocked for 20 minutes"
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

                        val speechDuration = textWithPauses.length * 120L

                        handler.postDelayed({
                            isSpeaking = false
                            if (isRecording && !recordingMode) {
                                status = "🎤 Listening..."
                                startRecognizer(fromLang, toLang)
                            }
                        }, speechDuration + 500)
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
        if (lang == "English") {
            vocabulary.addAll(vocabularyMaster.map { VocabItem(it, it, "") })
            vocabLoading = false
            return
        }
        vocabLoading = true
        vocabulary.addAll(vocabularyMaster.map { VocabItem(it, "", "") })
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
        }
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            practicingWord = null
            if (practiceRecognizer === sr) practiceRecognizer = null
        }
    }

    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) {
                Log.i("SpeechNova", "TTS initialized successfully")
            }
        }

        if (!hasMicPermission()) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        downloadModel(fromLang, toLang)
        loadRewardedAd()
        showHelp = true // first thing a beginner sees: how to use the app
    }

    // Second-resolution ticker so the Face-to-Face unlock countdown updates
    // and expiry is detected promptly.
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Drive the continuous Face-to-Face loop from screen + unlock state.
    LaunchedEffect(currentScreen, faceUnlocked) {
        if (currentScreen == Screen.FACE2FACE && faceUnlocked && hasMicPermission()) {
            topBubble = ""
            bottomBubble = ""
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
                    onPracticeWord = { word -> practiceWord(word, learnLang) }
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

                Screen.FACE2FACE -> FaceToFaceScreenContent(
                    fromLang = fromLang,
                    toLang = toLang,
                    unlocked = faceUnlocked,
                    remainingMs = (faceToFaceUnlockedUntil - nowTick).coerceAtLeast(0L),
                    rewardedReady = rewardedAd != null,
                    topBubble = topBubble,
                    bottomBubble = bottomBubble,
                    isListening = isFaceListening,
                    micLevel = micLevel,
                    onWatchAd = { showRewardedAd() },
                    onCancel = { currentScreen = Screen.HOME },
                    onSwapLangs = {
                        val f = fromLang; fromLang = toLang; toLang = f
                        learnLang = toLang
                        topBubble = ""; bottomBubble = ""
                        stopFaceToFaceListening()
                        downloadModel(fromLang, toLang)
                    }
                )
            }
        }

        // ── Banner ad (all screens), just above the navigation bar ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0b1220),
            shadowElevation = 6.dp
        ) {
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

        // ── Bottom navigation ──
        SpeechNovaBottomBar(current = currentScreen) { target ->
            // Whenever we change tabs, stop any mic that belongs to the tab we leave.
            if (target != Screen.FACE2FACE) stopFaceToFaceListening()
            if (target != Screen.LEARN) stopPractice()
            if (target != Screen.HOME && isRecording) stopRecording()
            if (target == Screen.FACE2FACE && !faceUnlocked) {
                loadRewardedAd() // make sure an ad is ready for the gate
            }
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
                        HelpStep("1", "Four simple tabs", "Use the bar at the bottom: 🏠 Home to translate, 📚 Learn the alphabet, 📖 Phrases, and 🎭 Face-to-Face.")
                        HelpStep("2", "Pick your languages", "On Home, tap the two boxes near the top — for example English → Hindi.")
                        HelpStep("3", "Speak", "Tap the big green button and talk clearly. Tap it again to stop.")
                        HelpStep("4", "Listen", "Your translation appears and is spoken out loud automatically. Tap 🔊 Listen to hear it again anytime.")
                        HelpStep("5", "First time with a language pair?", "The app downloads a small language pack — usually under a minute. After that it works even offline.")
                        HelpStep("6", "Learn the letters", "Open 📚 Learn to see the alphabet of your language. Tap any letter to hear how it sounds.")
                        HelpStep("7", "No mic needed", "Tap 📖 Phrases for ready-made travel sentences you can use without speaking at all.")
                        HelpStep("8", "Talking in person?", "Open 🎭 Face-to-Face, watch a short ad to unlock 20 minutes, then just talk — it listens and translates continuously.")
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
    onPlayAll: () -> Unit,
    onListen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onPrimaryButton: () -> Unit,
    onTranslateRecorded: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── HEADER ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6366f1), Color(0xFF8b5cf6))
                    )
                )
                .padding(vertical = 16.dp, horizontal = 16.dp)
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌐", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "SpeechNova",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Speak. Translate. Be understood.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeaderShortcut(emoji = "❓", label = "How to use", onClick = onShowHelp)
                    HeaderShortcut(emoji = "📷", label = "Scan", onClick = onScanCamera)
                    HeaderShortcut(emoji = "⭐", label = "Saved", onClick = onShowFavorites)
                    HeaderShortcut(emoji = "⚙️", label = "Settings", onClick = onShowSettings)
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

        Spacer(Modifier.height(12.dp))

        // ── START / RECORD / TRANSLATE buttons ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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

        Text(
            "$APP_CREDIT  •  $APP_COPYRIGHT",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
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
    onPracticeWord: (String) -> Unit
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

        // Dismissible native ad
        if (!adDismissed) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                NativeAdCard(
                    adUnitId = AD_UNIT_NATIVE,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = onDismissAd,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Text("✕", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(8.dp))

        vocabulary.forEach { item ->
            VocabCard(
                item = item,
                isListening = practicingWord == item.translated && item.translated.isNotEmpty(),
                result = practiceResult?.takeIf { it.target == item.translated && item.translated.isNotEmpty() },
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
                    Text(item.english, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
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
// SCREEN: FACE-TO-FACE (continuous conversation, rewarded-ad gated)
// ══════════════════════════════════════════════════════════════════════════
@Composable
private fun FaceToFaceScreenContent(
    fromLang: String,
    toLang: String,
    unlocked: Boolean,
    remainingMs: Long,
    rewardedReady: Boolean,
    topBubble: String,
    bottomBubble: String,
    isListening: Boolean,
    micLevel: Float,
    onWatchAd: () -> Unit,
    onCancel: () -> Unit,
    onSwapLangs: () -> Unit
) {
    if (!unlocked) {
        // ── Rewarded gate ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎭", fontSize = 60.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Face-to-Face conversation",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Lay the phone flat between you and the other person. It listens and translates continuously — no button pressing.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                color = Color(0xFF1e293b),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Watch a short ad to unlock Face-to-Face for 20 minutes",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onWatchAd,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                    ) {
                        Text(
                            if (rewardedReady) "▶  Watch Ad & Unlock" else "⏳ Loading ad…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        return
    }

    // ── Unlocked: continuous conversation UI ──
    val totalSeconds = (remainingMs / 1000L).toInt()
    val mm = totalSeconds / 60
    val ss = totalSeconds % 60
    val timeLabel = "%d:%02d".format(mm, ss)

    Column(modifier = Modifier.fillMaxSize()) {
        // Remaining-time chip
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
                    "⏱ $timeLabel left",
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
