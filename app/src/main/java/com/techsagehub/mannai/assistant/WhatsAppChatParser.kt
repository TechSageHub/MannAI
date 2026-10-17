package com.techsagehub.mannai.assistant

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Pure, testable parser for WhatsApp-exported .txt chat files.
 *
 * WhatsApp export format (varies by locale):
 *   DD/MM/YYYY, HH:MM - Sender: Message
 *   MM/DD/YYYY, HH:MM - Sender: Message
 *   DD/MM/YY, H:MM am/pm - Sender: Message
 *
 * System messages (no sender) and media placeholders are classified but not
 * attributed to any sender. No content is logged or uploaded.
 */
object WhatsAppChatParser {

    /** A single parsed line before user attribution. */
    data class ParsedLine(
        /** Sender name, or null for system/media messages. */
        val senderName: String?,
        val message: String,
        val timestampMs: Long,
        /** True when this is a system message (e.g. group created). */
        val isSystem: Boolean,
        /** True when this is a media placeholder (<Media omitted>). */
        val isMedia: Boolean
    )

    /** Parse result including diagnostics. */
    data class ParseResult(
        val lines: List<ParsedLine>,
        val unrecognizedLines: List<String>,
        val stats: Stats
    ) {
        data class Stats(
            val totalLines: Int,
            val parsedMessages: Int,
            val systemMessages: Int,
            val mediaMessages: Int,
            val unrecognized: Int
        )
    }

    private const val USER_MEDIA_PLACEHOLDER = "<Media omitted>"

    /**
     * Single unified line pattern. Groups:
     *   1 = day, 2 = month, 3 = year, 4 = hour, 5 = minute,
     *   6 = optional am/pm, 7 = remainder (sender: message or system text).
     */
    private val LINE_PATTERN = Pattern.compile(
        "^(\\d{1,2})/(\\d{1,2})/(\\d{2,4}),\\s*(\\d{1,2}):(\\d{2})\\s*" +
            "(am|pm|a\\.m\\.|p\\.m\\.)?\\s*-\\s*(.+)$",
        Pattern.CASE_INSENSITIVE
    )

    /** Parse the full export text. Returns parsed lines + diagnostics. */
    fun parse(exportText: String): ParseResult {
        val textLines = exportText.lines()
        val parsed = mutableListOf<ParsedLine>()
        val unrecognized = mutableListOf<String>()

        var current: ParsedLine? = null

        for (rawLine in textLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val m: Matcher? = LINE_PATTERN.matcher(line).takeIf { it.matches() }

            if (m == null) {
                // Continuation of a multiline message, or unrecognized line.
                if (current != null) {
                    current = current.copy(message = current.message + "\n" + line)
                } else {
                    unrecognized.add(line)
                }
                continue
            }

            // Save previous accumulated message.
            if (current != null) parsed.add(current)

            val dateStr = "${m.group(1)}/${m.group(2)}/${m.group(3)}"
            val timeStr = "${m.group(4)}:${m.group(5)}"
            val amPm = m.group(6)
            val rest = m.group(7)

            val timestampMs = parseDateTime(dateStr, timeStr, amPm) ?: System.currentTimeMillis()

            // Split sender:message at the first ": " after the dash separator.
            val colonIdx = rest.indexOf(": ")
            if (colonIdx > 0) {
                val sender = rest.substring(0, colonIdx).trim()
                val msg = rest.substring(colonIdx + 2)
                val isMedia = msg.trim() == USER_MEDIA_PLACEHOLDER
                current = ParsedLine(
                    senderName = sender,
                    message = msg.trim(),
                    timestampMs = timestampMs,
                    isSystem = false,
                    isMedia = isMedia
                )
            } else {
                // No sender — system message.
                current = ParsedLine(
                    senderName = null,
                    message = rest.trim(),
                    timestampMs = timestampMs,
                    isSystem = true,
                    isMedia = false
                )
            }
        }

        if (current != null) parsed.add(current)

        val stats = ParseResult.Stats(
            totalLines = textLines.count { it.isNotBlank() },
            parsedMessages = parsed.count { !it.isSystem },
            systemMessages = parsed.count { it.isSystem },
            mediaMessages = parsed.count { it.isMedia },
            unrecognized = unrecognized.size
        )

        return ParseResult(lines = parsed, unrecognizedLines = unrecognized, stats = stats)
    }

    /** Unique sender names from parsed messages (excluding system/media-only). */
    fun senderNames(result: ParseResult): List<String> {
        return result.lines
            .filter { it.senderName != null }
            .map { it.senderName!! }
            .distinct()
            .sortedWith(compareBy { it.lowercase() })
    }

    private fun parseDateTime(dateStr: String, timeStr: String, amPm: String?): Long? {
        val amPmPart = if (amPm.isNullOrBlank()) "" else " $amPm"
        val full = "$dateStr, $timeStr$amPmPart"

        val formats = listOf(
            // 24-hour formats
            "dd/MM/yyyy, HH:mm",
            "d/M/yyyy, H:mm",
            "dd/MM/yy, HH:mm",
            "d/M/yy, H:mm",
            "MM/dd/yyyy, HH:mm",
            "M/d/yyyy, H:mm",
            "MM/dd/yy, HH:mm",
            "M/d/yy, H:mm",
            // 12-hour formats (with am/pm appended)
            "dd/MM/yyyy, hh:mm a",
            "d/M/yyyy, h:mm a",
            "dd/MM/yy, hh:mm a",
            "d/M/yy, h:mm a",
            "MM/dd/yyyy, hh:mm a",
            "M/d/yyyy, h:mm a",
            "MM/dd/yy, hh:mm a",
            "M/d/yy, h:mm a"
        )
        for (fmt in formats) {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            sdf.isLenient = false
            return runCatching { sdf.parse(full)?.time }.getOrNull()
        }
        return null
    }
}
