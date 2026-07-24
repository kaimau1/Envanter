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

@Database(entities = [FoodItem::class, CategoryDef::class, ShelfLife::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shelfLifeDao(): ShelfLifeDao

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

        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "envanter.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
