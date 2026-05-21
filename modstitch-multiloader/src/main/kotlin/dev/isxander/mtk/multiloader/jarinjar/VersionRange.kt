package dev.isxander.mtk.multiloader.jarinjar

import java.io.Serializable

/**
 * Temporary copy of the manifests VersionRange utility.
 *
 * Jar-in-Jar resolves Maven ranges for NeoForge and will need the same ranges
 * in Fabric syntax when generated Fabric metadata carries requirements.
 */
sealed interface VersionRange : Serializable {
    data object Any : VersionRange

    data class Intervals(val intervals: List<Interval>) : VersionRange {
        init {
            require(intervals.isNotEmpty()) { "Intervals must not be empty; use VersionRange.Any" }
        }
    }

    data class Interval(val lower: Bound?, val upper: Bound?) : Serializable {
        val isAny: Boolean get() = lower == null && upper == null

        val isExact: Boolean get() =
            lower != null && upper != null &&
                lower.version == upper.version &&
                lower.inclusive && upper.inclusive

        internal fun contains(v: Version): Boolean {
            lower?.let { lowerBound ->
                val boundVersion = Version.parseOrNull(lowerBound.version) ?: return false
                val comparison = v.compareTo(boundVersion)
                if (if (lowerBound.inclusive) comparison < 0 else comparison <= 0) return false
            }
            upper?.let { upperBound ->
                val boundVersion = Version.parseOrNull(upperBound.version) ?: return false
                val comparison = v.compareTo(boundVersion)
                if (if (upperBound.inclusive) comparison > 0 else comparison >= 0) return false
            }
            return true
        }
    }

    data class Bound(val version: String, val inclusive: Boolean) : Serializable

    fun satisfies(version: String): Boolean {
        val parsedVersion = Version.parseOrNull(version) ?: return false
        return when (this) {
            Any -> true
            is Intervals -> intervals.any { it.contains(parsedVersion) }
        }
    }

    fun toMaven(): String = when (this) {
        Any -> "(,)"
        is Intervals -> intervals.joinToString(",", transform = ::intervalToMaven)
    }

    fun toFabric(): List<String> = when (this) {
        Any -> listOf("*")
        is Intervals -> intervals.map(::intervalToFabric)
    }

    companion object {
        fun parseMaven(input: String): VersionRange {
            val s = input.trim()
            if (s.isEmpty() || s == "*") return Any

            val intervals = mutableListOf<Interval>()
            var i = 0
            while (i < s.length) {
                i = skipWhitespace(s, i)
                if (i >= s.length) break

                val c = s[i]
                if (c == '[' || c == '(') {
                    val end = s.indexOfAny(charArrayOf(']', ')'), i + 1)
                    require(end != -1) { "Unclosed bracket at index $i in '$input'" }
                    intervals += parseInterval(c, s.substring(i + 1, end), s[end], input)
                    i = end + 1
                } else {
                    val comma = s.indexOf(',', i).let { if (it == -1) s.length else it }
                    val v = s.substring(i, comma).trim()
                    require(v.isNotEmpty()) { "Empty version in '$input'" }
                    intervals += Interval(Bound(v, inclusive = true), null)
                    i = comma
                }

                i = skipWhitespace(s, i)
                if (i < s.length) {
                    require(s[i] == ',') { "Expected ',' between intervals in '$input' at $i" }
                    i++
                }
            }

            return Intervals(intervals)
        }

        private fun parseInterval(open: Char, inner: String, close: Char, source: String): Interval {
            val parts = inner.split(',')
            return when (parts.size) {
                1 -> {
                    val version = parts[0].trim()
                    require(open == '[' && close == ']') {
                        "Single-version interval must use [v] form in '$source'"
                    }
                    require(version.isNotEmpty()) { "Empty interval in '$source'" }
                    Interval(Bound(version, true), Bound(version, true))
                }

                2 -> {
                    val lower = parts[0].trim().takeIf { it.isNotEmpty() }
                    val upper = parts[1].trim().takeIf { it.isNotEmpty() }
                    Interval(
                        lower?.let { Bound(it, inclusive = open == '[') },
                        upper?.let { Bound(it, inclusive = close == ']') },
                    )
                }

                else -> error("Invalid interval '$inner' in '$source'")
            }
        }

        private fun skipWhitespace(s: String, from: Int): Int {
            var i = from
            while (i < s.length && s[i].isWhitespace()) i++
            return i
        }

        private fun intervalToMaven(interval: Interval): String {
            val lower = interval.lower
            val upper = interval.upper
            return when {
                interval.isAny -> "(,)"
                interval.isExact -> "[${lower!!.version}]"
                else -> {
                    val lowerBracket = if (lower?.inclusive == true) "[" else "("
                    val upperBracket = if (upper?.inclusive == true) "]" else ")"
                    "$lowerBracket${lower?.version.orEmpty()},${upper?.version.orEmpty()}$upperBracket"
                }
            }
        }

        private fun intervalToFabric(interval: Interval): String {
            val lower = interval.lower
            val upper = interval.upper
            return when {
                interval.isAny -> "*"
                interval.isExact -> "=${lower!!.version}"
                lower != null && upper == null -> if (lower.inclusive) ">=${lower.version}" else ">${lower.version}"
                lower == null && upper != null -> if (upper.inclusive) "<=${upper.version}" else "<${upper.version}"
                else -> {
                    val lowerRequirement = if (lower!!.inclusive) ">=${lower.version}" else ">${lower.version}"
                    val upperRequirement = if (upper!!.inclusive) "<=${upper.version}" else "<${upper.version}"
                    "$lowerRequirement $upperRequirement"
                }
            }
        }
    }

    data class Version(val parts: List<Int>) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            val count = maxOf(parts.size, other.parts.size)
            for (index in 0 until count) {
                val part = parts.getOrElse(index) { 0 }
                val otherPart = other.parts.getOrElse(index) { 0 }
                if (part != otherPart) return part.compareTo(otherPart)
            }
            return 0
        }

        companion object {
            private val PATTERN = Regex("""\d+(?:\.\d+)*""")

            fun parseOrNull(version: String): Version? =
                if (PATTERN.matches(version)) Version(version.split('.').map(String::toInt)) else null
        }
    }
}
