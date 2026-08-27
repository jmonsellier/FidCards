package com.monselio.fidcards.util

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Forme canonique pour la recherche : minuscules, sans accents, sans ponctuation.
 * « leclerc » doit trouver « E.Leclerc » (docs/PROJET.md §5.1).
 */
fun String.searchNormalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .filter { it.isLetterOrDigit() }
