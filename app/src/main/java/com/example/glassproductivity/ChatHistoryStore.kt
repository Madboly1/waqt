package com.example.glassproductivity

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/** Local-only chat history. No conversation content is uploaded by this store. */
class ChatHistoryStore(context: Context) : SQLiteOpenHelper(context, "waqt_chat_history.db", null, 1) {
    data class Chat(val id: String, val title: String, val updatedAt: Long)
    data class Message(val id: Long, val chatId: String, val role: String, val content: String, val createdAt: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chats (id TEXT PRIMARY KEY, title TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(chat_id) REFERENCES chats(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX idx_messages_chat ON messages(chat_id, created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized fun createChat(firstMessage: String = ""): String {
        val id = UUID.randomUUID().toString()
        val title = firstMessage.replace("\n", " ").trim().take(42).ifBlank { "محادثة جديدة" }
        writableDatabase.insert("chats", null, ContentValues().apply {
            put("id", id); put("title", title); put("updated_at", System.currentTimeMillis())
        })
        return id
    }

    @Synchronized fun ensureChat(id: String?): String {
        if (!id.isNullOrBlank() && readableDatabase.rawQuery("SELECT id FROM chats WHERE id=?", arrayOf(id)).use { it.moveToFirst() }) return id
        return createChat()
    }

    @Synchronized fun addMessage(chatId: String, role: String, content: String) {
        if (content.isBlank()) return
        val now = System.currentTimeMillis()
        writableDatabase.beginTransaction()
        try {
            val exists = writableDatabase.rawQuery("SELECT id FROM chats WHERE id=?", arrayOf(chatId)).use { it.moveToFirst() }
            if (!exists) writableDatabase.insert("chats", null, ContentValues().apply {
                put("id", chatId); put("title", content.replace("\n", " ").take(42).ifBlank { "محادثة جديدة" }); put("updated_at", now)
            })
            writableDatabase.insert("messages", null, ContentValues().apply {
                put("chat_id", chatId); put("role", role); put("content", content); put("created_at", now)
            })
            if (role == "user") {
                writableDatabase.execSQL(
                    "UPDATE chats SET title=CASE WHEN title='محادثة جديدة' OR title='New chat' THEN ? ELSE title END, updated_at=? WHERE id=?",
                    arrayOf(content.replace("\n", " ").trim().take(42).ifBlank { "محادثة جديدة" }, now, chatId)
                )
            } else {
                writableDatabase.update("chats", ContentValues().apply { put("updated_at", now) }, "id=?", arrayOf(chatId))
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    @Synchronized fun listChats(limit: Int = 100): List<Chat> {
        val out = mutableListOf<Chat>()
        readableDatabase.rawQuery("SELECT id,title,updated_at FROM chats ORDER BY updated_at DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while (c.moveToNext()) out += Chat(c.getString(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    @Synchronized fun getMessages(chatId: String): List<Message> {
        val out = mutableListOf<Message>()
        readableDatabase.rawQuery("SELECT id,chat_id,role,content,created_at FROM messages WHERE chat_id=? ORDER BY created_at,id", arrayOf(chatId)).use { c ->
            while (c.moveToNext()) out += Message(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4))
        }
        return out
    }

    @Synchronized fun deleteChat(chatId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("messages", "chat_id=?", arrayOf(chatId))
            writableDatabase.delete("chats", "id=?", arrayOf(chatId))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
}
