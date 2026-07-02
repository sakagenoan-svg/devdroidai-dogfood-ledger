package com.dogfood.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.dogfood.ledger.data.LedgerDatabase
import com.dogfood.ledger.ui.LedgerScreen
import com.dogfood.ledger.ui.LedgerViewModel
import com.dogfood.ledger.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(
            applicationContext,
            LedgerDatabase::class.java,
            "ledger.db",
        ).build()
        val dao = db.dao()
        setContent {
            AppTheme {
                val vm: LedgerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            LedgerViewModel(dao) as T
                    }
                )
                LedgerScreen(vm)
            }
        }
    }
}
