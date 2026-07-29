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
