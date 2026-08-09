/*
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  SPEECHNOVA — CAMERA CAPTURE FOR TEXT SCANNING (OCR)                     ║
 * ║                                                                          ║
 * ║  Developed by : Mr.Parashmani                                            ║
 * ║  Copyright    : © 2025 Parashmani. All rights reserved.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

package com.parashmani.speechnova

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.text.Text
import java.io.File
import java.io.InputStreamReader

/**
 * The photo handed to ML Kit, together with how far it has to be rotated to
 * be upright. Rotating a multi-megapixel bitmap costs a second full-size
 * allocation, so the angle is passed to `InputImage` instead — the recognizer
 * applies it itself.
 */
data class CapturedScan(val bitmap: Bitmap, val rotationDegrees: Int)

/**
 * Longest edge, in pixels, that a captured photo is scaled down to before it
 * reaches the text recognizer.
 *
 * ML Kit wants at least ~1024px on the long edge for small print, and there is
 * nothing to gain from feeding it a full 12MP frame — it only costs memory.
 * 2048 keeps a page of body text comfortably readable.
 */
private const val OCR_MAX_DIMENSION = 2048

private const val SCAN_DIR = "scans"

/**
 * Creates the file the camera app will write the full-resolution photo into,
 * and returns the content URI to hand to `ActivityResultContracts.TakePicture`.
 *
 * Previous captures are cleared first: only one scan is ever in flight, so
 * leaving old frames in the cache is pure waste.
 */
fun createScanCaptureUri(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, SCAN_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.onFailure {
    Log.w("SpeechNova", "Could not create scan capture file", it)
}.getOrNull()

/**
 * Decodes a captured photo at a size the recognizer can actually work with.
 *
 * This is the fix for "OCR only reads one word": the app used to take the
 * thumbnail that `TakePicturePreview` hands back — a ~150px image in which
 * only the largest word on the page survives as legible glyphs. Reading the
 * real file back gives the recognizer the whole line of text.
 */
fun decodeCapturedScan(
    context: Context,
    uri: Uri,
    maxDimension: Int = OCR_MAX_DIMENSION
): CapturedScan? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
    }.getOrNull()

    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    if (longestEdge <= 0) return null

    // inSampleSize has to be a power of two; anything else is rounded down by
    // the decoder anyway.
    var sampleSize = 1
    while (longestEdge / sampleSize > maxDimension) sampleSize *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }.onFailure {
        Log.w("SpeechNova", "Could not decode captured scan", it)
    }.getOrNull() ?: return null

    return CapturedScan(bitmap, readRotationDegrees(context, uri))
}

/**
 * Most phones store the photo in the sensor's orientation and record the real
 * one in EXIF. Text recognition on a sideways page finds almost nothing, so
 * the angle has to travel with the bitmap.
 */
private fun readRotationDegrees(context: Context, uri: Uri): Int = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        when (
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
}.getOrDefault(0)

/**
 * Rebuilds readable sentences out of ML Kit's line-by-line output.
 *
 * `Text.text` joins every visual line with a newline, so a sentence printed
 * across three lines of a sign arrives as three fragments — and the translator
 * then translates each fragment on its own, which reads badly. ML Kit already
 * groups lines that belong together into blocks, so joining the lines within a
 * block with a space and keeping blocks as separate paragraphs restores the
 * sentences.
 */
fun Text.toReadableText(): String {
    val paragraphs = textBlocks.mapNotNull { block ->
        block.lines
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }
    }
    return if (paragraphs.isEmpty()) text.trim() else paragraphs.joinToString("\n")
}


// ── Reading a text file the user picked ────────────────────────────────────

/** Files bigger than this are refused outright. */
const val MAX_TEXT_FILE_BYTES = 50L * 1024 * 1024

/**
 * How much of a file is actually translated.
 *
 * A 50 MB text file is roughly fifty million characters. Holding that as a
 * String costs about 100 MB before anything is translated, and ML Kit would
 * be asked to run on-device translation across the whole of it — neither is
 * survivable on a phone. This is a generous cap on real documents: a long
 * essay is a few thousand characters, a book chapter maybe forty thousand.
 */
const val MAX_TEXT_CHARS = 120_000

/** What came back from a picked file. */
data class PickedText(
    val text: String,
    val truncated: Boolean,
    val error: String? = null
)

/**
 * Reads a picked file as text, refusing what cannot work rather than crashing
 * on it. Only genuinely text-based files are readable — a PDF or a Word
 * document is a container this app has no parser for, and reading one raw
 * produces gibberish, so those are turned away by name with an explanation.
 */
fun readPickedText(context: Context, uri: Uri): PickedText {
    val name = displayNameOf(context, uri).orEmpty()
    val lower = name.lowercase()
    val unreadable = listOf(".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".odt")
    unreadable.firstOrNull { lower.endsWith(it) }?.let { ext ->
        return PickedText(
            "",
            false,
            "$ext files aren't plain text, so they can't be read here. Open it in its own app, copy the text, and use Paste."
        )
    }

    val size = sizeOf(context, uri)
    if (size != null && size > MAX_TEXT_FILE_BYTES) {
        val mb = size / (1024 * 1024)
        return PickedText("", false, "That file is ${mb} MB. The limit is 50 MB.")
    }

    return runCatching {
        val builder = StringBuilder()
        var truncated = false
        context.contentResolver.openInputStream(uri)?.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    val room = MAX_TEXT_CHARS - builder.length
                    if (read >= room) {
                        builder.appendRange(buffer, 0, room)
                        truncated = true
                        break
                    }
                    builder.appendRange(buffer, 0, read)
                }
            }
        } ?: return PickedText("", false, "Couldn't open that file.")

        val text = builder.toString()
        if (text.isBlank()) {
            PickedText("", false, "That file has no text in it that can be read.")
        } else {
            PickedText(text, truncated)
        }
    }.onFailure {
        Log.w("SpeechNova", "Could not read picked file", it)
    }.getOrElse {
        PickedText("", false, "Couldn't read that file.")
    }
}

private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()

private fun sizeOf(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
            cursor.getLong(index)
        } else {
            null
        }
    }
}.getOrNull()
