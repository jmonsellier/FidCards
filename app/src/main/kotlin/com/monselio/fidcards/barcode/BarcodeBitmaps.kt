package com.monselio.fidcards.barcode

import android.graphics.Bitmap
import android.graphics.Matrix
import com.monselio.fidcards.data.BarcodeFormat

/** Rotation de 90° sans perte (pixel à pixel, aucun filtrage) pour l'agrandi portrait. */
fun Bitmap.rotatedQuarter(): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(90f) }, false)

/**
 * Enveloppe Android du rendu pur : renvoie null si la valeur est invalide pour le
 * format ou si la largeur disponible ne suffit pas — l'écran affiche alors le
 * numéro seul, qui reste le plan B du cas d'usage principal (docs/PROJET.md §1.1).
 */
fun generateBarcodeBitmap(
    value: String,
    format: BarcodeFormat,
    maxWidthPx: Int,
    barHeightPx: Int,
): Bitmap? {
    val rendered = try {
        BarcodeEncoder.renderFitting(value, format, maxWidthPx, barHeightPx)
    } catch (_: Exception) {
        null
    } ?: return null
    return Bitmap.createBitmap(
        rendered.pixels,
        rendered.width,
        rendered.height,
        Bitmap.Config.ARGB_8888,
    )
}
