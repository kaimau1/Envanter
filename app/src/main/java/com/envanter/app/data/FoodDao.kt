package com.envanter.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(entities = [FoodItem::class, CategoryDef::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun categoryDao(): CategoryDao

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

        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "envanter.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
