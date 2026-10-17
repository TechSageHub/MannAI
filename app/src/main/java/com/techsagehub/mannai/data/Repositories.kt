package com.techsagehub.mannai.data

import com.techsagehub.mannai.data.db.ChatDao
import com.techsagehub.mannai.data.db.ChatMessageEntity
import com.techsagehub.mannai.data.db.NoteDao
import com.techsagehub.mannai.data.db.NoteEntity
import com.techsagehub.mannai.data.db.ReminderDao
import com.techsagehub.mannai.data.db.ReminderEntity
import com.techsagehub.mannai.data.db.TaskDao
import com.techsagehub.mannai.data.db.TaskEntity
import com.techsagehub.mannai.data.db.WhatsAppContactDao
import com.techsagehub.mannai.data.db.WhatsAppContactEntity
import com.techsagehub.mannai.data.db.WhatsAppMessageDao
import com.techsagehub.mannai.data.db.WhatsAppMessageEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    suspend fun get(id: Long): TaskEntity? = dao.getById(id)
    suspend fun add(title: String, description: String = "", dueAt: Long? = null,
                    priority: Int = 0, reminderAt: Long? = null): Long {
        require(title.isNotBlank()) { "Task title must not be blank" }
        return dao.insert(
            TaskEntity(title = title.trim(), description = description.trim(),
                dueAt = dueAt, priority = priority.coerceIn(0, 2), reminderAt = reminderAt)
        )
    }
    suspend fun update(task: TaskEntity) = dao.update(task)
    suspend fun toggleDone(task: TaskEntity) = dao.update(task.copy(done = !task.done))
    suspend fun delete(task: TaskEntity) = dao.delete(task)
    suspend fun countPending(): Int = dao.countPending()
    suspend fun pendingTaskReminders(): List<TaskEntity> = dao.pendingTaskReminders()
}

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()
    fun search(q: String): Flow<List<NoteEntity>> = dao.search(q)
    suspend fun add(title: String, content: String): Long {
        require(content.isNotBlank() || title.isNotBlank()) { "Note must not be empty" }
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title.trim(), content = content.trim(),
            createdAt = now, updatedAt = now))
    }
    suspend fun update(note: NoteEntity) =
        dao.update(note.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(note: NoteEntity) = dao.delete(note)
    suspend fun clearAll() = dao.clearAll()
}

class ReminderRepository(private val dao: ReminderDao) {
    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()
    suspend fun get(id: Long): ReminderEntity? = dao.getById(id)
    suspend fun add(title: String, description: String, triggerAt: Long): Long {
        require(title.isNotBlank()) { "Reminder title must not be blank" }
        require(triggerAt > System.currentTimeMillis() - 60_000) {
            "Reminder time must be in the future"
        }
        return dao.insert(ReminderEntity(title = title.trim(),
            description = description.trim(), triggerAt = triggerAt))
    }
    suspend fun update(r: ReminderEntity) = dao.update(r)
    suspend fun delete(r: ReminderEntity) = dao.delete(r)
    suspend fun markFired(r: ReminderEntity) = dao.update(r.copy(fired = true))
    suspend fun pendingScheduled(): List<ReminderEntity> = dao.pendingScheduled()
}

class ChatRepository(private val dao: ChatDao) {
    fun observeAll(): Flow<List<ChatMessageEntity>> = dao.observeAll()
    suspend fun addUser(text: String) = dao.insert(ChatMessageEntity(role = "user", text = text))
    suspend fun addAssistant(text: String) =
        dao.insert(ChatMessageEntity(role = "assistant", text = text))
    suspend fun clear() = dao.clearAll()
}

/** Repository for imported WhatsApp conversation history. */
class WhatsAppImportRepository(
    private val contactDao: WhatsAppContactDao,
    private val messageDao: WhatsAppMessageDao
) {
    fun observeContacts(): Flow<List<WhatsAppContactEntity>> =
        contactDao.observeAll()

    suspend fun getContact(id: Long): WhatsAppContactEntity? = contactDao.getById(id)

    suspend fun addContact(displayName: String): Long {
        val normalized = displayName.trim().lowercase()
        require(normalized.isNotBlank()) { "Contact name must not be blank" }
        return contactDao.insert(
            WhatsAppContactEntity(displayName = displayName.trim(), normalizedName = normalized)
        )
    }

    suspend fun updateContact(contact: WhatsAppContactEntity) = contactDao.update(contact)

    suspend fun deleteContact(id: Long) = contactDao.deleteById(id)

    fun observeMessages(contactId: Long): Flow<List<WhatsAppMessageEntity>> =
        messageDao.observeByContact(contactId)

    suspend fun commitMessages(
        contactId: Long,
        userSenderName: String,
        messages: List<ImportedMessage>
    ) = messageDao.insertAll(
        messages.map {
            WhatsAppMessageEntity(
                contactId = contactId,
                senderName = it.senderName,
                isUser = it.senderName.equals(userSenderName, ignoreCase = true),
                message = it.message,
                timestampMs = it.timestampMs
            )
        }
    )

    suspend fun messageCountsByContact(): List<WhatsAppMessageDao.MessageCount> =
        messageDao.messageCountsByContact()

    data class ImportedMessage(
        val senderName: String,
        val message: String,
        val timestampMs: Long
    )
}
