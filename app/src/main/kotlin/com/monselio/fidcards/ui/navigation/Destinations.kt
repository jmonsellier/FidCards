package com.monselio.fidcards.ui.navigation

import android.net.Uri

object Destinations {
    const val HOME = "home"

    const val CARD_DETAIL = "card/{cardId}"
    fun cardDetail(cardId: Long) = "card/$cardId"

    /** cardId = -1 : nouvelle carte ; value/format : préremplissage depuis le scan. */
    const val EDIT = "edit?cardId={cardId}&value={value}&format={format}"
    fun editNew() = "edit"
    fun edit(cardId: Long) = "edit?cardId=$cardId"
    fun editScanned(value: String, format: String) =
        "edit?value=${Uri.encode(value)}&format=$format"

    const val SCAN = "scan"

    const val SETTINGS = "settings"
}
