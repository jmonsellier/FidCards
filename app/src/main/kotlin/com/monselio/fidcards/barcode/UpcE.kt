package com.monselio.fidcards.barcode

/**
 * zxing-cpp rapporte un symbole UPC-E sous son texte étendu UPC-A (12 chiffres),
 * quel que soit le TextMode — mais notre générateur UPC-E attend la forme courte
 * à 8 chiffres. Cette recompression est l'inverse exact des règles d'expansion.
 *
 * Nota : l'expansion n'est pas injective (plusieurs symboles UPC-E peuvent porter
 * le même UPC-A) ; le symbole régénéré peut différer en barres de l'original tout
 * en encodant strictement les mêmes chiffres — indiscernable en caisse.
 */
object UpcE {

    /**
     * Ramène le texte rapporté par un lecteur pour un symbole UPC-E à la forme
     * courte à 8 chiffres : déjà courte (8), expansion UPC-A (12), ou expansion
     * EAN-13 à préfixe 0 (13) — cette dernière est ce que zxing-cpp 3.1.1 renvoie.
     */
    fun toCompact(text: String): String? = when {
        text.length == 8 && text.all { it.isDigit() } -> text
        text.length == 12 -> compress(text)
        text.length == 13 && text.startsWith("0") -> compress(text.substring(1))
        else -> null
    }

    fun compress(expanded: String): String? {
        if (expanded.length != 12 || expanded.any { !it.isDigit() }) return null
        val ns = expanded[0]
        if (ns != '0' && ns != '1') return null
        val c = expanded.substring(1, 11)
        val check = expanded[11]
        val core = when {
            c[2] in '0'..'2' && c.regionMatches(3, "0000", 0, 4) ->
                "${c[0]}${c[1]}${c[7]}${c[8]}${c[9]}${c[2]}"
            c.regionMatches(3, "00000", 0, 5) ->
                "${c[0]}${c[1]}${c[2]}${c[8]}${c[9]}3"
            c.regionMatches(4, "00000", 0, 5) ->
                "${c[0]}${c[1]}${c[2]}${c[3]}${c[9]}4"
            c.regionMatches(5, "0000", 0, 4) && c[9] in '5'..'9' ->
                "${c[0]}${c[1]}${c[2]}${c[3]}${c[4]}${c[9]}"
            else -> return null
        }
        return "$ns$core$check"
    }
}
