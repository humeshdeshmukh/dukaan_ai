package com.dukaan.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.hilt.navigation.compose.hiltViewModel
import com.dukaan.ai.util.shareViaWhatsApp
import com.dukaan.feature.billing.ui.BillingViewModel
import com.dukaan.feature.billing.ui.VoiceBillingScreen
import com.dukaan.feature.billing.ui.BillHistoryScreen
import com.dukaan.feature.billing.ui.BillDetailScreen
import com.dukaan.feature.dashboard.DashboardScreen
import com.dukaan.feature.dashboard.SettingsScreen
import com.dukaan.feature.dashboard.SettingsViewModel
import com.dukaan.feature.inventory.ui.InventoryListScreen
import com.dukaan.feature.inventory.ui.InventoryViewModel
import com.dukaan.feature.khata.ui.CustomerListScreen
import com.dukaan.feature.khata.ui.CustomerDetailScreen
import com.dukaan.feature.khata.ui.AddTransactionScreen
import com.dukaan.feature.khata.ui.KhataViewModel
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.ocr.ui.BillScannerScreen
import com.dukaan.feature.ocr.ui.OcrResultScreen
import com.dukaan.feature.ocr.ui.OcrViewModel
import com.dukaan.feature.orders.ui.WholesaleOrderScreen
import com.dukaan.feature.orders.ui.OrderViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Dashboard : Screen("dashboard")
    object SmartKhata : Screen("smart_khata")
    object CustomerDetail : Screen("customer_detail/{customerId}") {
        fun createRoute(customerId: Long) = "customer_detail/$customerId"
    }
    object AddTransaction : Screen("add_transaction/{customerId}/{type}") {
        fun createRoute(customerId: Long, type: TransactionType) = "add_transaction/$customerId/${type.name}"
    }
    object VoiceBilling : Screen("voice_billing")
    object OcrFlow : Screen("ocr_flow")
    object ScanBill : Screen("scan_bill")
    object OcrResult : Screen("ocr_result")
    object WholesaleOrder : Screen("wholesale_order")
    object BillHistory : Screen("bill_history")
    object BillDetail : Screen("bill_detail/{billId}") {
        fun createRoute(billId: Long) = "bill_detail/$billId"
    }
    object Settings : Screen("settings")
    object Inventory : Screen("inventory")
}

@Composable
fun AppNavigation(navController: NavHostController) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            com.dukaan.feature.dashboard.SplashScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onScanBillClick = { navController.navigate(Screen.OcrFlow.route) },
                onVoiceBillingClick = { navController.navigate(Screen.VoiceBilling.route) },
                onSmartKhataClick = { navController.navigate(Screen.SmartKhata.route) },
                onOrdersClick = { navController.navigate(Screen.WholesaleOrder.route) },
                onInventoryClick = { navController.navigate(Screen.Inventory.route) },
                onProfileClick = { navController.navigate(Screen.Settings.route) },
                onBillHistoryClick = { navController.navigate(Screen.BillHistory.route) }
            )
        }

        // Smart Khata Flow
        composable(Screen.SmartKhata.route) {
            val viewModel: KhataViewModel = hiltViewModel()
            CustomerListScreen(
                viewModel = viewModel,
                onCustomerClick = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.CustomerDetail.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
            val viewModel: KhataViewModel = hiltViewModel()
            CustomerDetailScreen(
                customerId = customerId,
                viewModel = viewModel,
                onAddTransaction = { type ->
                    navController.navigate(Screen.AddTransaction.createRoute(customerId, type))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.AddTransaction.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
            val typeStr = backStackEntry.arguments?.getString("type") ?: return@composable
            val type = TransactionType.valueOf(typeStr)
            val viewModel: KhataViewModel = hiltViewModel()

            AddTransactionScreen(
                customerId = customerId,
                type = type,
                viewModel = viewModel,
                onSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        // OCR Flow - nested navigation so both screens share the same OcrViewModel
        navigation(
            startDestination = Screen.ScanBill.route,
            route = Screen.OcrFlow.route
        ) {
            composable(Screen.ScanBill.route) {
                val parentEntry = navController.getBackStackEntry(Screen.OcrFlow.route)
                val ocrViewModel: OcrViewModel = hiltViewModel(parentEntry)
                BillScannerScreen(
                    viewModel = ocrViewModel,
                    onBackClick = { navController.popBackStack() },
                    onBillDetected = { navController.navigate(Screen.OcrResult.route) }
                )
            }

            composable(Screen.OcrResult.route) {
                val parentEntry = navController.getBackStackEntry(Screen.OcrFlow.route)
                val ocrViewModel: OcrViewModel = hiltViewModel(parentEntry)
                OcrResultScreen(
                    state = ocrViewModel.uiState.collectAsState().value,
                    onBackClick = { navController.popBackStack() },
                    onSaveClick = {
                        ocrViewModel.saveBill()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    },
                    onDeleteItem = { item -> ocrViewModel.deleteItem(item) }
                )
            }
        }

        // Wholesale Order
        composable(Screen.WholesaleOrder.route) {
            val orderViewModel: OrderViewModel = hiltViewModel()
            WholesaleOrderScreen(
                viewModel = orderViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { message ->
                    shareViaWhatsApp(context, message)
                }
            )
        }

        // Voice Billing
        composable(Screen.VoiceBilling.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            VoiceBillingScreen(
                viewModel = billingViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { message ->
                    shareViaWhatsApp(context, message)
                }
            )
        }

        // Bill History
        composable(Screen.BillHistory.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            BillHistoryScreen(
                viewModel = billingViewModel,
                onBillClick = { billId ->
                    navController.navigate(Screen.BillDetail.createRoute(billId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // Bill Detail
        composable(Screen.BillDetail.route) { backStackEntry ->
            val billId = backStackEntry.arguments?.getString("billId")?.toLongOrNull() ?: return@composable
            val billingViewModel: BillingViewModel = hiltViewModel()
            BillDetailScreen(
                billId = billId,
                viewModel = billingViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { message ->
                    shareViaWhatsApp(context, message)
                }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                isDarkTheme = settingsState.isDarkTheme,
                onToggleDarkTheme = { settingsViewModel.toggleDarkTheme(it) },
                shopName = settingsState.shopName,
                ownerName = settingsState.ownerName,
                phone = settingsState.phone,
                address = settingsState.address,
                onSaveProfile = { sn, on, ph, addr ->
                    settingsViewModel.saveProfile(sn, on, ph, addr)
                }
            )
        }

        // Inventory
        composable(Screen.Inventory.route) {
            val inventoryViewModel: InventoryViewModel = hiltViewModel()
            InventoryListScreen(
                viewModel = inventoryViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
