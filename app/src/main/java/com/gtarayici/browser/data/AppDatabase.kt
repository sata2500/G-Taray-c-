package com.gtarayici.browser.data

import android.content.Context
import androidx.room.ColumnInfo
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
    /** Klasör adı; boşsa yer imi kök düzeydedir. */
    @ColumnInfo(defaultValue = "") val folder: String = "",
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

    /** Öneriler için aday listesi; eşleştirme Türkçe harfleri tanıyan [com.gtarayici.browser.search.TextMatch] ile yapılır. */
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitCount DESC, lastVisited DESC LIMIT :limit")
    suspend fun candidates(profileId: String, limit: Int = 2000): List<HistoryEntry>

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

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    suspend fun list(profileId: String): List<Bookmark>

    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId AND url = :url")
    suspend fun deleteByUrl(profileId: String, url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Klasörü yeniden adlandırır (boş ad: klasörü kaldırıp yer imlerini köke taşır). */
    @Query("UPDATE bookmarks SET folder = :newName WHERE profileId = :profileId AND folder = :oldName")
    suspend fun renameFolder(profileId: String, oldName: String, newName: String)

    @Query("DELETE FROM bookmarks WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}

/**
 * Kayıtlı parola. Kullanıcı adı ve parola Android anahtar deposundaki anahtarla şifrelenmiş olarak tutulur
 * (bkz. passwords/LoginCrypto); site adresi aramak için açık tutulur.
 */
@Entity(tableName = "logins", indices = [Index(value = ["profileId", "host"])])
data class LoginRow(
    @PrimaryKey val guid: String,
    val profileId: String,
    val origin: String,
    val host: String,
    val formActionOrigin: String?,
    val httpRealm: String?,
    val usernameField: String,
    val passwordField: String,
    val username: String,
    val password: String,
    val timesUsed: Long,
    val timeCreated: Long,
    val timeLastUsed: Long,
    val timePasswordChanged: Long,
)

@Dao
interface LoginDao {
    @Query("SELECT * FROM logins WHERE profileId = :profileId ORDER BY host")
    fun all(profileId: String): Flow<List<LoginRow>>

    @Query("SELECT * FROM logins WHERE profileId = :profileId")
    suspend fun allOnce(profileId: String): List<LoginRow>

    @Query("SELECT * FROM logins")
    suspend fun everything(): List<LoginRow>

    @Query("SELECT * FROM logins WHERE guid = :guid")
    suspend fun get(guid: String): LoginRow?

    @Upsert
    suspend fun upsert(row: LoginRow)

    @Query("DELETE FROM logins WHERE guid = :guid")
    suspend fun delete(guid: String)

    @Query("DELETE FROM logins WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}

@Database(
    entities = [Profile::class, HistoryEntry::class, Bookmark::class, LoginRow::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao

    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    abstract fun logins(): LoginDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gtarayici.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v0.11: parola yöneticisi. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `logins` (`guid` TEXT NOT NULL, `profileId` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                        "`host` TEXT NOT NULL, `formActionOrigin` TEXT, `httpRealm` TEXT, `usernameField` TEXT NOT NULL, " +
                        "`passwordField` TEXT NOT NULL, `username` TEXT NOT NULL, `password` TEXT NOT NULL, " +
                        "`timesUsed` INTEGER NOT NULL, `timeCreated` INTEGER NOT NULL, `timeLastUsed` INTEGER NOT NULL, " +
                        "`timePasswordChanged` INTEGER NOT NULL, PRIMARY KEY(`guid`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_logins_profileId_host` ON `logins` (`profileId`, `host`)")
            }
        }

        /** v0.7: yer imi klasörleri. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `bookmarks` ADD COLUMN `folder` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v0.1.x verileri varsayılan profile taşınır; hiçbir geçmiş veya yer imi kaybolmaz. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }
    }
}
