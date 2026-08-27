package com.monselio.fidcards.barcode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monselio.fidcards.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import zxingcpp.BarcodeReader

/**
 * Le cycle de production complet : génération par zxing core (notre affichage),
 * relecture par zxing-cpp (notre scanner caméra). Si l'un des deux diverge sur un
 * format, ce test casse — c'est le « re-scan » du plan (§13 phase 4), sans optique.
 */
@RunWith(AndroidJUnit4::class)
class ZxingCppReadBackTest {

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

    private val expectedZxingCppFormat = mapOf(
        BarcodeFormat.EAN_13 to BarcodeReader.Format.EAN_13,
        BarcodeFormat.EAN_8 to BarcodeReader.Format.EAN_8,
        BarcodeFormat.UPC_A to BarcodeReader.Format.UPC_A,
        BarcodeFormat.UPC_E to BarcodeReader.Format.UPC_E,
        BarcodeFormat.CODE_128 to BarcodeReader.Format.CODE_128,
        BarcodeFormat.CODE_39 to BarcodeReader.Format.CODE_39,
        BarcodeFormat.ITF to BarcodeReader.Format.ITF,
        BarcodeFormat.CODABAR to BarcodeReader.Format.CODABAR,
        BarcodeFormat.QR_CODE to BarcodeReader.Format.QR_CODE,
        BarcodeFormat.DATA_MATRIX to BarcodeReader.Format.DATA_MATRIX,
        BarcodeFormat.PDF_417 to BarcodeReader.Format.PDF_417,
        BarcodeFormat.AZTEC to BarcodeReader.Format.AZTEC,
    )

    /** Codabar : zxing-cpp rapporte les caractères start/stop, zxing core les attend. */
    private fun canonical(format: BarcodeFormat, text: String): String =
        if (format == BarcodeFormat.CODABAR) text.trim('A', 'B', 'C', 'D') else text

    @Test
    fun chaque_format_genere_est_relu_par_zxing_cpp() {
        val reader = BarcodeReader().apply {
            options.tryHarder = true
            // En mode HRI (défaut), zxing-cpp étend l'UPC-E en UPC-A 12 chiffres,
            // que notre générateur UPC-E refuserait. PLAIN garde la forme compacte.
            options.textMode = BarcodeReader.TextMode.PLAIN
        }
        samples.forEach { (format, value) ->
            val bitmap = generateBarcodeBitmap(value, format, maxWidthPx = 1080, barHeightPx = 300)
            assertNotNull("$format : bitmap nul", bitmap)
            val results = reader.read(bitmap!!)
            val result = results.firstOrNull()
            assertNotNull("$format : aucun code relu par zxing-cpp", result)
            if (format == BarcodeFormat.UPC_A && result!!.format == BarcodeReader.Format.EAN_13) {
                // Un UPC-A est un EAN-13 à préfixe 0 : sans restriction de formats,
                // zxing-cpp le rapporte en EAN-13 « 0 + valeur ». Mêmes chiffres,
                // même symbole en caisse — équivalence acceptée.
                assertEquals("UPC_A relu en EAN-13", "0$value", result.text)
            } else if (format == BarcodeFormat.UPC_E) {
                // En TextMode PLAIN, zxing-cpp rend la forme courte à 8 chiffres ;
                // en HRI il rendrait l'expansion à 12, que le scanner recompresse
                // (UpcE). Les deux chemins doivent aboutir à la valeur d'origine.
                assertEquals("UPC_E : format relu", BarcodeReader.Format.UPC_E, result!!.format)
                assertEquals("UPC_E : forme courte", value, UpcE.toCompact(result.text.orEmpty()))
            } else {
                assertEquals("$format : format relu", expectedZxingCppFormat.getValue(format), result!!.format)
                assertEquals(
                    "$format : contenu relu",
                    canonical(format, value),
                    canonical(format, result.text.orEmpty()),
                )
            }
        }
    }
}
