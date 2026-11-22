package id.umkmku.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import id.umkmku.pos.feature.CatalogViewModel
import id.umkmku.pos.feature.CashViewModel
import id.umkmku.pos.feature.CashbookViewModel
import id.umkmku.pos.feature.SettlementViewModel
import id.umkmku.pos.ui.WarungApp
import id.umkmku.pos.ui.WarungTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val catalog: CatalogViewModel by viewModels()
    private val cash: CashViewModel by viewModels()
    private val finance: CashbookViewModel by viewModels()
    private val settlements: SettlementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { WarungTheme { WarungApp(catalog, cash, finance, settlements) } }
    }
}
