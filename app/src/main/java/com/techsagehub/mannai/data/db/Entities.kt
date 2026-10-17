package com.techsagehub.mannai.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user task. reminderAt (epoch millis) optionally triggers a notification. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = null,
    /** 0 = normal, 1 = high, 2 = urgent */
    val priority: Int = 0,
    val done: Boolean = false,
    val reminderAt: Long? = null,
    val reminderFired: Boolean = false
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** Epoch millis when the reminder should fire. */
    val triggerAt: Long,
    val enabled: Boolean = true,
    val fired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" or "assistant" */
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A WhatsApp conversation imported from an exported .txt file.
 * Kept separate from [ChatMessageEntity] (in-app assistant history).
 */
@Entity(tableName = "whatsapp_contacts")
data class WhatsAppContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-facing display name for the contact (e.g. "Ahmed Khan"). */
    val displayName: String,
    /** Lowercase-trimmed display name used for matching. */
    val normalizedName: String,
    /** Epoch millis when the import was committed. */
    val importedAt: Long = System.currentTimeMillis()
)

/**
 * A single imported WhatsApp message.
 * [isUser] is set by the user during import attribution — never inferred.
 */
@Entity(
    tableName = "whatsapp_messages",
    foreignKeys = [ForeignKey(
        entity = WhatsAppContactEntity::class,
        parentColumns = ["id"],
        childColumns = ["contactId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["contactId"]), Index(value = ["timestampMs"])]
)
data class WhatsAppMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    /** Sender name as it appeared in the export (preserved verbatim). */
    val senderName: String,
    /** True when [senderName] was identified by the user as their own. */
    val isUser: Boolean,
    val message: String,
    /** Epoch millis parsed from the export line. */
    val timestampMs: Long
)
