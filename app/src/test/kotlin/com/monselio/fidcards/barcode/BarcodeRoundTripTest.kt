package com.monselio.fidcards.barcode

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.monselio.fidcards.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verrou du piège principal (docs/PROJET.md §14) : générer le code puis le redécoder,
 * y compris après mise à l'échelle. Si le rendu perd la quiet zone ou interpole,
 * ce test casse — sans appareil.
 */
class BarcodeRoundTripTest {

    private val samples = mapOf(
        BarcodeFormat.EAN_13 to "4006381333931",
        BarcodeFormat.EAN_8 to "96385074",
        BarcodeFormat.UPC_A to "036000291452",
        BarcodeFormat.UPC_E to "01234565",
        BarcodeFormat.CODE_128 to "2081234567890",
        BarcodeFormat.CODE_39 to "FID-12345",
        BarcodeFormat.ITF to "30712345000010",
        BarcodeFormat.CODABAR to "A40156B",
        BarcodeFormat.QR_CODE to "FIDCARDS-QR-0123456789",
        BarcodeFormat.DATA_MATRIX to "FIDCARDS-DM-123",
        BarcodeFormat.PDF_417 to "FIDCARDS-PDF417-001",
        BarcodeFormat.AZTEC to "FIDCARDS-AZTEC-001",
    )

    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    private fun decode(rendered: BarcodeEncoder.Rendered): Result {
        val source = RGBLuminanceSource(rendered.width, rendered.height, rendered.pixels)
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        } catch (_: Exception) {
            // Le détecteur zxing ne trouve pas un QR à modules d'un seul pixel —
            // cas irréaliste à l'écran (un module y fait toujours plusieurs px),
            // conservé pour verrouiller la géométrie. PURE_BARCODE, prévu pour les
            // images synthétiques exactes, vérifie que chaque module est à sa place.
            MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(source)),
                hints + mapOf(DecodeHintType.PURE_BARCODE to true),
            )
        }
    }

    /**
     * Le lecteur zxing retire les caractères start/stop (A-D) du Codabar : les barres
     * générées depuis « A40156B » et redécodées donnent « 40156 ». Même symbole,
     * représentations textuelles différentes — on compare la forme canonique.
     */
    private fun canonical(format: BarcodeFormat, text: String): String =
        if (format == BarcodeFormat.CODABAR) text.trim('A', 'B', 'C', 'D') else text

    private fun roundTrip(format: BarcodeFormat, value: String, scale: Int) {
        val matrix = BarcodeEncoder.encode(value, format)
        val quiet = BarcodeEncoder.quietZoneModules(format)
        val rendered = if (matrix.height == 1) {
            BarcodeEncoder.render(matrix, scale, 40, quiet, 0)
        } else {
            BarcodeEncoder.render(matrix, scale, scale, quiet, quiet)
        }
        val result = try {
            decode(rendered)
        } catch (e: Exception) {
            throw AssertionError("$format ×$scale : décodage impossible (${e.javaClass.simpleName})", e)
        }
        assertEquals("$format ×$scale : format", format.toZxing(), result.barcodeFormat)
        assertEquals("$format ×$scale : contenu", canonical(format, value), canonical(format, result.text))
    }

    @Test
    fun `round-trip a l'echelle 1 pour les 12 formats`() {
        samples.forEach { (format, value) ->
            // Le détecteur Aztec de zxing ne retrouve pas un code à modules d'un
            // pixel, même en PURE_BARCODE — cas impossible à l'écran ; échelle 2.
            val minScale = if (format == BarcodeFormat.AZTEC) 2 else 1
            roundTrip(format, value, scale = minScale)
        }
    }

    @Test
    fun `round-trip apres agrandissement nearest neighbour`() {
        samples.forEach { (format, value) -> roundTrip(format, value, scale = 3) }
    }

    @Test
    fun `round-trip a grande echelle`() {
        samples.forEach { (format, value) -> roundTrip(format, value, scale = 7) }
    }

    @Test
    fun `renderFitting respecte la largeur maximale et reste decodable`() {
        samples.forEach { (format, value) ->
            val rendered = BarcodeEncoder.renderFitting(value, format, maxWidthPx = 1080, barHeightPx = 300)
            assertNotNull("$format : rendu nul", rendered)
            assertTrue("$format : dépasse 1080 px", rendered!!.width <= 1080)
            val result = decode(rendered)
            assertEquals("$format : contenu", canonical(format, value), canonical(format, result.text))
        }
    }

    @Test
    fun `la valeur scannee n'est jamais corrigee`() {
        // EAN-13 à checksum invalide : le writer doit refuser, jamais « corriger » (§14).
        val invalid = "4006381333930"
        val thrown = runCatching { BarcodeEncoder.encode(invalid, BarcodeFormat.EAN_13) }.exceptionOrNull()
        assertNotNull("un EAN-13 à checksum invalide doit être refusé", thrown)
    }

    @Test
    fun `un rendu sans quiet zone n'est pas decodable en ITF`() {
        // Preuve par l'absurde que la quiet zone est porteuse : sans elle, échec attendu.
        val matrix = BarcodeEncoder.encode(samples.getValue(BarcodeFormat.ITF), BarcodeFormat.ITF)
        val rendered = BarcodeEncoder.render(matrix, 3, 40, quietXModules = 0, quietYModules = 0)
        val failed = runCatching { decode(rendered) }.isFailure
        assertTrue("le décodage ITF devrait échouer sans quiet zone", failed)
    }
}
