package com.monselio.fidcards.ui.navigation

object Destinations {
    const val HOME = "home"

    const val CARD_DETAIL = "card/{cardId}"
    fun cardDetail(cardId: Long) = "card/$cardId"

    const val EDIT = "edit?cardId={cardId}"
    fun editNew() = "edit"
    fun edit(cardId: Long) = "edit?cardId=$cardId"

    const val SCAN = "scan"

    const val SETTINGS = "settings"
}
