package com.example.controlgastos.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.controlgastos.BudgetViewModel
import com.example.controlgastos.data.Account
import com.example.controlgastos.data.Expense
import com.example.controlgastos.data.MonthlySummary
import com.example.controlgastos.data.AnnualSummary
import com.example.controlgastos.data.YearTotals
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
private fun money(cents: Long) = currency.format(cents / 100.0)
private fun parseEuros(text: String): Long? = text.trim().replace('.', '#').replace(',', '.').replace('#', '.').toBigDecimalOrNull()?.movePointRight(2)?.toLong()

@Composable
fun ControlGastosApp(viewModel: BudgetViewModel) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle(initialValue = emptyList())
    val summaries by viewModel.summaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentYearTotals by viewModel.currentYearTotals.collectAsStateWithLifecycle(initialValue = YearTotals(0, 0))
    val annualSummaries by viewModel.annualSummaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val selected = accounts.find { it.id == selectedId }
    var showHistory by remember { mutableStateOf(false) }
    var showAnnualHistory by remember { mutableStateOf(false) }
    var accountEditor by remember { mutableStateOf<Account?>(null) }
    var addingAccount by remember { mutableStateOf(false) }
    var expenseEditor by remember { mutableStateOf<Expense?>(null) }
    var addingExpense by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf<Account?>(null) }
    var deletingExpense by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(accounts, selectedId) {
        if (accounts.isNotEmpty() && accounts.none { it.id == selectedId }) viewModel.select(accounts.first().id)
    }

    MaterialTheme {
        Scaffold(
            topBar = { Header(showHistory && !showAnnualHistory, { showHistory = !showHistory; showAnnualHistory = false }, { addingAccount = true }) },
            floatingActionButton = {
                if (!showHistory && selected != null) FloatingActionButton(onClick = { addingExpense = true }) { Text("+", fontWeight = FontWeight.Bold, fontSize = 40.sp) }
            }
        ) { padding ->
            if (accounts.isEmpty()) EmptyState(Modifier.padding(padding), { addingAccount = true })
            else Column(Modifier.fillMaxSize().padding(padding)) {
                AccountChooser(accounts, selectedId, viewModel::select, viewModel::saveAccountOrder)
                if (selected != null) {
                    if (showAnnualHistory) AnnualHistoryScreen(selected, currentYearTotals, annualSummaries, onBack = { showAnnualHistory = false })
                    else if (showHistory) HistoryScreen(selected, summaries, currentYearTotals, onShowAnnualHistory = { showAnnualHistory = true })
                    else ExpenseScreen(selected, expenses, onEditAccount = { accountEditor = selected }, onDeleteAccount = { deletingAccount = selected }, onEditExpense = { expenseEditor = it }, onDeleteExpense = { deletingExpense = it })
                }
            }
        }
    }
    if (addingAccount) AccountDialog(null, onClose = { addingAccount = false }) { name, limit, warning -> viewModel.saveAccount(null, name, limit, warning); addingAccount = false }
    accountEditor?.let { account -> AccountDialog(account, onClose = { accountEditor = null }) { name, limit, warning -> viewModel.saveAccount(account.id, name, limit, warning); accountEditor = null } }
    if (addingExpense && selected != null) ExpenseDialog(selected.id, null, onClose = { addingExpense = false }) { expense -> viewModel.saveExpense(expense); addingExpense = false }
    expenseEditor?.let { expense -> ExpenseDialog(expense.accountId, expense, onClose = { expenseEditor = null }) { changed -> viewModel.saveExpense(changed); expenseEditor = null } }
    deletingAccount?.let { account -> ConfirmDialog("¿Eliminar ${account.name}?", "También se borrará permanentemente todo su histórico mensual.", { deletingAccount = null }) { viewModel.deleteAccount(account.id); deletingAccount = null } }
    deletingExpense?.let { expense -> ConfirmDialog("¿Eliminar este gasto?", "Esta acción no se puede deshacer.", { deletingExpense = null }) { viewModel.deleteExpense(expense.id); deletingExpense = null } }
}

@Composable private fun Header(history: Boolean, onHistory: () -> Unit, onNewAccount: () -> Unit) = Surface(shadowElevation = 3.dp) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Gastos💸", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onHistory) { Text(if (history) "Gastos" else "Histórico") }
        TextButton(onClick = onNewAccount) { Text("Nueva Cuenta") }
    }
}

@Composable private fun AccountChooser(accounts: List<Account>, selected: Long?, onSelect: (Long) -> Unit, onOrderChange: (List<Long>) -> Unit) {
    var orderedIds by remember { mutableStateOf(emptyList<Long>()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    LaunchedEffect(accounts) {
        val incoming = accounts.map { it.id }
        orderedIds = if (orderedIds.toSet() == incoming.toSet()) orderedIds else incoming
    }
    val shownAccounts = (if (orderedIds.isEmpty()) accounts.map { it.id } else orderedIds).mapNotNull { id -> accounts.find { it.id == id } }
    LazyRow(state = listState, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(shownAccounts, key = { _, account -> account.id }) { _, account ->
        val active = account.id == selected
        val isDragging = draggingId == account.id
        Button(
            onClick = { onSelect(account.id) },
            colors = if (active) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) {
                        translationX = dragOffset
                        shadowElevation = 18f
                    }
                }
                .pointerInput(account.id, orderedIds) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingId = account.id; dragOffset = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount.x
                        val layout = listState.layoutInfo
                        if (change.position.x > layout.viewportEndOffset - 48f) listState.dispatchRawDelta(24f)
                        else if (change.position.x < layout.viewportStartOffset + 48f) listState.dispatchRawDelta(-24f)
                        val draggedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == account.id }
                        val targetItem = draggedItem?.let { item ->
                            val center = item.offset + item.size / 2 + dragOffset
                            listState.layoutInfo.visibleItemsInfo.minByOrNull { visible -> abs((visible.offset + visible.size / 2) - center) }
                        }
                        val targetId = targetItem?.key as? Long
                        if (targetId != null && targetId != account.id) {
                            val from = orderedIds.indexOf(account.id)
                            val to = orderedIds.indexOf(targetId)
                            if (from >= 0 && to >= 0) {
                                val oldOffset = draggedItem?.offset ?: 0
                                val newOffset = targetItem.offset
                                val reordered = orderedIds.toMutableList().apply { removeAt(from); add(to, account.id) }
                                orderedIds = reordered
                                dragOffset += oldOffset - newOffset
                                onOrderChange(reordered)
                            }
                        }
                    },
                    onDragEnd = { draggingId = null; dragOffset = 0f },
                    onDragCancel = { draggingId = null; dragOffset = 0f }
                )
            }
        ) { Text(account.name) }
        }
    }
}

@Composable private fun EmptyState(modifier: Modifier, onNew: () -> Unit) = Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text("Crea tu primera cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp)); Text("Por ejemplo: Obligatorios, Ocio o Ahorro. Cada una tendrá su propio límite mensual.")
    Spacer(Modifier.height(22.dp)); Button(onClick = onNew) { Text("Crear cuenta") }
}

@Composable private fun ExpenseScreen(account: Account, expenses: List<Expense>, onEditAccount: () -> Unit, onDeleteAccount: () -> Unit, onEditExpense: (Expense) -> Unit, onDeleteExpense: (Expense) -> Unit) {
    val spent = expenses.sumOf { it.amountCents }; val balance = account.limitCents - spent
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BudgetCard(account, spent, balance, onEditAccount, onDeleteAccount) }
        item { Text("Gastos de este mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        if (expenses.isEmpty()) item { Text("Todavía no has añadido ningún gasto.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(expenses, key = { it.id }) { expense -> ExpenseRow(expense, { onEditExpense(expense) }, { onDeleteExpense(expense) }) }
        item { Spacer(Modifier.height(76.dp)) }
    }
}

@Composable private fun BudgetCard(account: Account, spent: Long, balance: Long, edit: () -> Unit, delete: () -> Unit) {
    val over = balance < 0; val near = !over && balance <= account.warningCents
    val color = when { over -> MaterialTheme.colorScheme.errorContainer; near -> Color(0xFFFFE7B0); else -> MaterialTheme.colorScheme.secondaryContainer }
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = edit) { Text("Editar") } }
            Text("Límite mensual: ${money(account.limitCents)}")
            Text("Gastado: ${money(spent)}")
            Spacer(Modifier.height(8.dp)); Text("Disponible: ${money(balance)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (over) Text("⛔ Has superado el límite. No deberías gastar más.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            else if (near) Text("⚠️ Te acercas al máximo establecido.", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = delete) { Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable private fun ExpenseRow(expense: Expense, edit: () -> Unit, delete: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(if (expense.description.isBlank()) "Gasto sin descripción" else expense.description, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(expense.date, style = MaterialTheme.typography.bodySmall) }
        Text(money(expense.amountCents), fontWeight = FontWeight.Bold); TextButton(onClick = edit) { Text("Editar") }; TextButton(onClick = delete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable private fun HistoryScreen(account: Account, summaries: List<MonthlySummary>, yearTotals: YearTotals, onShowAnnualHistory: () -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item {
        Text("Resumen de ${account.name} · ${YearMonth.now().year}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Total gastado"); Text(money(yearTotals.spentCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.End) { Text("Total ahorrado"); Text(money(yearTotals.savedCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF147D3D)) }
            }
        }
        TextButton(onClick = onShowAnnualHistory) { Text("Años anteriores") }
    }
    item { Spacer(Modifier.height(8.dp)); Text("Histórico · ${account.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Los movimientos antiguos se eliminan; aquí solo se guarda su resumen.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
    if (summaries.isEmpty()) item { Text("Aún no hay meses archivados.") }
    items(summaries, key = { it.id }) { summary -> SummaryRow(summary) }
}

@Composable private fun AnnualHistoryScreen(account: Account, yearTotals: YearTotals, annualSummaries: List<AnnualSummary>, onBack: () -> Unit) = LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Años · ${account.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(onClick = onBack) { Text("Volver") } } }
    item { Text("${YearMonth.now().year} (actual)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); AnnualSummaryRow(AnnualSummary(account.id, YearMonth.now().year.toString(), yearTotals.spentCents, yearTotals.savedCents)) }
    if (annualSummaries.isEmpty()) item { Text("Aún no hay años cerrados para esta cuenta.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    else {
        item { Text("Años cerrados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(annualSummaries.filter { it.year != YearMonth.now().year.toString() }, key = { it.year }) { summary -> AnnualSummaryRow(summary) }
    }
}

@Composable private fun SummaryRow(summary: MonthlySummary) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
    Text(monthLabel(summary.month), fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Límite ${money(summary.limitCents)} · Gastado ${money(summary.spentCents)}"); Text(if (summary.balanceCents >= 0) "+${money(summary.balanceCents)}" else money(summary.balanceCents), color = if (summary.balanceCents < 0) MaterialTheme.colorScheme.error else Color(0xFF147D3D), fontWeight = FontWeight.Bold) }
} }

@Composable private fun AnnualSummaryRow(summary: AnnualSummary) = Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(summary.year, fontWeight = FontWeight.Bold)
    Text("Gastado: ${money(summary.spentCents)}")
    Text("Ahorrado: ${money(summary.savedCents)}", color = Color(0xFF147D3D), fontWeight = FontWeight.Bold)
} }

@Composable private fun AccountDialog(account: Account?, onClose: () -> Unit, onSave: (String, Long, Long) -> Unit) {
    var name by remember(account) { mutableStateOf(account?.name ?: "") }; var limit by remember(account) { mutableStateOf(account?.let { (it.limitCents / 100.0).toString() } ?: "") }; var warning by remember(account) { mutableStateOf(account?.let { (it.warningCents / 100.0).toString() } ?: "") }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (account == null) "Nueva cuenta" else "Editar cuenta") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true); OutlinedTextField(limit, { limit = it }, label = { Text("Límite mensual (€)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(warning, { warning = it }, label = { Text("Avisar cuando queden (€)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button(onClick = { val l = parseEuros(limit); val w = parseEuros(warning); if (name.isBlank() || l == null || l <= 0 || w == null || w < 0) error = "Revisa el nombre y los importes." else if (w > l) error = "El aviso no puede ser mayor que el límite mensual." else onSave(name, l, w) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable private fun ExpenseDialog(accountId: Long, expense: Expense?, onClose: () -> Unit, onSave: (Expense) -> Unit) {
    var date by remember(expense) { mutableStateOf(expense?.date ?: LocalDate.now().toString()) }; var amount by remember(expense) { mutableStateOf(expense?.let { (it.amountCents / 100.0).toString() } ?: "") }; var description by remember(expense) { mutableStateOf(expense?.description ?: "") }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    fun showCalendar() {
        val current = LocalDate.now()
        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString() }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (expense == null) "Añadir gasto" else "Editar gasto") }, text = { Column { OutlinedTextField(date, { date = formatDateWithHyphens(it) }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), trailingIcon = { TextButton(onClick = { showCalendar() }) { Text("📅") } }); OutlinedTextField(amount, { amount = it }, label = { Text("Importe (€)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(description, { description = it }, label = { Text("En qué (opcional)") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { Button(onClick = { val cents = parseEuros(amount); val parsed = try { LocalDate.parse(date) } catch (_: DateTimeParseException) { null }; val currentMonth = YearMonth.now(); if (cents == null || cents <= 0 || parsed == null || YearMonth.from(parsed) != currentMonth) error = "La fecha debe ser de este mes y el importe mayor que cero." else onSave(Expense(expense?.id ?: 0, accountId, parsed.toString(), cents, description)) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

@Composable private fun ConfirmDialog(title: String, message: String, cancel: () -> Unit, confirm: () -> Unit) = AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { Text(message) }, confirmButton = { Button(onClick = confirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") } }, dismissButton = { TextButton(onClick = cancel) { Text("Cancelar") } })

private fun monthLabel(month: String): String = try { YearMonth.parse(month).atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))).replaceFirstChar { it.uppercase() } } catch (_: Exception) { month }
private fun formatDateWithHyphens(input: String): String {
    val digits = input.filter(Char::isDigit).take(8)
    return when {
        digits.length <= 4 -> digits
        digits.length <= 6 -> digits.take(4) + "-" + digits.drop(4)
        else -> digits.take(4) + "-" + digits.substring(4, 6) + "-" + digits.drop(6)
    }
}
