package com.envanter.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomWarnings
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM items WHERE deleted = 0")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM items WHERE deleted = 0")
    suspend fun getAll(): List<FoodItem>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): FoodItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FoodItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodItem>)

    @Query("SELECT * FROM items")
    suspend fun getAllIncludingDeleted(): List<FoodItem>

    /**
     * Daha önce eklenmiş ürün adı önerileri. Silinen ürünler de sayılır: bitirip
     * tekrar aldığın ürünün adı (özellikle fotoğraftan gelen markalı tam ad)
     * hafızada kalsın diye. Sık eklenen üstte, eşitlikte en son eklenen üstte.
     */
    // MAX(updatedAt) kolonu FoodItem'a maplenmez; sadece grup içi satır seçimi için var.
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query(
        // MAX(updatedAt) SELECT içinde: SQLite'ta gruptaki diğer kolonlar da o satırdan
        // gelir, yani kategori/birim en son eklenen kayıttan okunur (rastgele değil).
        "SELECT *, MAX(updatedAt) FROM items WHERE name LIKE :q ESCAPE '!' " +
            "GROUP BY LOWER(name) ORDER BY COUNT(*) DESC, MAX(updatedAt) DESC LIMIT :limit"
    )
    suspend fun suggestByName(q: String, limit: Int = 6): List<FoodItem>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryDef>>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    suspend fun getAll(): List<CategoryDef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryDef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryDef>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface HomeDao {
    @Query("SELECT * FROM homes WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<Home>>

    @Query("SELECT * FROM homes WHERE deleted = 0 ORDER BY sortOrder")
    suspend fun getAll(): List<Home>

    @Query("SELECT * FROM homes")
    suspend fun getAllIncludingDeleted(): List<Home>

    @Query("SELECT * FROM homes WHERE id = :id")
    suspend fun get(id: String): Home?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(home: Home)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(homes: List<Home>)

    @Query("SELECT COUNT(*) FROM homes")
    suspend fun count(): Int

    /**
     * Silinen evin ürünlerini başka bir eve taşır (ürünler kaybolmasın).
     * [alsoSource] eski, evsiz kayıtlar içindir: boş homeId varsayılan eve sayılır.
     */
    @Query("UPDATE items SET homeId = :target, updatedAt = :stamp WHERE homeId = :source OR homeId = :alsoSource")
    suspend fun moveItems(
        source: String,
        target: String,
        alsoSource: String = source,
        stamp: Long = System.currentTimeMillis()
    )
}

@Dao
interface ShelfLifeDao {
    @Query("SELECT * FROM shelf_life")
    fun observeAll(): Flow<List<ShelfLife>>

    @Query("SELECT * FROM shelf_life")
    suspend fun getAll(): List<ShelfLife>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ShelfLife>)

    @Query("SELECT COUNT(*) FROM shelf_life")
    suspend fun count(): Int
}

@Database(
    entities = [FoodItem::class, CategoryDef::class, ShelfLife::class, Home::class],
    version = 5,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shelfLifeDao(): ShelfLifeDao
    abstract fun homeDao(): HomeDao

    companion object {
        /** items tablosunu koruyarak categories tablosunu ekler. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "label TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "redDays INTEGER NOT NULL, " +
                        "yellowDays INTEGER NOT NULL, " +
                        "keywords TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL)"
                )
            }
        }

        /** items/categories'e dokunmadan ürün bazlı raf ömrü tablosunu ekler. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shelf_life (" +
                        "keyword TEXT NOT NULL PRIMARY KEY, " +
                        "days INTEGER NOT NULL)"
                )
            }
        }

        /** Tarihin otomatik mi atandığını (ve kaç günle) tutan kolonu ekler. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN expiryAutoDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * "Açıldı" işareti ve çoklu ev desteği: ürünlere açılma tarihi/süresi ve
         * ev kimliği, raf ömrü tablosuna açıldıktan sonraki gün sayısı eklenir.
         * Mevcut ürünler varsayılan eve ([HomeStore.DEFAULT_ID]) bağlanır.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN openedDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN openedDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN homeId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE shelf_life ADD COLUMN openedDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS homes (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "deleted INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO homes (id, name, emoji, sortOrder, updatedAt, deleted) " +
                        "VALUES ('${HomeStore.DEFAULT_ID}', 'Evim', '🏠', 0, ${System.currentTimeMillis()}, 0)"
                )
                db.execSQL("UPDATE items SET homeId = '${HomeStore.DEFAULT_ID}' WHERE homeId = ''")
            }
        }

        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "envanter.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
