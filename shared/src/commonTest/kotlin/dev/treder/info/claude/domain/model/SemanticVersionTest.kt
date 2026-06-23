package dev.treder.info.claude.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun higherMajorIsNewer() = assertTrue(SemanticVersion.isNewer("2.0.0", "1.9.9"))

    @Test
    fun higherMinorIsNewer() = assertTrue(SemanticVersion.isNewer("1.2.0", "1.1.5"))

    @Test
    fun higherPatchIsNewer() = assertTrue(SemanticVersion.isNewer("1.0.1", "1.0.0"))

    @Test
    fun equalIsNotNewer() = assertFalse(SemanticVersion.isNewer("1.0.0", "1.0.0"))

    @Test
    fun olderIsNotNewer() = assertFalse(SemanticVersion.isNewer("1.0.0", "1.2.0"))

    @Test
    fun leadingVIsIgnored() = assertTrue(SemanticVersion.isNewer("v1.1.0", "1.0.0"))

    @Test
    fun bothTaggedAndEqualIsNotNewer() = assertFalse(SemanticVersion.isNewer("v1.0.0", "1.0.0"))

    @Test
    fun preReleaseSuffixIsIgnored() = assertFalse(SemanticVersion.isNewer("1.0.0-beta.1", "1.0.0"))

    @Test
    fun missingTrailingComponentsCountAsZero() = assertTrue(SemanticVersion.isNewer("1.1", "1.0.5"))

    @Test
    fun shorterButEqualIsNotNewer() = assertFalse(SemanticVersion.isNewer("1.0", "1.0.0"))

    @Test
    fun unparsableCandidateIsNotNewer() = assertFalse(SemanticVersion.isNewer("not-a-version", "1.0.0"))

    @Test
    fun nonNumericSegmentIsRejectedNotCoercedToZero() {
        // "1.x.0" must not silently become "1.0.0" and mis-order either way.
        assertFalse(SemanticVersion.isNewer("1.x.0", "1.5.0"))
        assertFalse(SemanticVersion.isNewer("1.5.0", "1.x.0"))
    }

    @Test
    fun emptySegmentIsRejected() = assertFalse(SemanticVersion.isNewer("1..0", "1.0.0"))
}
