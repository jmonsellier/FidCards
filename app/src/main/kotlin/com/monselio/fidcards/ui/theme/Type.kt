package com.monselio.fidcards.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.monselio.fidcards.R

/** Titres et wordmark. */
val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

/** Tout le reste de l'UI. */
val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
)

private val defaults = Typography()

val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold),
    displayMedium = defaults.displayMedium.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold),
    displaySmall = defaults.displaySmall.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.copy(fontFamily = BricolageGrotesque, fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = InstrumentSans),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = InstrumentSans),
    bodySmall = defaults.bodySmall.copy(fontFamily = InstrumentSans),
    labelLarge = defaults.labelLarge.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium),
    labelMedium = defaults.labelMedium.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium),
    labelSmall = defaults.labelSmall.copy(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium),
)
