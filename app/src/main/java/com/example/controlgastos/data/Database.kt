package com.example.controlgastos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitCents: Long,
    val warningCents: Long,
    /** User-controlled position in the account bar. */
    val sortOrder: Long = 0
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** ISO-8601 date: yyyy-MM-dd */ val date: String,
    val amountCents: Long,
    val description: String = ""
)

@Entity(
    tableName = "monthly_summaries",
    foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")]
)
data class MonthlySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** yyyy-MM */ val month: String,
    val limitCents: Long,
    val spentCents: Long,
    val balanceCents: Long
)

@Entity(
    tableName = "annual_summaries",
    primaryKeys = ["accountId", "year"],
    foreignKeys = [ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("accountId")]
)
data class AnnualSummary(
    val accountId: Long,
    val year: String,
    val spentCents: Long,
    /** Sum of the positive balances across the year's accounts and months. */
    val savedCents: Long
)

data class YearTotals(val spentCents: Long, val savedCents: Long)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC") suspend fun accountsNow(): List<Account>
    @Query("SELECT * FROM expenses WHERE accountId = :accountId ORDER BY date DESC, id DESC") fun expenses(accountId: Long): Flow<List<Expense>>
    @Query("SELECT * FROM monthly_summaries WHERE accountId = :accountId ORDER BY month DESC") fun summaries(accountId: Long): Flow<List<MonthlySummary>>
    @Query("SELECT * FROM annual_summaries WHERE accountId = :accountId ORDER BY year DESC") fun annualSummaries(accountId: Long): Flow<List<AnnualSummary>>
    @Query("""
        SELECT
          COALESCE((SELECT SUM(spentCents) FROM monthly_summaries WHERE accountId = :accountId AND substr(month, 1, 4) = :year), 0) AS spentCents,
          COALESCE((SELECT SUM(balanceCents) FROM monthly_summaries WHERE accountId = :accountId AND substr(month, 1, 4) = :year), 0) AS savedCents
    """) fun currentYearTotals(accountId: Long, year: String): Flow<YearTotals>
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE accountId = :accountId") suspend fun total(accountId: Long): Long
    @Insert suspend fun addAccount(account: Account): Long
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts") suspend fun nextSortOrder(): Long
    @Query("UPDATE accounts SET name = :name, limitCents = :limit, warningCents = :warning WHERE id = :id") suspend fun updateAccount(id: Long, name: String, limit: Long, warning: Long)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("UPDATE accounts SET sortOrder = :position WHERE id = :id") suspend fun updateSortOrder(id: Long, position: Long)
    @Insert suspend fun addExpense(expense: Expense): Long
    @Query("UPDATE expenses SET date = :date, amountCents = :amount, description = :description WHERE id = :id") suspend fun updateExpense(id: Long, date: String, amount: Long, description: String)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteExpense(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSummary(summary: MonthlySummary)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addAnnualSummary(summary: AnnualSummary)
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("SELECT COALESCE(SUM(spentCents), 0) FROM monthly_summaries WHERE accountId = :accountId AND substr(month, 1, 4) = :year") suspend fun yearlySpent(accountId: Long, year: String): Long
    @Query("SELECT COALESCE(SUM(balanceCents), 0) FROM monthly_summaries WHERE accountId = :accountId AND substr(month, 1, 4) = :year") suspend fun yearlySaved(accountId: Long, year: String): Long

    @Transaction
    suspend fun archiveAndReset(month: String, accounts: List<Account>) {
        accounts.forEach { account ->
            val spent = total(account.id)
            addSummary(MonthlySummary(accountId = account.id, month = month, limitCents = account.limitCents, spentCents = spent, balanceCents = account.limitCents - spent))
        }
        clearExpenses()
    }

    @Transaction
    suspend fun saveAccountOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> updateSortOrder(id, index.toLong()) }
    }

    @Transaction
    suspend fun archiveYear(year: String, accounts: List<Account>) {
        accounts.forEach { account ->
            addAnnualSummary(AnnualSummary(account.id, year, yearlySpent(account.id, year), yearlySaved(account.id, year)))
        }
    }
}

@Database(entities = [Account::class, Expense::class, MonthlySummary::class, AnnualSummary::class], version = 4, exportSchema = false)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao
    companion object {
        private val migration1to2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE TABLE IF NOT EXISTS annual_summaries (year TEXT NOT NULL, spentCents INTEGER NOT NULL, savedCents INTEGER NOT NULL, PRIMARY KEY(year))")
            }
        }
        private val migration2to3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE annual_summaries RENAME TO annual_summaries_old")
                database.execSQL("CREATE TABLE annual_summaries (accountId INTEGER NOT NULL, year TEXT NOT NULL, spentCents INTEGER NOT NULL, savedCents INTEGER NOT NULL, PRIMARY KEY(accountId, year), FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_annual_summaries_accountId ON annual_summaries(accountId)")
                database.execSQL("INSERT INTO annual_summaries (accountId, year, spentCents, savedCents) SELECT accountId, substr(month, 1, 4), SUM(spentCents), SUM(CASE WHEN balanceCents > 0 THEN balanceCents ELSE 0 END) FROM monthly_summaries GROUP BY accountId, substr(month, 1, 4)")
                database.execSQL("DROP TABLE annual_summaries_old")
            }
        }
        private val migration3to4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM annual_summaries")
                database.execSQL("INSERT INTO annual_summaries (accountId, year, spentCents, savedCents) SELECT accountId, substr(month, 1, 4), SUM(spentCents), SUM(balanceCents) FROM monthly_summaries GROUP BY accountId, substr(month, 1, 4)")
            }
        }
        fun create(context: Context): BudgetDatabase = Room.databaseBuilder(context, BudgetDatabase::class.java, "control-gastos.db").addMigrations(migration1to2, migration2to3, migration3to4).build()
    }
}

class BudgetRepository(context: Context) {
    private val dao = BudgetDatabase.create(context).dao()
    private val preferences = context.getSharedPreferences("budget_cycle", Context.MODE_PRIVATE)
    val accounts = dao.accounts()
    fun expenses(accountId: Long) = dao.expenses(accountId)
    fun summaries(accountId: Long) = dao.summaries(accountId)
    fun annualSummaries(accountId: Long) = dao.annualSummaries(accountId)
    fun currentYearTotals(accountId: Long) = dao.currentYearTotals(accountId, YearMonth.now().year.toString())

    /** Archives only totals, then permanently clears the movements of the closed month. */
    suspend fun processMonthChange() {
        val current = YearMonth.now().toString()
        val last = preferences.getString("active_month", null)
        if (last == null) preferences.edit().putString("active_month", current).apply()
        else if (last != current) {
            val accountList = dao.accountsNow()
            dao.archiveAndReset(last, accountList)
            val completedYear = YearMonth.parse(last).year
            if (completedYear < YearMonth.now().year) dao.archiveYear(completedYear.toString(), accountList)
            preferences.edit().putString("active_month", current).apply()
        }
    }
    suspend fun saveAccount(id: Long?, name: String, limit: Long, warning: Long) {
        if (id == null) dao.addAccount(Account(name = name.trim(), limitCents = limit, warningCents = warning, sortOrder = dao.nextSortOrder()))
        else dao.updateAccount(id, name.trim(), limit, warning)
    }
    suspend fun deleteAccount(id: Long) = dao.deleteAccount(id)
    suspend fun saveAccountOrder(ids: List<Long>) = dao.saveAccountOrder(ids)
    fun lastSelectedAccountId(): Long? = preferences.getLong("last_selected_account", -1L).takeIf { it >= 0L }
    fun saveLastSelectedAccount(id: Long) { preferences.edit().putLong("last_selected_account", id).apply() }
    fun clearLastSelectedAccount() { preferences.edit().remove("last_selected_account").apply() }
    suspend fun saveExpense(expense: Expense) {
        if (expense.id == 0L) dao.addExpense(expense)
        else dao.updateExpense(expense.id, expense.date, expense.amountCents, expense.description.trim())
    }
    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)
}
