package dev.treder.info.claude.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class FormattingTest {

    @Test
    fun formatModelNameShortensKnownFamilies() {
        assertEquals("Opus 4.7", formatModelName("claude-opus-4-7"))
        assertEquals("Sonnet 4.6", formatModelName("claude-sonnet-4-6"))
        assertEquals("Haiku 4.5", formatModelName("claude-haiku-4-5"))
    }

    @Test
    fun formatModelNameStripsDateSuffix() {
        assertEquals("Haiku 4.5", formatModelName("claude-haiku-4-5-20251001"))
        assertEquals("Opus 4.8", formatModelName("claude-opus-4-8"))
    }

    @Test
    fun formatModelNameHandlesLegacyOrdering() {
        assertEquals("Sonnet 3.5", formatModelName("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun formatModelNameDerivesGenericallyWithoutHardcodedFamilies() {
        // A family the code has never heard of still formats correctly.
        assertEquals("Neptune 5.0", formatModelName("claude-neptune-5-0"))
        assertEquals("Synthetic", formatModelName("<synthetic>"))
    }

    @Test
    fun formatModelNameFallsBackForBlank() {
        assertEquals("Unbekannt", formatModelName(null))
        assertEquals("Unbekannt", formatModelName(""))
    }

    @Test
    fun formatPercentRoundsAndGuardsEdges() {
        assertEquals("84 %", formatPercent(0.84))
        assertEquals("100 %", formatPercent(1.0))
        assertEquals("<1 %", formatPercent(0.004))
        assertEquals("0 %", formatPercent(0.0))
        assertEquals("0 %", formatPercent(Double.NaN))
    }

    @Test
    fun formatResetShowsTimeOnlyWithin23Hours() {
        val zone = TimeZone.UTC
        val now = Instant.parse("2026-06-29T08:00:00Z")
        // 5 h ahead -> time only
        assertEquals("13:00", formatReset(LocalDateTime(2026, 6, 29, 13, 0), now, zone))
        // 22 h ahead (next calendar day, but < 23 h) -> still time only
        assertEquals("06:00", formatReset(LocalDateTime(2026, 6, 30, 6, 0), now, zone))
    }

    @Test
    fun formatResetAddsDateFrom23HoursAhead() {
        val zone = TimeZone.UTC
        val now = Instant.parse("2026-06-29T08:00:00Z")
        // exactly 23 h ahead -> date appears
        assertEquals("30.06., 07:00", formatReset(LocalDateTime(2026, 6, 30, 7, 0), now, zone))
        // several days ahead -> date
        assertEquals("06.07., 09:00", formatReset(LocalDateTime(2026, 7, 6, 9, 0), now, zone))
    }
}
