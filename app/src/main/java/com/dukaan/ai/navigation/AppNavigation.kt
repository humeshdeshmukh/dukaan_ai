package com.dukaan.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.dukaan.feature.billing.ui.VoiceBillingScreen
import com.dukaan.feature.billing.ui.BillingViewModel
import com.dukaan.feature.dashboard.DashboardScreen
import com.dukaan.feature.khata.ui.CustomerListScreen
import com.dukaan.feature.khata.ui.CustomerDetailScreen
import com.dukaan.feature.khata.ui.AddTransactionScreen
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
    object ScanBill : Screen("scan_bill")
    object OcrResult : Screen("ocr_result")
    object WholesaleOrder : Screen("wholesale_order")
}

@Composable
fun AppNavigation(navController: NavHostController) {
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
                onScanBillClick = { navController.navigate(Screen.ScanBill.route) },
                onVoiceBillingClick = { navController.navigate(Screen.VoiceBilling.route) },
                onSmartKhataClick = { navController.navigate(Screen.SmartKhata.route) },
                onOrdersClick = { navController.navigate(Screen.WholesaleOrder.route) },
                onInventoryClick = { /* Removed from UI */ }
            )
        }
        
        composable(Screen.SmartKhata.route) {
            val viewModel: com.dukaan.feature.khata.ui.KhataViewModel = hiltViewModel()
            CustomerListScreen(
                viewModel = viewModel,
                onCustomerClick = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.CustomerDetail.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
            val viewModel: com.dukaan.feature.khata.ui.KhataViewModel = hiltViewModel()
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
            val viewModel: com.dukaan.feature.khata.ui.KhataViewModel = hiltViewModel()
            
            AddTransactionScreen(
                customerId = customerId,
                type = type,
                viewModel = viewModel,
                onSuccess = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ScanBill.route) {
            val ocrViewModel: OcrViewModel = hiltViewModel()
            BillScannerScreen(
                viewModel = ocrViewModel,
                onBackClick = { navController.popBackStack() },
                onBillDetected = { navController.navigate(Screen.OcrResult.route) }
            )
        }

        composable(Screen.OcrResult.route) {
            val ocrViewModel: OcrViewModel = hiltViewModel()
            OcrResultScreen(
                state = ocrViewModel.uiState.collectAsState().value,
                onBackClick = { navController.popBackStack() },
                onSaveClick = { navController.navigate(Screen.Dashboard.route) },
                onDeleteItem = { item -> ocrViewModel.deleteItem(item) }
            )
        }

        composable(Screen.WholesaleOrder.route) {
            val orderViewModel: OrderViewModel = hiltViewModel()
            WholesaleOrderScreen(
                viewModel = orderViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { message ->
                    // Share logic handled at higher level or via context in screen
                }
            )
        }

        composable(Screen.VoiceBilling.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            VoiceBillingScreen(
                viewModel = billingViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { /* WhatsApp Logic */ }
            )
        }
    }
}
