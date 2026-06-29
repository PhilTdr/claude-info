package dev.treder.info.claude.presentation.ui

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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

/**
 * Turns a raw model id into a short, human-readable name without hardcoding any
 * model family: the alphabetic tokens become the (capitalized) name, the numeric
 * tokens become a dotted version, and a trailing date stamp is dropped. So future
 * models work too, no code change needed.
 *
 *   "claude-opus-4-7"            -> "Opus 4.7"
 *   "claude-haiku-4-5-20251001"  -> "Haiku 4.5"
 *   "claude-3-5-sonnet-20241022" -> "Sonnet 3.5"
 *   "<synthetic>"                -> "Synthetic"
 */
internal fun formatModelName(model: String?): String {
    val raw = model?.trim()?.trim('<', '>')
    if (raw.isNullOrBlank()) return "Unbekannt"

    val tokens = raw.removePrefix("claude-").split('-', '.').filter { it.isNotBlank() }
    val nameParts = mutableListOf<String>()
    val versionParts = mutableListOf<String>()
    for (token in tokens) {
        val numeric = token.all { it.isDigit() }
        when {
            numeric && token.length >= 6 -> {} // date stamp (e.g. 20251001) -> drop
            numeric -> versionParts += token
            else -> nameParts += token.replaceFirstChar { it.uppercase() }
        }
    }

    val name = nameParts.joinToString(" ")
    val version = versionParts.joinToString(".")
    return when {
        name.isNotBlank() && version.isNotBlank() -> "$name $version"
        name.isNotBlank() -> name
        version.isNotBlank() -> version
        else -> raw
    }
}

/** Formats a local time as zero-padded "HH:mm", e.g. 14:10 -> "14:10". */
internal fun formatResetTime(dateTime: LocalDateTime): String {
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

/**
 * Formats a reset moment: just "HH:mm" when it is less than 23 hours away (e.g. the 5-hour
 * window), or "dd.MM., HH:mm" once it is at least 23 hours in the future (e.g. the weekly window).
 */
internal fun formatReset(resetAt: LocalDateTime, now: Instant, zone: TimeZone): String {
    val time = formatResetTime(resetAt)
    val farAhead = resetAt.toInstant(zone) - now >= 23.hours
    if (!farAhead) return time
    val day = resetAt.date.day.toString().padStart(2, '0')
    val month = (resetAt.date.month.ordinal + 1).toString().padStart(2, '0')
    return "$day.$month., $time"
}

/** Formats a 0..1 share as a rounded percentage, e.g. 0.84 -> "84 %". */
internal fun formatPercent(fraction: Double): String {
    if (fraction.isNaN() || fraction <= 0.0) return "0 %"
    val percent = (fraction * 100.0).roundToLong()
    return if (percent <= 0L) "<1 %" else "$percent %"
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
