package com.msma.tgpanel.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.msma.tgpanel.core.idsToDelete
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lastText: String,
    val lastSender: String,
    val time: String,
    val isGroup: Boolean,
    val bizId: String,            // "" = not a Telegram-business chat
    val hasBotChat: Boolean,
    val muted: Boolean,
    val pinned: Boolean,
    val aiPending: Boolean,
    val unread: Int,
    val orderIdx: Int,            // position in the server's list (newest activity first)
)

@Entity(
    tableName = "messages",
    primaryKeys = ["chatId", "id"],
    indices = [Index(value = ["chatId", "sortKey"])],
)
data class MessageEntity(
    val chatId: String,
    val id: String,
    val sortKey: Long,
    val msgId: String,
    val role: String,
    val text: String,
    val sender: String,
    val time: String,
    val ts: Long,
    val type: String,
    val fileId: String,
    val replyToMsgId: String,
    val replyPreview: String,
    val forwardedFrom: String,
    val myReaction: String,
    val theirReaction: String,
)

@Dao
abstract class PanelDao {
    // ---- chats ----
    @Query("SELECT * FROM chats ORDER BY pinned DESC, orderIdx ASC")
    abstract fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    abstract fun observeChat(id: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats")
    abstract suspend fun allChats(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertChats(chats: List<ChatEntity>)

    @Query("DELETE FROM chats WHERE id NOT IN (:keep)")
    abstract suspend fun deleteChatsNotIn(keep: List<String>)

    @Query("DELETE FROM messages WHERE chatId NOT IN (:keep)")
    abstract suspend fun deleteMessagesOfChatsNotIn(keep: List<String>)

    @Query("UPDATE chats SET unread = :n WHERE id = :id")
    abstract suspend fun setUnread(id: String, n: Int)

    // ---- messages ----
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY sortKey ASC, id ASC")
    abstract fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT id FROM messages WHERE chatId = :chatId")
    abstract suspend fun messageIds(chatId: String): List<String>

    @Query("SELECT id FROM messages WHERE chatId = :chatId ORDER BY sortKey ASC, id ASC LIMIT 1")
    abstract suspend fun oldestId(chatId: String): String?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    abstract suspend fun messageCount(chatId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND id IN (:ids)")
    abstract suspend fun deleteMessages(chatId: String, ids: List<String>)

    /** Applies a fetched page with the API.md merging rule: rows the page proves deleted go, the rest is upserted. */
    @Transaction
    open suspend fun mergePage(chatId: String, page: List<MessageEntity>, newest: Boolean) {
        val gone = idsToDelete(messageIds(chatId), page.map { it.id }, newest)
        for (chunk in gone.chunked(500)) deleteMessages(chatId, chunk)    // SQLite caps bound variables
        upsertMessages(page)
    }

    @Query("DELETE FROM chats")
    abstract suspend fun clearChats()

    @Query("DELETE FROM messages")
    abstract suspend fun clearMessages()
}

@Database(entities = [ChatEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class PanelDb : RoomDatabase() {
    abstract fun dao(): PanelDao

    companion object {
        fun create(ctx: Context): PanelDb =
            Room.databaseBuilder(ctx, PanelDb::class.java, "panel.db")
                .fallbackToDestructiveMigration()      // it is only a cache of the server — rebuilding is always safe
                .build()
    }
}
