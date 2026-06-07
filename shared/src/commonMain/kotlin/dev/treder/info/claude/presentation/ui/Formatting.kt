package dev.treder.info.claude.presentation.ui

import kotlin.math.abs
import kotlin.math.roundToLong

internal fun formatTokens(value: Long): String {
    val abs = abs(value)
    return when {
        abs >= 1_000_000_000 -> "${formatDecimal(value / 1_000_000_000.0, 2)}B"
        abs >= 1_000_000 -> "${formatDecimal(value / 1_000_000.0, 2)}M"
        abs >= 1_000 -> "${formatDecimal(value / 1_000.0, 1)}K"
        else -> value.toString()
    }
}

internal fun formatUsd(amount: Double): String {
    val cents = (amount * 100.0).roundToLong()
    val whole = cents / 100L
    val fractional = (cents % 100L).let { if (it < 10L) "0$it" else "$it" }
    return "$$whole.$fractional"
}

private fun formatDecimal(value: Double, digits: Int): String {
    val factor = when (digits) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        3 -> 1_000L
        else -> 10_000L
    }
    val rounded = (value * factor).roundToLong()
    val whole = rounded / factor
    val fractional = (rounded % factor).let {
        val abs = if (it < 0) -it else it
        abs.toString().padStart(digits, '0')
    }
    return if (digits == 0) whole.toString() else "$whole.$fractional"
}
