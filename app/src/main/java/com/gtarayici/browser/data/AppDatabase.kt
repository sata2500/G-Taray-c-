package com.gtarayici.browser.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", indices = [Index("lastVisited")])
data class HistoryEntry(
    @PrimaryKey val url: String,
    val title: String,
    val lastVisited: Long,
    val visitCount: Int = 1,
)

@Entity(tableName = "bookmarks", indices = [Index("url", unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisited DESC LIMIT :limit")
    fun recent(limit: Int = 500): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' " +
            "ORDER BY visitCount DESC, lastVisited DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 8): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE url = :url")
    suspend fun find(url: String): HistoryEntry?

    @Upsert
    suspend fun upsert(entry: HistoryEntry)

    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Query(
        "SELECT * FROM bookmarks WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC LIMIT :limit",
    )
    suspend fun search(query: String, limit: Int = 4): List<Bookmark>

    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gtarayici.db").build()
    }
}
