package com.techsagehub.mannai

import android.app.Application
import com.techsagehub.mannai.assistant.CommandExecutor
import com.techsagehub.mannai.data.ChatRepository
import com.techsagehub.mannai.data.ReminderRepository
import com.techsagehub.mannai.data.NoteRepository
import com.techsagehub.mannai.data.SettingsStore
import com.techsagehub.mannai.data.TaskRepository
import com.techsagehub.mannai.data.db.MannAIDatabase
import com.techsagehub.mannai.notify.MannAIChannels

class MannAIApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var tasks: TaskRepository
        private set
    lateinit var notes: NoteRepository
        private set
    lateinit var reminders: ReminderRepository
        private set
    lateinit var chat: ChatRepository
        private set
    lateinit var executor: CommandExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        MannAIChannels.createAll(this)
        val db = MannAIDatabase.get(this)
        settings = SettingsStore(this)
        tasks = TaskRepository(db.taskDao())
        notes = NoteRepository(db.noteDao())
        reminders = ReminderRepository(db.reminderDao())
        chat = ChatRepository(db.chatDao())
        executor = CommandExecutor(this, tasks, notes, reminders, settings)
    }
}
