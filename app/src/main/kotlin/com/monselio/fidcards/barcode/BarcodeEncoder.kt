package com.monselio.fidcards.barcode

import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.monselio.fidcards.data.BarcodeFormat

/**
 * Génération des codes-barres — le piège principal du projet (docs/PROJET.md §14) :
 * un code agrandi par interpolation devient flou et illisible. On encode donc à la
 * taille exacte en modules, puis on agrandit par blocs entiers (nearest neighbour),
 * quiet zone comprise. Tout est en Kotlin pur : le round-trip est testé en JVM.
 *
 * La lecture (caméra) passe par zxing-cpp ; l'écriture par zxing core, car l'AAR
 * zxing-cpp 3.1.1 n'expose aucune API de génération.
 */
object BarcodeEncoder {

    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val BLACK: Int = 0xFF000000.toInt()

    /**
     * Quiet zone en modules, de part et d'autre du code. La norme ITF exige 10 X ;
     * 10 couvre tous les formats linéaires. 4 suffit aux formats matriciels (QR : 4).
     */
    fun quietZoneModules(format: BarcodeFormat): Int = if (format.wide) 10 else 4

    /**
     * Encode [value] à la taille minimale : un module = une cellule de la matrice.
     * Lève [com.google.zxing.WriterException] (ou IllegalArgumentException) si la
     * valeur est invalide pour le format.
     */
    fun encode(value: String, format: BarcodeFormat): BitMatrix =
        MultiFormatWriter().encode(
            value,
            format.toZxing(),
            1,
            1,
            mapOf(EncodeHintType.MARGIN to 0),
        )

    class Rendered(val width: Int, val height: Int, val pixels: IntArray)

    /**
     * Agrandit la matrice par blocs entiers : chaque module devient un rectangle
     * [scaleX] × [scaleY] px, sans aucune interpolation. Les quiet zones sont
     * ajoutées en blanc, exprimées en modules (donc mises à l'échelle elles aussi).
     */
    fun render(
        matrix: BitMatrix,
        scaleX: Int,
        scaleY: Int,
        quietXModules: Int,
        quietYModules: Int,
    ): Rendered {
        require(scaleX >= 1 && scaleY >= 1) { "échelle invalide : $scaleX × $scaleY" }
        val quietX = quietXModules * scaleX
        val quietY = quietYModules * scaleY
        val width = matrix.width * scaleX + 2 * quietX
        val height = matrix.height * scaleY + 2 * quietY
        val pixels = IntArray(width * height) { WHITE }
        for (moduleY in 0 until matrix.height) {
            for (moduleX in 0 until matrix.width) {
                if (!matrix.get(moduleX, moduleY)) continue
                val left = quietX + moduleX * scaleX
                val top = quietY + moduleY * scaleY
                for (y in top until top + scaleY) {
                    val rowOffset = y * width
                    for (x in left until left + scaleX) {
                        pixels[rowOffset + x] = BLACK
                    }
                }
            }
        }
        return Rendered(width, height, pixels)
    }

    /**
     * Rendu prêt à afficher : code linéaire étiré à [barHeightPx] de haut, code
     * matriciel carré ; dans les deux cas la largeur totale (quiet zones comprises)
     * approche [maxWidthPx] sans jamais dépasser, par un facteur entier.
     * Renvoie null si [maxWidthPx] est trop petit pour un module d'un pixel.
     */
    fun renderFitting(
        value: String,
        format: BarcodeFormat,
        maxWidthPx: Int,
        barHeightPx: Int,
    ): Rendered? {
        val matrix = encode(value, format)
        val quiet = quietZoneModules(format)
        val totalModulesX = matrix.width + 2 * quiet
        val scale = maxWidthPx / totalModulesX
        if (scale < 1) return null
        return if (matrix.height == 1) {
            render(matrix, scaleX = scale, scaleY = barHeightPx, quietXModules = quiet, quietYModules = 0)
        } else {
            render(matrix, scaleX = scale, scaleY = scale, quietXModules = quiet, quietYModules = quiet)
        }
    }
}

fun BarcodeFormat.toZxing(): com.google.zxing.BarcodeFormat = when (this) {
    BarcodeFormat.EAN_13 -> com.google.zxing.BarcodeFormat.EAN_13
    BarcodeFormat.EAN_8 -> com.google.zxing.BarcodeFormat.EAN_8
    BarcodeFormat.UPC_A -> com.google.zxing.BarcodeFormat.UPC_A
    BarcodeFormat.UPC_E -> com.google.zxing.BarcodeFormat.UPC_E
    BarcodeFormat.CODE_128 -> com.google.zxing.BarcodeFormat.CODE_128
    BarcodeFormat.CODE_39 -> com.google.zxing.BarcodeFormat.CODE_39
    BarcodeFormat.ITF -> com.google.zxing.BarcodeFormat.ITF
    BarcodeFormat.CODABAR -> com.google.zxing.BarcodeFormat.CODABAR
    BarcodeFormat.QR_CODE -> com.google.zxing.BarcodeFormat.QR_CODE
    BarcodeFormat.DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX
    BarcodeFormat.PDF_417 -> com.google.zxing.BarcodeFormat.PDF_417
    BarcodeFormat.AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
}
