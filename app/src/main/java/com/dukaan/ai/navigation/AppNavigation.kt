package com.dukaan.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.hilt.navigation.compose.hiltViewModel
import com.dukaan.ai.util.shareViaWhatsApp
import com.dukaan.ai.util.sharePdfFile
import com.dukaan.ai.util.PdfGenerator
import com.dukaan.ai.util.PdfPreviewDialog
import com.dukaan.ai.util.toShopInfo
import com.dukaan.ai.translation.TranslationManager
import com.dukaan.core.db.SupportedLanguages
import com.dukaan.feature.billing.ui.BillingViewModel
import com.dukaan.feature.billing.ui.VoiceBillingScreen
import com.dukaan.feature.billing.ui.BillHistoryScreen
import com.dukaan.feature.billing.ui.BillDetailScreen
import com.dukaan.feature.dashboard.DashboardScreen
import com.dukaan.feature.dashboard.DashboardViewModel
import com.dukaan.feature.dashboard.SettingsScreen
import com.dukaan.feature.dashboard.SettingsViewModel
import com.dukaan.feature.khata.ui.CustomerListScreen
import com.dukaan.feature.khata.ui.CustomerDetailScreen
import com.dukaan.feature.khata.ui.AddTransactionScreen
import com.dukaan.feature.khata.ui.CustomerStatementScreen
import com.dukaan.feature.khata.ui.KhataOverviewScreen
import com.dukaan.feature.khata.ui.KhataViewModel
import com.dukaan.feature.khata.domain.model.TransactionType
import com.dukaan.feature.ocr.ui.BillScannerScreen
import com.dukaan.feature.ocr.ui.OcrResultScreen
import com.dukaan.feature.ocr.ui.OcrViewModel
import com.dukaan.feature.ocr.ui.ScannedBillHistoryScreen
import com.dukaan.feature.ocr.ui.ScannedBillHistoryViewModel
import com.dukaan.feature.ocr.ui.WholesalerBillsScreen
import com.dukaan.feature.orders.ui.WholesaleOrderScreen
import com.dukaan.feature.orders.ui.OrderViewModel
import kotlinx.coroutines.launch

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
    object KhataFlow : Screen("khata_flow")
    object ScanBill : Screen("scan_bill")
    object OcrResult : Screen("ocr_result")
    object WholesaleOrder : Screen("wholesale_order")
    object BillHistory : Screen("bill_history")
    object BillDetail : Screen("bill_detail/{billId}") {
        fun createRoute(billId: Long) = "bill_detail/$billId"
    }
    object Settings : Screen("settings")
    object CustomerStatement : Screen("customer_statement/{customerId}") {
        fun createRoute(customerId: Long) = "customer_statement/$customerId"
    }
    object KhataOverview : Screen("khata_overview")
    object ScannedBillHistory : Screen("scanned_bill_history")
    object WholesalerBills : Screen("wholesaler_bills/{sellerName}") {
        fun createRoute(sellerName: String) = "wholesaler_bills/${java.net.URLEncoder.encode(sellerName, "UTF-8")}"
    }
}

@Composable
fun AppNavigation(navController: NavHostController, translationManager: TranslationManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isTranslating by translationManager.isTranslating.collectAsState()
    var pdfPreviewFile by remember { mutableStateOf<java.io.File?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
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

        // Home tab
        composable(Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onScanBillClick = { navController.navigate(Screen.OcrFlow.route) },
                onVoiceBillingClick = { navController.navigate(Screen.VoiceBilling.route) },
                onSmartKhataClick = {
                    navController.navigate(Screen.KhataFlow.route) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOrdersClick = {
                    navController.navigate(Screen.WholesaleOrder.route) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBillHistoryClick = {
                    navController.navigate(Screen.BillHistory.route) {
                        popUpTo(Screen.Dashboard.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onPurchaseBillsClick = { navController.navigate(Screen.ScannedBillHistory.route) },
                onBillClick = { billId -> navController.navigate(Screen.BillDetail.createRoute(billId)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        // Khata tab - nested navigation so all screens share KhataViewModel
        navigation(
            startDestination = Screen.SmartKhata.route,
            route = Screen.KhataFlow.route
        ) {
            composable(Screen.SmartKhata.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)
                CustomerListScreen(
                    viewModel = viewModel,
                    onCustomerClick = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) },
                    onOverviewClick = { navController.navigate(Screen.KhataOverview.route) },
                    onBackClick = null
                )
            }

            composable(Screen.CustomerDetail.route) { backStackEntry ->
                val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                CustomerDetailScreen(
                    customerId = customerId,
                    viewModel = viewModel,
                    shopName = settingsState.shopName,
                    languageCode = settingsState.languageCode,
                    onAddTransaction = { type ->
                        navController.navigate(Screen.AddTransaction.createRoute(customerId, type))
                    },
                    onStatementClick = {
                        navController.navigate(Screen.CustomerStatement.createRoute(customerId))
                    },
                    onShareReminder = { message ->
                        shareViaWhatsApp(context, message)
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.AddTransaction.route) { backStackEntry ->
                val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
                val typeStr = backStackEntry.arguments?.getString("type") ?: return@composable
                val type = TransactionType.valueOf(typeStr)
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)

                AddTransactionScreen(
                    customerId = customerId,
                    type = type,
                    viewModel = viewModel,
                    onSuccess = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.CustomerStatement.route) { backStackEntry ->
                val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                CustomerStatementScreen(
                    customerId = customerId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onShareClick = { data ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateStatementPdf(context, shopInfo, data)
                        pdfPreviewFile = file
                    }
                )
            }

            composable(Screen.KhataOverview.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)
                KhataOverviewScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onCustomerClick = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) }
                )
            }
        }

        // OCR Flow (Scan tab) - nested navigation so both screens share the same OcrViewModel
        navigation(
            startDestination = Screen.ScanBill.route,
            route = Screen.OcrFlow.route
        ) {
            composable(Screen.ScanBill.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.OcrFlow.route) }
                val ocrViewModel: OcrViewModel = hiltViewModel(parentEntry)
                BillScannerScreen(
                    viewModel = ocrViewModel,
                    onBackClick = {
                        ocrViewModel.resetScan()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBillDetected = { navController.navigate(Screen.OcrResult.route) }
                )
            }

            composable(Screen.OcrResult.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.OcrFlow.route) }
                val ocrViewModel: OcrViewModel = hiltViewModel(parentEntry)
                val existingSellerNames by ocrViewModel.existingSellerNames.collectAsState()
                OcrResultScreen(
                    state = ocrViewModel.uiState.collectAsState().value,
                    existingSellerNames = existingSellerNames,
                    onBackClick = {
                        ocrViewModel.resetScan()
                        navController.popBackStack()
                    },
                    onSaveClick = {
                        ocrViewModel.saveBill()
                    },
                    onNavigateAfterSave = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    },
                    onDeleteItem = { item -> ocrViewModel.deleteItem(item) },
                    onEditItem = { index, item -> ocrViewModel.editItem(index, item) },
                    onAddItem = { item -> ocrViewModel.addItem(item) },
                    onSellerNameChanged = { name -> ocrViewModel.updateSellerName(name) },
                    onSendChatMessage = { message -> ocrViewModel.sendChatMessage(message) }
                )
            }
        }

        // Orders tab
        composable(Screen.WholesaleOrder.route) {
            val orderViewModel: OrderViewModel = hiltViewModel()
            WholesaleOrderScreen(
                viewModel = orderViewModel,
                onBackClick = null,
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

        // Bills tab
        composable(Screen.BillHistory.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            BillHistoryScreen(
                viewModel = billingViewModel,
                onBillClick = { billId ->
                    navController.navigate(Screen.BillDetail.createRoute(billId))
                },
                onBackClick = null
            )
        }

        // Bill Detail
        composable(Screen.BillDetail.route) { backStackEntry ->
            val billId = backStackEntry.arguments?.getString("billId")?.toLongOrNull() ?: return@composable
            val billingViewModel: BillingViewModel = hiltViewModel()
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsState()
            BillDetailScreen(
                billId = billId,
                viewModel = billingViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfPreviewFile = file
                }
            )
        }

        // Settings (sub-screen, accessed from dashboard header)
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
                gstNumber = settingsState.gstNumber,
                email = settingsState.email,
                upiId = settingsState.upiId,
                tagline = settingsState.tagline,
                bankName = settingsState.bankName,
                bankAccountNumber = settingsState.bankAccountNumber,
                bankIfscCode = settingsState.bankIfscCode,
                onSaveProfile = { sn, on, ph, addr, gst, em, upi, tag, bn, ban, bic ->
                    settingsViewModel.saveProfile(sn, on, ph, addr, gst, em, upi, tag, bn, ban, bic)
                },
                languageCode = settingsState.languageCode,
                onLanguageChange = { settingsViewModel.updateLanguage(it) },
                onApplyLanguage = { code ->
                    coroutineScope.launch {
                        settingsViewModel.updateLanguage(code)
                        val langName = SupportedLanguages.getByCode(code).englishName
                        translationManager.translateAndApply(code, langName)
                    }
                },
                isTranslating = isTranslating,
                onPreviewPdf = {
                    val shopInfo = settingsState.toShopInfo()
                    val sampleBill = com.dukaan.core.network.model.Bill(
                        id = 0,
                        items = listOf(
                            com.dukaan.core.network.model.BillItem("Sample Item 1", 2.0, "kg", 50.0),
                            com.dukaan.core.network.model.BillItem("Sample Item 2", 1.0, "pc", 120.0),
                            com.dukaan.core.network.model.BillItem("Sample Item 3", 3.0, "pkt", 30.0)
                        ),
                        totalAmount = 310.0,
                        sellerName = "",
                        billNumber = "SAMPLE-001",
                        timestamp = System.currentTimeMillis()
                    )
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, sampleBill)
                    pdfPreviewFile = file
                }
            )
        }

        // Scanned Bill History (Purchase Bills by Wholesaler)
        composable(Screen.ScannedBillHistory.route) {
            val historyViewModel: ScannedBillHistoryViewModel = hiltViewModel()
            ScannedBillHistoryScreen(
                viewModel = historyViewModel,
                onWholesalerClick = { sellerName ->
                    navController.navigate(Screen.WholesalerBills.createRoute(sellerName))
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        // Wholesaler Bills
        composable(Screen.WholesalerBills.route) { backStackEntry ->
            val sellerName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("sellerName") ?: "",
                "UTF-8"
            )
            val historyViewModel: ScannedBillHistoryViewModel = hiltViewModel()
            WholesalerBillsScreen(
                sellerName = sellerName,
                viewModel = historyViewModel,
                onBillClick = { billId ->
                    navController.navigate(Screen.BillDetail.createRoute(billId))
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }

    // PDF Preview Dialog overlay
    pdfPreviewFile?.let { file ->
        PdfPreviewDialog(
            pdfFile = file,
            onShare = {
                sharePdfFile(context, file, "Share PDF")
                pdfPreviewFile = null
            },
            onDismiss = { pdfPreviewFile = null }
        )
    }
}
