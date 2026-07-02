package com.dogfood.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogfood.ledger.data.CategoryEntity
import com.dogfood.ledger.data.LedgerDao
import com.dogfood.ledger.data.TransactionEntity
import com.dogfood.ledger.domain.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class CategorySummary(
    val id: String,
    val name: String,
    val budget: Money,
    val spent: Money,
) {
    val remaining: Money get() = budget - spent
    val ratio: Float get() = if (budget.minor == 0L) 0f else (spent.minor.toFloat() / budget.minor.toFloat())
}

data class MonthlySummary(
    val income: Money,
    val expense: Money,
    val balance: Money,
)

class LedgerViewModel(private val dao: LedgerDao) : ViewModel() {

    private val firstOfMonth: Long
        get() = LocalDate.now().withDayOfMonth(1).toEpochDay()

    val transactions: StateFlow<List<TransactionEntity>> =
        dao.transactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<CategorySummary>> =
        dao.categories()
            .flatMapLatest { cats ->
                if (cats.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(cats.map { summaryFlow(it) }) { it.toList() }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlySummary: StateFlow<MonthlySummary?> =
        combine(
            dao.incomeThisMonth(firstOfMonth),
            dao.expenseThisMonth(firstOfMonth),
        ) { incomeMinor, expenseMinor ->
            MonthlySummary(
                income = Money(incomeMinor),
                // expenseMinor is <= 0 because expenses are negative; negate to get positive amount
                expense = Money(-expenseMinor),
                balance = Money(incomeMinor + expenseMinor),
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun summaryFlow(category: CategoryEntity) =
        dao.spentSince(category.id, firstOfMonth).map { spentMinor ->
            CategorySummary(
                id = category.id,
                name = category.name,
                budget = Money(category.monthlyBudgetMinor),
                // spentMinor is <= 0 because expenses are negative; present as positive spend.
                spent = Money(-spentMinor),
            )
        }

    fun addExpense(categoryId: String, amount: Money, note: String) = viewModelScope.launch {
        dao.upsertTransaction(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                categoryId = categoryId,
                amountMinor = -amount.minor,
                note = note,
                epochDay = LocalDate.now().toEpochDay(),
            ),
        )
    }

    fun addCategory(name: String, budget: Money) = viewModelScope.launch {
        dao.upsertCategory(CategoryEntity(UUID.randomUUID().toString(), name, budget.minor))
    }
}

@Composable
fun LedgerScreen(vm: LedgerViewModel) {
    val categories by vm.categories.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Budget", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories, key = { it.id }) { summary ->
                OutlinedCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(summary.name, style = MaterialTheme.typography.titleMedium)
                            Text(summary.remaining.format() + " left", style = MaterialTheme.typography.bodyMedium)
                        }
                        LinearProgressIndicator(
                            progress = { summary.ratio.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            summary.spent.format() + " / " + summary.budget.format(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
