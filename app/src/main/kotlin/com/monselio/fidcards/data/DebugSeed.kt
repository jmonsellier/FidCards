package com.monselio.fidcards.data

/**
 * Amorçage du développement (docs/PROJET.md §10.3) : le plein écran se développe avant
 * l'écran d'ajout, il faut donc des cartes en base. Inséré uniquement en build debug,
 * et uniquement si la base est vide.
 */
object DebugSeed {

    /** Clé de contrôle EAN-13 sur les 12 premiers chiffres. */
    internal fun ean13(base12: String): String {
        require(base12.length == 12 && base12.all { it.isDigit() })
        val sum = base12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return base12 + ((10 - sum % 10) % 10)
    }

    fun cards(now: Long): List<Card> = listOf(
        Card(
            name = "Carrefour",
            barcodeValue = ean13("913572468012"),
            barcodeFormat = BarcodeFormat.EAN_13,
            brandId = "carrefour",
            createdAt = now,
        ),
        Card(
            name = "E.Leclerc",
            barcodeValue = ean13("270000123456"),
            barcodeFormat = BarcodeFormat.EAN_13,
            brandId = "eleclerc",
            createdAt = now,
        ),
        Card(
            name = "Decathlon",
            barcodeValue = "2081234567890",
            barcodeFormat = BarcodeFormat.CODE_128,
            brandId = "decathlon",
            createdAt = now,
        ),
        Card(
            name = "Fnac",
            barcodeValue = "F123456789",
            barcodeFormat = BarcodeFormat.CODE_128,
            brandId = "fnac",
            createdAt = now,
        ),
        Card(
            name = "Sephora",
            barcodeValue = ean13("312345678901"),
            barcodeFormat = BarcodeFormat.QR_CODE,
            brandId = "sephora",
            createdAt = now,
        ),
        Card(
            name = "Picard",
            // Numéro imprimé différent du contenu encodé (docs/PROJET.md §1.1).
            barcodeValue = ean13("330123456789"),
            displayValue = "0123456789",
            barcodeFormat = BarcodeFormat.EAN_13,
            brandId = "picard",
            createdAt = now,
        ),
    )
}
