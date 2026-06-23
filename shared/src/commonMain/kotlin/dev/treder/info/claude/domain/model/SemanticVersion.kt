package dev.treder.info.claude.domain.model

/**
 * A dotted numeric version (e.g. `1.4.0`), tolerant of a leading `v` and of
 * pre-release / build suffixes which are ignored for ordering. Used to decide
 * whether a published release is newer than the running build.
 *
 * Missing trailing components compare as zero, so `1.1` is newer than `1.0.5`.
 */
class SemanticVersion private constructor(private val parts: List<Int>) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (i in 0 until size) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    companion object {
        /**
         * Parses [text] into a version, or returns `null` when any dotted segment
         * is not a plain number (e.g. an empty or malformed string). Rejecting
         * garbage rather than coercing it to `0` keeps a malformed remote tag from
         * being silently mis-ordered — it simply falls through to "no update".
         */
        fun parseOrNull(text: String): SemanticVersion? {
            val core = text.trim()
                .removePrefix("v").removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (core.isEmpty()) return null
            val parts = core.split('.').map { segment ->
                segment.toIntOrNull() ?: return null
            }
            return SemanticVersion(parts)
        }

        /**
         * True when [candidate] is a strictly newer version than [current].
         * A version that cannot be parsed is never considered newer.
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val c = parseOrNull(candidate) ?: return false
            val cur = parseOrNull(current) ?: return false
            return c > cur
        }
    }
}
