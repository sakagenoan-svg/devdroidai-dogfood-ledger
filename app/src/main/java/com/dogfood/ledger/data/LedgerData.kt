package com.dogfood.ledger.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val monthlyBudgetMinor: Long,
)

@Entity(
    tableName = "transactions",
    indices = [Index("categoryId"), Index("epochDay")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    /** Negative = expense, positive = income. Stored as minor units. */
    val amountMinor: Long,
    val note: String,
    val epochDay: Long,
)

@Dao
interface LedgerDao {

    @Query("SELECT * FROM categories ORDER BY name")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, id DESC")
    fun transactions(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE categoryId = :categoryId AND epochDay >= :fromDay AND amountMinor < 0",
    )
    fun spentSince(categoryId: String, fromDay: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE epochDay >= :fromDay AND amountMinor > 0",
    )
    fun incomeThisMonth(fromDay: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(amountMinor), 0) FROM transactions " +
            "WHERE epochDay >= :fromDay AND amountMinor < 0",
    )
    fun expenseThisMonth(fromDay: Long): Flow<Long>

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Upsert
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(tx: TransactionEntity)
}

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
}
