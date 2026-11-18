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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val DEFAULT_PROFILE_ID = "default"

/**
 * Bir tarayıcı profili. Her profilin GeckoView "contextId" değeri ayrıdır; böylece çerezler,
 * site verileri, oturumlar, geçmiş ve yer imleri profiller arasında tamamen ayrışır.
 * Varsayılan profilin contextId değeri null'dır (ilk sürümlerden kalan verilerle uyumlu).
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    val colorIndex: Int,
    val contextId: String?,
    val locked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "history",
    primaryKeys = ["profileId", "url"],
    indices = [Index("lastVisited")],
)
data class HistoryEntry(
    val profileId: String,
    val url: String,
    val title: String,
    val lastVisited: Long,
    val visitCount: Int = 1,
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["profileId", "url"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String = DEFAULT_PROFILE_ID,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun all(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    suspend fun list(): List<Profile>

    @Upsert
    suspend fun upsert(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY lastVisited DESC LIMIT :limit")
    fun recent(profileId: String, limit: Int = 500): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE profileId = :profileId AND (url LIKE '%' || :query || '%' OR " +
            "title LIKE '%' || :query || '%') ORDER BY visitCount DESC, lastVisited DESC LIMIT :limit",
    )
    suspend fun search(profileId: String, query: String, limit: Int = 8): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE profileId = :profileId AND url = :url")
    suspend fun find(profileId: String, url: String): HistoryEntry?

    @Upsert
    suspend fun upsert(entry: HistoryEntry)

    @Query("UPDATE history SET title = :title WHERE profileId = :profileId AND url = :url")
    suspend fun updateTitle(profileId: String, url: String, title: String)

    @Query("DELETE FROM history WHERE profileId = :profileId AND url = :url")
    suspend fun delete(profileId: String, url: String)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clear(profileId: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun all(profileId: String): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE profileId = :profileId AND url = :url)")
    fun isBookmarked(profileId: String, url: String): Flow<Boolean>

    @Query(
        "SELECT * FROM bookmarks WHERE profileId = :profileId AND (url LIKE '%' || :query || '%' OR " +
            "title LIKE '%' || :query || '%') ORDER BY createdAt DESC LIMIT :limit",
    )
    suspend fun search(profileId: String, query: String, limit: Int = 4): List<Bookmark>

    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId AND url = :url")
    suspend fun deleteByUrl(profileId: String, url: String)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}

@Database(
    entities = [Profile::class, HistoryEntry::class, Bookmark::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao

    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gtarayici.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v0.1.x verileri varsayılan profile taşınır; hiçbir geçmiş veya yer imi kaybolmaz. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }
    }
}
