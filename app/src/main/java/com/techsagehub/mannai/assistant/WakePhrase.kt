package com.techsagehub.mannai.assistant

import java.time.LocalDateTime

/** Normalized wake-phrase matching for the configured assistant name. Pure logic. */
object WakePhrase {
    /** Canonical phrase, e.g. "Hey Manny". */
    fun phraseFor(name: String): String = "Hey ${name.trim().ifEmpty { "MannAI" }}"

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** True if [heard] contains the wake phrase for [name] ("hey <name>"). */
    fun matches(heard: String, name: String): Boolean {
        val cleanName = normalize(name).ifEmpty { return false }
        val heardNorm = normalize(heard)
        if ("hey $cleanName" in heardNorm) return true
        // Tolerate small misrecognitions of the name right after "hey" (edit distance <= 1..2).
        val tokens = heardNorm.split(" ")
        for (i in tokens.indices) {
            if (tokens[i] == "hey" && i + 1 < tokens.size) {
                val candidate = tokens[i + 1]
                if (editDistance(candidate, cleanName.replace(" ", "")) <= tolerance(cleanName)) {
                    return true
                }
            }
        }
        return false
    }

    private fun tolerance(name: String): Int = if (name.replace(" ", "").length <= 4) 1 else 2

    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return dp[a.length][b.length]
    }
}
