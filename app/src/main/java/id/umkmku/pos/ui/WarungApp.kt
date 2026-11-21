package id.umkmku.pos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
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
import id.umkmku.pos.domain.cartLines
import id.umkmku.pos.feature.CatalogState
import id.umkmku.pos.feature.CatalogViewModel

private enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    HOME("home", "Beranda", Icons.Default.Home),
    CASHIER("cashier", "Kasir", Icons.Default.ShoppingCart),
    CATALOG("catalog", "Katalog", Icons.AutoMirrored.Filled.List),
}

private const val CART = "cart"
private const val EDIT = "edit/{type}?id={id}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarungApp(model: CatalogViewModel) {
    val state by model.catalog.collectAsStateWithLifecycle()
    val quantities by model.cart.collectAsStateWithLifecycle()
    val save by model.save.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = {
            val title = when (route) {
                Tab.HOME.route -> null
                CART -> "Keranjang"
                EDIT -> if (entry?.arguments?.getString("type") == "product") "Produk" else "Kategori"
                else -> tab?.title
            }
            if (title != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (tab == null) IconButton(onClick = { nav.popBackStack() }, enabled = !save.busy) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
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
                Cashier(state, quantities, { model.changeQuantity(it, 1) }, { nav.navigate(CART) },
                    { openTab(Tab.CATALOG) }, model::reload)
            }
            composable(Tab.CATALOG.route) {
                CatalogScreen(state, model::reload) { product, id ->
                    model.clearError()
                    val type = if (product) "product" else "category"
                    nav.navigate(if (id == null) "edit/$type" else "edit/$type?id=$id")
                }
            }
            composable(CART) {
                if (catalog == null) CatalogStatus(state, model::reload)
                else CartScreen(cartLines(catalog, quantities), model::changeQuantity)
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
