package com.monselio.fidcards.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Les 12 formats supportés (docs/PROJET.md §4).
 *
 * [wide] : formats plus larges que hauts, affichés en paysage dans la vue plein écran.
 * Les formats carrés (QR, DataMatrix, Aztec) ne gagnent rien à la rotation.
 */
enum class BarcodeFormat(val wide: Boolean) {
    EAN_13(true),
    EAN_8(true),
    UPC_A(true),
    UPC_E(true),
    CODE_128(true),
    CODE_39(true),
    ITF(true),
    CODABAR(true),
    QR_CODE(false),
    DATA_MATRIX(false),
    PDF_417(true),
    AZTEC(false),
}

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Contenu brut du code, tel que scanné — jamais « corrigé » (checksum comprise). */
    val barcodeValue: String,
    /** Numéro imprimé sur la carte s'il diffère du contenu encodé (docs/PROJET.md §1.1). */
    val displayValue: String? = null,
    val barcodeFormat: BarcodeFormat,
    /** Clé dans brands.json, nul si l'enseigne est absente du catalogue. */
    val brandId: String? = null,
    /** Couleur de repli de la vignette, dérivée du nom si nulle. */
    val colorArgb: Int? = null,
    /** Logo choisi à la main par l'utilisateur (fichier local). */
    val logoPath: String? = null,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val createdAt: Long,
    /** Dernière ouverture en plein écran. */
    val lastUsedAt: Long? = null,
    /** Compteur brut, informatif. */
    val useCount: Int = 0,
    /** Score de récence pondérée, base du tri par défaut (docs/PROJET.md §4.1). */
    val score: Double = 0.0,
) {
    /** Numéro à afficher sous le code en plein écran. */
    val numberToDisplay: String
        get() = displayValue?.takeIf { it.isNotBlank() } ?: barcodeValue
}
