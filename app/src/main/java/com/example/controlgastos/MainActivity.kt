package com.example.controlgastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.controlgastos.data.Account
import com.example.controlgastos.data.BudgetRepository
import com.example.controlgastos.data.Expense
import com.example.controlgastos.ui.ControlGastosApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BudgetViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = BudgetRepository(application)
    val accounts = repository.accounts
    val selectedAccountId = MutableStateFlow<Long?>(null)
    val expenses = selectedAccountId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.expenses(id) }
    val summaries = selectedAccountId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.summaries(id) }
    val currentYearTotals = selectedAccountId.flatMapLatest { id -> if (id == null) flowOf(com.example.controlgastos.data.YearTotals(0, 0)) else repository.currentYearTotals(id) }
    val annualSummaries = selectedAccountId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.annualSummaries(id) }
    val appReady = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.processMonthChange()
            val loadedAccounts = repository.accounts.first()
            repository.lastSelectedAccountId()?.takeIf { saved -> loadedAccounts.any { it.id == saved } }?.let { selectedAccountId.value = it }
            delay(900)
            appReady.value = true
        }
    }
    fun select(id: Long) { selectedAccountId.value = id; repository.saveLastSelectedAccount(id) }
    fun saveAccount(id: Long?, name: String, limit: Long, warning: Long) = viewModelScope.launch { repository.saveAccount(id, name, limit, warning) }
    fun deleteAccount(id: Long) = viewModelScope.launch { repository.deleteAccount(id); if (selectedAccountId.value == id) { selectedAccountId.value = null; repository.clearLastSelectedAccount() } }
    fun saveAccountOrder(ids: List<Long>) = viewModelScope.launch { repository.saveAccountOrder(ids) }
    fun saveExpense(expense: Expense) = viewModelScope.launch { repository.saveExpense(expense) }
    fun deleteExpense(id: Long) = viewModelScope.launch { repository.deleteExpense(id) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[BudgetViewModel::class.java]
        setContent { ControlGastosApp(model) }
    }
}
