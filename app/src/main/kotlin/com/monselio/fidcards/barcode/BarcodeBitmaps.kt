package com.monselio.fidcards.barcode

import android.graphics.Bitmap
import com.monselio.fidcards.data.BarcodeFormat

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
