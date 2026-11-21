package id.umkmku.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import id.umkmku.pos.feature.CashRead
import id.umkmku.pos.feature.CashViewModel
import id.umkmku.pos.feature.CashbookViewModel
import id.umkmku.pos.domain.LedgerAccount
import java.util.UUID
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.umkmku.pos.feature.CatalogState
import id.umkmku.pos.feature.CatalogViewModel

private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Beranda", Icons.Default.Home),
    CASHIER("cashier", "Kasir", Icons.Default.ShoppingCart),
    CATALOG("catalog", "Katalog", Icons.AutoMirrored.Filled.List),
    HISTORY("history", "Riwayat", Icons.Default.DateRange),
    FINANCE("finance", "Keuangan", Icons.Default.AccountBox),
}

private const val CART = "cart"
private const val CASH = "cash/{id}"
private const val QRIS = "qris/{id}"
private const val QRIS_CONFIRM = "qris-confirm/{id}"
private const val QRIS_PROFILE = "qris-profile"
private const val RECEIPT = "receipt/{id}"
private const val FINANCE_ENTRY = "finance-entry/{id}?opening={opening}"
private const val FINANCE_HISTORY = "finance-history"
private const val FINANCE_DETAIL = "finance-detail/{id}"
private const val EDIT = "edit/{type}?id={id}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarungApp(model: CatalogViewModel, cash: CashViewModel, finance: CashbookViewModel) {
    val state by model.catalog.collectAsStateWithLifecycle()
    val draftState by cash.draft.collectAsStateWithLifecycle()
    val command by cash.command.collectAsStateWithLifecycle()
    val draft = (draftState as? CashRead.Ready)?.value
    val quantities = draft?.lines.orEmpty().associate { it.product.id to it.quantity }
    val save by model.save.collectAsStateWithLifecycle()
    val financeSave by finance.save.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.HOME.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    val catalog = (state as? CatalogState.Ready)?.catalog

    fun openTab(destination: Tab) {
        nav.navigate(destination.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun returnToTab(destination: Tab) {
        nav.clearBackStack(destination.route)
        nav.navigate(destination.route) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    Scaffold(
        topBar = {
            val title = when (route) {
                Tab.HOME.route -> null
                CART -> "Keranjang"
                CASH -> "Pembayaran tunai"
                QRIS -> "Pembayaran QRIS"
                QRIS_CONFIRM -> "Bukti pembayaran QRIS"
                QRIS_PROFILE -> "Profil QRIS"
                RECEIPT -> "Struk penjualan"
                FINANCE_ENTRY -> "Catat mutasi"
                FINANCE_HISTORY -> "Riwayat mutasi"
                FINANCE_DETAIL -> "Rincian mutasi"
                EDIT -> if (entry?.arguments?.getString("type") == "product") "Produk" else "Kategori"
                else -> tab?.title
            }
            if (title != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (tab == null) IconButton(onClick = { nav.popBackStack() }, enabled = !save.busy && !command.busy && !financeSave.busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    actions = {
                        if (tab == Tab.CATALOG) TextButton({
                            cash.clearCommand()
                            cash.cancelQrisImport()
                            nav.navigate(QRIS_PROFILE)
                        }) { Text("Profil QRIS") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (tab != null) NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination, onClick = { openTab(destination) },
                        icon = { Icon(destination.icon, null) }, label = { Text(destination.title) },
                    )
                }
            }
        },
    ) { inset ->
        NavHost(
            navController = nav, startDestination = Tab.HOME.route,
            modifier = Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding(),
        ) {
            composable(Tab.HOME.route) {
                Dashboard(state, { openTab(Tab.CASHIER) }, { openTab(Tab.CATALOG) }, model::reload)
            }
            composable(Tab.CASHIER.route) {
                Column(Modifier.fillMaxSize()) {
                    if (draftState !is CashRead.Ready) CashReadStatus(draftState, cash::retryRead)
                    CashCommandError(command)
                    Box(Modifier.weight(1f)) {
                        Cashier(state, quantities, { id, delta -> cash.changeQuantity(id, delta) },
                            { cash.clearCommand(); nav.navigate(CART) },
                            { openTab(Tab.CATALOG) }, model::reload,
                            draftLines = draft?.lines.orEmpty(),
                            quantityEnabled = !command.busy && draftState is CashRead.Ready,
                        )
                    }
                }
            }
            composable(Tab.CATALOG.route) {
                CatalogScreen(state, model::reload) { product, id ->
                    model.clearError()
                    val type = if (product) "product" else "category"
                    nav.navigate(if (id == null) "edit/$type" else "edit/$type?id=$id")
                }
            }
            composable(CART) {
                BackHandler(enabled = command.busy) {}
                if (draftState !is CashRead.Ready) CashReadStatus(draftState, cash::retryRead)
                else Column(Modifier.fillMaxSize()) {
                    CashCommandError(command)
                    Box(Modifier.weight(1f)) {
                        CartScreen(draft?.lines.orEmpty(), !command.busy) { id, delta ->
                            cash.changeQuantity(id, delta, draft?.id)
                        }
                    }
                    if (draft != null && draft.lines.isNotEmpty()) {
                        Button(
                            onClick = { cash.clearCommand(); nav.navigate("cash/${draft.id}") },
                            enabled = !command.busy,
                            modifier = Modifier.fillMaxWidth().padding(Space.page).heightIn(min = Space.touch),
                        ) { Text("Bayar tunai") }
                        OutlinedButton(
                            onClick = { cash.clearCommand(); nav.navigate("qris/${draft.id}") },
                            enabled = !command.busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page)
                                .padding(bottom = Space.page).heightIn(min = Space.touch),
                        ) { Text("Bayar QRIS") }
                    }
                }
            }
            composable(Tab.HISTORY.route) {
                val history by remember(cash) { cash.history() }.collectAsStateWithLifecycle(CashRead.Loading)
                HistoryScreen(history, cash::retryRead) { sale ->
                    cash.clearCommand()
                    val destination = if (sale.pending != null) "qris" else "receipt"
                    nav.navigate("$destination/${sale.draft.id}")
                }
            }
            composable(Tab.FINANCE.route) {
                val book by finance.book.collectAsStateWithLifecycle()
                CashbookScreen(book, finance.today, finance.zone, finance::retryRead, finance::selectDate,
                    onNew = { opening ->
                        finance.clearError()
                        val id = UUID.randomUUID().toString()
                        nav.navigate(if (opening == null) "finance-entry/$id" else "finance-entry/$id?opening=${opening.name}")
                    },
                    onHistory = { nav.navigate(FINANCE_HISTORY) },
                )
            }
            composable(
                FINANCE_ENTRY,
                arguments = listOf(navArgument("opening") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { formEntry ->
                BackHandler(enabled = financeSave.busy) {}
                val id = checkNotNull(formEntry.arguments?.getString("id"))
                val opening = formEntry.arguments?.getString("opening")?.let(LedgerAccount::valueOf)
                val book by finance.book.collectAsStateWithLifecycle()
                val existing = (book as? CashRead.Ready)?.value?.entries?.find { it.id == "movement:$id" }
                when {
                    book !is CashRead.Ready -> CashbookStatus(book, finance::retryRead)
                    existing != null -> MovementDetails(existing, finance.zone) { nav.navigate("receipt/$it") }
                    else -> CashbookForm(opening, financeSave) { form ->
                        finance.post(id, form) { posted ->
                            nav.navigate("finance-detail/$posted") { popUpTo(FINANCE_ENTRY) { inclusive = true } }
                        }
                    }
                }
            }
            composable(FINANCE_HISTORY) {
                val book by finance.book.collectAsStateWithLifecycle()
                val snapshot = (book as? CashRead.Ready)?.value
                if (snapshot == null) CashbookStatus(book, finance::retryRead)
                else MovementHistory(snapshot.entries, finance.zone) { nav.navigate("finance-detail/$it") }
            }
            composable(FINANCE_DETAIL) { detail ->
                val book by finance.book.collectAsStateWithLifecycle()
                val id = detail.arguments?.getString("id")
                val ledger = (book as? CashRead.Ready)?.value?.entries?.find { it.id == id }
                when {
                    book !is CashRead.Ready -> CashbookStatus(book, finance::retryRead)
                    ledger == null -> MessagePanel("Mutasi tidak ditemukan", "Periksa kembali riwayat mutasi.")
                    else -> MovementDetails(ledger, finance.zone) { nav.navigate("receipt/$it") }
                }
            }
            composable(QRIS_PROFILE) {
                BackHandler(enabled = command.busy) {}
                val active by cash.activeProfile.collectAsStateWithLifecycle()
                val preview by cash.profileImport.collectAsStateWithLifecycle()
                QrisProfileScreen(
                    active, preview, command, cash::retryRead, cash::previewQrisImage, cash::previewQrisPayload,
                    cash::activateQris, { cash.cancelQrisImport(); cash.clearCommand() }, cash::deactivateQris,
                )
            }
            listOf(CASH, RECEIPT, QRIS, QRIS_CONFIRM).forEach { saleRoute ->
                composable(saleRoute) { saleEntry ->
                    BackHandler(enabled = command.busy) {}
                    val id = checkNotNull(saleEntry.arguments?.getString("id"))
                    val saleRead by remember(cash, id) { cash.sale(id) }.collectAsStateWithLifecycle(CashRead.Loading)
                    val sale = (saleRead as? CashRead.Ready)?.value
                    val receipt = sale?.receipt
                    when {
                        saleRead !is CashRead.Ready -> CashReadStatus(saleRead, cash::retryRead)
                        receipt != null -> ReceiptScreen(receipt) { cash.clearCommand(); returnToTab(Tab.HISTORY) }
                        sale?.pending != null -> {
                            val pending = sale.pending
                            val profileRead by remember(cash, pending.profileId) {
                                cash.profile(pending.profileId)
                            }.collectAsStateWithLifecycle(CashRead.Loading)
                            val profile = (profileRead as? CashRead.Ready)?.value
                            when {
                                profileRead !is CashRead.Ready -> CashReadStatus(profileRead, cash::retryRead)
                                profile == null -> MessagePanel("Profil QRIS tidak ditemukan", "Pembayaran masih menunggu verifikasi kasir.")
                                saleRoute == QRIS_CONFIRM -> QrisConfirmationScreen(sale.draft, profile, command) {
                                    cash.confirmQris(id, it)
                                }
                                else -> QrisPendingScreen(sale.draft, profile, {
                                    cash.clearCommand(); nav.navigate("qris-confirm/$id")
                                }, { cash.clearCommand(); returnToTab(Tab.CASHIER) })
                            }
                        }
                        sale == null || saleRoute == RECEIPT || saleRoute == QRIS_CONFIRM ->
                            MessagePanel("Penjualan tidak ditemukan", "Periksa kembali riwayat penjualan.")
                        else -> {
                            LaunchedEffect(sale.draft) { cash.reviewPrices(sale.draft) }
                            if (saleRoute == QRIS) {
                                val active by cash.activeProfile.collectAsStateWithLifecycle()
                                QrisStartScreen(
                                    sale.draft, active, command, cash::retryRead,
                                    { cash.beginQris(sale.draft, it) },
                                    { cash.clearCommand(); cash.cancelQrisImport(); nav.navigate(QRIS_PROFILE) },
                                    cash::acceptPrices, cash::clearCommand,
                                )
                            } else {
                                CashCheckoutScreen(
                                    sale.draft, command, { cash.pay(sale.draft, it) },
                                    cash::acceptPrices, cash::clearCommand,
                                )
                            }
                        }
                    }
                }
            }
            composable(
                EDIT,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { editor ->
                BackHandler(enabled = save.busy) {}
                val product = editor.arguments?.getString("type") == "product"
                val id = editor.arguments?.getString("id")
                if (catalog == null) CatalogStatus(state, model::reload) else {
                    val selectedProduct = catalog.products.find { it.id == id }
                    val selectedCategory = catalog.categories.find { it.id == id }
                    if (id != null && (if (product) selectedProduct == null else selectedCategory == null)) {
                        MessagePanel("Data sudah diarsipkan", "Kembali ke katalog untuk melihat data terbaru.")
                    } else {
                        val done: () -> Unit = { nav.popBackStack() }
                        CatalogEditor(
                            product, selectedProduct, selectedCategory, catalog.categories, save,
                            onSave = { name, price, category ->
                                if (product) model.saveProduct(id, name, price, category, done)
                                else model.saveCategory(id, name, done)
                            },
                            onArchive = {
                                if (id != null) {
                                    if (product) model.archiveProduct(id, done)
                                    else model.archiveCategory(id, done)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
