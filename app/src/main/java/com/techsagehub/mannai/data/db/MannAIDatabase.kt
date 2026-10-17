package com.techsagehub.mannai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class, NoteEntity::class, ReminderEntity::class, ChatMessageEntity::class,
        WhatsAppContactEntity::class, WhatsAppMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MannAIDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatDao(): ChatDao
    abstract fun whatsAppContactDao(): WhatsAppContactDao
    abstract fun whatsAppMessageDao(): WhatsAppMessageDao

    companion object {
        /** Additive migration v1 → v2: creates whatsapp_contacts and whatsapp_messages. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `whatsapp_contacts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `whatsapp_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `contactId` INTEGER NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `isUser` INTEGER NOT NULL DEFAULT 0,
                        `message` TEXT NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        FOREIGN KEY(`contactId`) REFERENCES `whatsapp_contacts`(`id`) ON DELETE CASCADE
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_contactId` " +
                        "ON `whatsapp_messages` (`contactId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_whatsapp_messages_timestampMs` " +
                        "ON `whatsapp_messages` (`timestampMs`)"
                )
            }
        }

        @Volatile private var instance: MannAIDatabase? = null

        fun get(context: Context): MannAIDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MannAIDatabase::class.java,
                    "mannai.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
