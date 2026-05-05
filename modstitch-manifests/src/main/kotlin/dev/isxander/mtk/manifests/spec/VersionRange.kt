package dev.isxander.mtk.manifests.spec

import java.io.Serializable

/**
 * A version range expressed in Maven (NeoForge) syntax.
 *
 * Maven syntax is the canonical form parsed by this utility because it is
 * structural — brackets carry inclusivity and the only operator is comma.
 * Fabric syntax (`~`, `^`, `>=`, x-ranges, …) is generated as output.
 *
 * Supported Maven inputs:
 *  - `""` or `"*"`               → matches any version
 *  - `"[1.0,2.0]"`               → 1.0 ≤ v ≤ 2.0
 *  - `"[1.0,2.0)"`, `"(1.0,2.0]"`, `"(1.0,2.0)"`
 *  - `"[1.0,)"`                  → v ≥ 1.0
 *  - `"(,2.0)"`                  → v < 2.0
 *  - `"[1.0]"`                   → v == 1.0
 *  - `"[1.0,2.0),[3.0,)"`        → union of intervals
 *  - `"1.0"`                     → bare version, treated as `[1.0,)`
 */
sealed interface VersionRange : Serializable {
    /** Matches any version. */
    data object Any : VersionRange

    /** Union of one or more intervals. */
    data class Intervals(val intervals: List<Interval>) : VersionRange {
        init {
            require(intervals.isNotEmpty()) { "Intervals must not be empty; use VersionRange.Any" }
        }
    }

    /**
     * A half-open or closed interval with optional bounds.
     *
     * `null` lower means -∞, `null` upper means +∞.
     */
    data class Interval(val lower: Bound?, val upper: Bound?) : Serializable {
        val isAny: Boolean get() = lower == null && upper == null

        val isExact: Boolean get() =
            lower != null && upper != null &&
                lower.version == upper.version &&
                lower.inclusive && upper.inclusive

        internal fun contains(v: Version): Boolean {
            lower?.let { l ->
                val bv = Version.parseOrNull(l.version) ?: return false
                val cmp = v.compareTo(bv)
                if (if (l.inclusive) cmp < 0 else cmp <= 0) return false
            }
            upper?.let { u ->
                val bv = Version.parseOrNull(u.version) ?: return false
                val cmp = v.compareTo(bv)
                if (if (u.inclusive) cmp > 0 else cmp >= 0) return false
            }
            return true
        }
    }

    data class Bound(val version: String, val inclusive: Boolean) : Serializable

    /**
     * Returns whether [version] falls within this range.
     *
     * Versions that don't parse as numeric dotted versions (e.g. snapshots,
     * pre-releases) always return `false`.
     */
    fun satisfies(version: String): Boolean {
        val v = Version.parseOrNull(version) ?: return false
        return when (this) {
            Any -> true
            is Intervals -> intervals.any { it.contains(v) }
        }
    }

    /** Serialises this range back to Maven (NeoForge) syntax. */
    fun toMaven(): String = when (this) {
        Any -> "(,)"
        is Intervals -> intervals.joinToString(",", transform = ::intervalToMaven)
    }

    /**
     * Serialises this range to Fabric version-requirement strings.
     *
     * Returns one string per interval; consumers should pass them as a list
     * to `depends`/`recommends` etc. (FMJ treats list entries as OR).
     */
    fun toFabric(): List<String> = when (this) {
        Any -> listOf("*")
        is Intervals -> intervals.map(::intervalToFabric)
    }

    companion object {
        /** Parses a Maven (NeoForge) version range. */
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
                    val v = parts[0].trim()
                    require(open == '[' && close == ']') {
                        "Single-version interval must use [v] form in '$source'"
                    }
                    require(v.isNotEmpty()) { "Empty interval in '$source'" }
                    Interval(Bound(v, true), Bound(v, true))
                }
                2 -> {
                    val lo = parts[0].trim().takeIf { it.isNotEmpty() }
                    val hi = parts[1].trim().takeIf { it.isNotEmpty() }
                    Interval(
                        lo?.let { Bound(it, inclusive = open == '[') },
                        hi?.let { Bound(it, inclusive = close == ']') },
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
            val (l, h) = interval.lower to interval.upper
            return when {
                interval.isAny -> "(,)"
                interval.isExact -> "[${l!!.version}]"
                else -> {
                    val lb = if (l?.inclusive == true) "[" else "("
                    val rb = if (h?.inclusive == true) "]" else ")"
                    "$lb${l?.version.orEmpty()},${h?.version.orEmpty()}$rb"
                }
            }
        }

        private fun intervalToFabric(interval: Interval): String {
            val l = interval.lower
            val h = interval.upper
            return when {
                interval.isAny -> "*"
                interval.isExact -> "=${l!!.version}"
                l != null && h == null -> if (l.inclusive) ">=${l.version}" else ">${l.version}"
                l == null && h != null -> if (h.inclusive) "<=${h.version}" else "<${h.version}"
                else -> {
                    val lo = if (l!!.inclusive) ">=${l.version}" else ">${l.version}"
                    val hi = if (h!!.inclusive) "<=${h.version}" else "<${h.version}"
                    "$lo $hi"
                }
            }
        }
    }

    /**
     * Numeric dotted version (e.g. `1.20.6`). Compared component-wise; missing
     * trailing components are treated as `0` so `1.21` == `1.21.0`.
     */
    data class Version(val parts: List<Int>) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            val n = maxOf(parts.size, other.parts.size)
            for (i in 0 until n) {
                val a = parts.getOrElse(i) { 0 }
                val b = other.parts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        companion object {
            private val PATTERN = Regex("""\d+(?:\.\d+)*""")

            fun parseOrNull(s: String): Version? =
                if (PATTERN.matches(s)) Version(s.split('.').map(String::toInt)) else null
        }
    }
}