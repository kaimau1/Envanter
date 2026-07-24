package com.envanter.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(entities = [FoodItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "envanter.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
