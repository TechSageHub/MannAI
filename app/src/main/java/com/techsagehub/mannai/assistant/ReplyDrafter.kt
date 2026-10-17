package com.techsagehub.mannai.assistant

/** Reply tones offered by the Message Reply Assistant. */
enum class ReplyTone(val label: String) {
    Friendly("Friendly"),
    Casual("Casual"),
    Professional("Professional"),
    Polite("Polite"),
    Romantic("Romantic"),
    Apologetic("Apologetic"),
    Short("Short & direct"),
    Detailed("Detailed")
}

/**
 * Offline reply drafting. Uses local templates — honest about being offline.
 * Online AI (optional, user-configured) can refine drafts; this always works.
 */
object ReplyDrafter {
    data class DraftResult(val draft: String?, val error: String?)

    fun tones(): List<ReplyTone> = ReplyTone.entries

    fun draft(original: String, intent: String, tone: ReplyTone): DraftResult {
        val msg = original.trim()
        val what = intent.trim().removeSuffix(".")
        if (msg.isEmpty()) return DraftResult(null, "Paste or dictate the message you received first.")
        if (what.isEmpty()) return DraftResult(null, "Describe what you want to say first.")
        val snippet = if (msg.length > 120) msg.take(120) + "…" else msg
        val draft = when (tone) {
            ReplyTone.Friendly ->
                "Hey! Thanks for your message about \"$snippet\". $what — happy to help. Let me know what works for you!"
            ReplyTone.Casual ->
                "Got it — \"$snippet\". $what. Let me know!"
            ReplyTone.Professional ->
                "Thank you for your message regarding \"$snippet\". $what. Please let me know if you need anything further."
            ReplyTone.Polite ->
                "Thanks so much for letting me know — \"$snippet\". $what. I really appreciate your understanding."
            ReplyTone.Romantic ->
                "Reading your message about \"$snippet\" made me smile. $what — you mean a lot to me."
            ReplyTone.Apologetic ->
                "I'm really sorry about \"$snippet\". $what — I'll make it right. Please forgive me."
            ReplyTone.Short ->
                "Noted — \"$snippet\". $what."
            ReplyTone.Detailed ->
                "Thanks for your message: \"$snippet\". To be clear on my side: $what. " +
                    "If anything is unclear, I'm happy to go over the details whenever suits you."
        }
        return DraftResult(capitalize(draft), null)
    }

    private fun capitalize(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        // Capitalize the intent fragment in place.
        return t.replaceFirstChar { it.uppercase() }
    }
}
