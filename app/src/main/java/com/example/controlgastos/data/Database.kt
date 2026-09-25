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
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limitCents: Long,
    val warningCents: Long
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

@Dao
interface BudgetDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts") suspend fun accountsNow(): List<Account>
    @Query("SELECT * FROM expenses WHERE accountId = :accountId ORDER BY date DESC, id DESC") fun expenses(accountId: Long): Flow<List<Expense>>
    @Query("SELECT * FROM monthly_summaries WHERE accountId = :accountId ORDER BY month DESC") fun summaries(accountId: Long): Flow<List<MonthlySummary>>
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM expenses WHERE accountId = :accountId") suspend fun total(accountId: Long): Long
    @Insert suspend fun addAccount(account: Account): Long
    @Query("UPDATE accounts SET name = :name, limitCents = :limit, warningCents = :warning WHERE id = :id") suspend fun updateAccount(id: Long, name: String, limit: Long, warning: Long)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Insert suspend fun addExpense(expense: Expense): Long
    @Query("UPDATE expenses SET date = :date, amountCents = :amount, description = :description WHERE id = :id") suspend fun updateExpense(id: Long, date: String, amount: Long, description: String)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteExpense(id: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSummary(summary: MonthlySummary)
    @Query("DELETE FROM expenses") suspend fun clearExpenses()

    @Transaction
    suspend fun archiveAndReset(month: String, accounts: List<Account>) {
        accounts.forEach { account ->
            val spent = total(account.id)
            addSummary(MonthlySummary(accountId = account.id, month = month, limitCents = account.limitCents, spentCents = spent, balanceCents = account.limitCents - spent))
        }
        clearExpenses()
    }
}

@Database(entities = [Account::class, Expense::class, MonthlySummary::class], version = 1, exportSchema = false)
abstract class BudgetDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao
    companion object {
        fun create(context: Context): BudgetDatabase = Room.databaseBuilder(context, BudgetDatabase::class.java, "control-gastos.db").build()
    }
}

class BudgetRepository(context: Context) {
    private val dao = BudgetDatabase.create(context).dao()
    private val preferences = context.getSharedPreferences("budget_cycle", Context.MODE_PRIVATE)
    val accounts = dao.accounts()
    fun expenses(accountId: Long) = dao.expenses(accountId)
    fun summaries(accountId: Long) = dao.summaries(accountId)

    /** Archives only totals, then permanently clears the movements of the closed month. */
    suspend fun processMonthChange() {
        val current = YearMonth.now().toString()
        val last = preferences.getString("active_month", null)
        if (last == null) preferences.edit().putString("active_month", current).apply()
        else if (last != current) {
            dao.archiveAndReset(last, dao.accountsNow())
            preferences.edit().putString("active_month", current).apply()
        }
    }
    suspend fun saveAccount(id: Long?, name: String, limit: Long, warning: Long) {
        if (id == null) dao.addAccount(Account(name = name.trim(), limitCents = limit, warningCents = warning))
        else dao.updateAccount(id, name.trim(), limit, warning)
    }
    suspend fun deleteAccount(id: Long) = dao.deleteAccount(id)
    suspend fun saveExpense(expense: Expense) {
        if (expense.id == 0L) dao.addExpense(expense)
        else dao.updateExpense(expense.id, expense.date, expense.amountCents, expense.description.trim())
    }
    suspend fun deleteExpense(id: Long) = dao.deleteExpense(id)
}
