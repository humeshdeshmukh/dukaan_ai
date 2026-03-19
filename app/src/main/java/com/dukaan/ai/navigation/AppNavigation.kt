package com.dukaan.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dukaan.ai.ads.AdConfig
import com.dukaan.ai.ads.AdManager
import com.dukaan.ai.ads.composables.AdScaffold
import com.dukaan.ai.ads.composables.AppResumeInterstitialEffect
import com.dukaan.ai.ads.composables.InterstitialTrigger
import com.dukaan.ai.ads.composables.ShowInterstitialOnEnter
import com.dukaan.ai.util.shareViaWhatsApp
import com.dukaan.ai.util.shareViaWhatsAppToPhone
import com.dukaan.ai.util.sharePdfFile
import com.dukaan.ai.util.sharePdfViaWhatsAppToPhone
import com.dukaan.ai.util.sharePdfViaWhatsApp
import com.dukaan.ai.util.PdfGenerator
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
import com.dukaan.feature.orders.ui.OrderDetailScreen
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
    object EditBill : Screen("voice_billing/edit/{billId}") {
        fun createRoute(billId: Long) = "voice_billing/edit/$billId"
    }
    object OcrFlow : Screen("ocr_flow")
    object KhataFlow : Screen("khata_flow")
    object ScanBill : Screen("scan_bill")
    object OcrResult : Screen("ocr_result")
    object WholesaleOrder : Screen("wholesale_order")
    object OrdersFlow : Screen("orders_flow")
    object OrderDetail : Screen("order_detail/{orderId}") {
        fun createRoute(orderId: Long) = "order_detail/$orderId"
    }
    object EditOrder : Screen("edit_order/{orderId}") {
        fun createRoute(orderId: Long) = "edit_order/$orderId"
    }
    object BillHistory : Screen("bill_history")
    object BillDetail : Screen("bill_detail/{billId}") {
        fun createRoute(billId: Long) = "bill_detail/$billId"
    }
    object Settings : Screen("settings")
    object CustomerStatement : Screen("customer_statement/{customerId}") {
        fun createRoute(customerId: Long) = "customer_statement/$customerId"
    }
    object KhataOverview : Screen("khata_overview") // unused, kept for route compat
    object ScannedBillHistory : Screen("scanned_bill_history")
    object WholesalerBills : Screen("wholesaler_bills/{sellerName}") {
        fun createRoute(sellerName: String) = "wholesaler_bills/${java.net.URLEncoder.encode(sellerName, "UTF-8")}"
    }
    object EditPurchaseBill : Screen("edit_purchase_bill/{billId}") {
        fun createRoute(billId: Long) = "edit_purchase_bill/$billId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    translationManager: TranslationManager,
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val coroutineScope = rememberCoroutineScope()
    val isTranslating by translationManager.isTranslating.collectAsState()

    // Handle interstitial ads on app resume
    AppResumeInterstitialEffect(adManager = adManager)

    // Handle interstitial ads on app resume
    AppResumeInterstitialEffect(adManager = adManager)

    var pdfToShow by remember { mutableStateOf<java.io.File?>(null) }
    var onSharePdfAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (pdfToShow != null) {
        com.dukaan.ai.util.PdfPreviewDialog(
            pdfFile = pdfToShow!!,
            onShare = { 
                onSharePdfAction?.invoke()
                pdfToShow = null 
            },
            onDismiss = { pdfToShow = null }
        )
    }

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

        // Home tab - with banner ad
        composable(Screen.Dashboard.route) {
            val dashboardViewModel: DashboardViewModel = hiltViewModel()
            AdScaffold(adUnitId = AdConfig.BANNER_DASHBOARD) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onScanBillClick = { navController.navigate(Screen.OcrFlow.route) },
                    onVoiceBillingClick = {
                        navController.navigate(Screen.VoiceBilling.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSmartKhataClick = {
                        navController.navigate(Screen.KhataFlow.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOrdersClick = {
                        navController.navigate(Screen.OrdersFlow.route) {
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
                    onPurchaseBillsClick = {
                        navController.navigate(Screen.ScannedBillHistory.route) {
                            launchSingleTop = true
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // Khata tab - nested navigation so all screens share KhataViewModel
        navigation(
            startDestination = Screen.SmartKhata.route,
            route = Screen.KhataFlow.route
        ) {
            composable(Screen.SmartKhata.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.KhataFlow.route) }
                val viewModel: KhataViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                AdScaffold(adUnitId = AdConfig.BANNER_KHATA_LIST) {
                    CustomerListScreen(
                        viewModel = viewModel,
                        onCustomerClick = { id -> navController.navigate(Screen.CustomerDetail.createRoute(id)) },
                        onBackClick = null,
                        languageCode = settingsState.languageCode
                    )
                }
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
                    onShareReminder = { message, phone ->
                        if (phone.isNotBlank()) {
                            shareViaWhatsAppToPhone(context, message, phone)
                        } else {
                            shareViaWhatsApp(context, message)
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.AddTransaction.route) { backStackEntry ->
                val customerId = backStackEntry.arguments?.getString("customerId")?.toLongOrNull() ?: return@composable
                val typeStr = backStackEntry.arguments?.getString("type") ?: return@composable
                val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.JAMA }
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
                        pdfToShow = file
                        onSharePdfAction = { sharePdfFile(context, file, "Share Statement") }
                    }
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

                // HIGH VALUE: Show interstitial when entering scan - user is already waiting for camera
                ShowInterstitialOnEnter(adManager = adManager, trigger = InterstitialTrigger.ENTER_SCAN)

                BillScannerScreen(
                    viewModel = ocrViewModel,
                    onBackClick = {
                        ocrViewModel.resetScan()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBillDetected = {
                        navController.navigate(Screen.OcrResult.route) {
                            launchSingleTop = true
                        }
                    },
                    onScannerCancelled = {
                        ocrViewModel.resetScan()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.OcrResult.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.OcrFlow.route) }
                val ocrViewModel: OcrViewModel = hiltViewModel(parentEntry)
                val existingSellerNames by ocrViewModel.existingSellerNames.collectAsState()
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                val activity = context as? android.app.Activity


                // Track when bill is saved to show interstitial
                var billSavedTrigger by remember { mutableStateOf<Long?>(null) }

                // HIGH VALUE: Show interstitial after scan bill is saved
                if (billSavedTrigger != null) {
                    LaunchedEffect(billSavedTrigger) {
                        activity?.let { adManager.showInterstitialAfterScanBillSave(it) }
                    }
                }

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
                        billSavedTrigger = System.currentTimeMillis()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    },
                    onDeleteItem = { item -> ocrViewModel.deleteItem(item) },
                    onEditItem = { index, item -> ocrViewModel.editItem(index, item) },
                    onAddItem = { item -> ocrViewModel.addItem(item) },
                    onSellerNameChanged = { name -> ocrViewModel.updateSellerName(name) },
                    onSellerPhoneChanged = { phone -> ocrViewModel.updateSellerPhone(phone) },
                    onSendChatMessage = { message -> ocrViewModel.sendChatMessage(message, settingsState.languageCode) },

                    onUseCalculatedSubtotal = { ocrViewModel.useCalculatedSubtotal() },
                    onDismissSubtotalMismatch = { ocrViewModel.dismissSubtotalMismatch() },
                    onDiscountPercentChanged = { percent -> ocrViewModel.updateDiscountPercent(percent) },
                    onTaxPercentChanged = { percent -> ocrViewModel.updateTaxPercent(percent) }
                )
            }
        }

        // Orders tab - nested navigation so all screens share OrderViewModel
        navigation(
            startDestination = Screen.WholesaleOrder.route,
            route = Screen.OrdersFlow.route
        ) {
            composable(Screen.WholesaleOrder.route) { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.OrdersFlow.route) }
                val orderViewModel: OrderViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                val activity = context as? android.app.Activity

                // Track order saves for interstitial
                var orderSavedTrigger by remember { mutableStateOf<Long?>(null) }

                // HIGH VALUE: Show interstitial after saving order (orders are rare)
                if (orderSavedTrigger != null) {
                    LaunchedEffect(orderSavedTrigger) {
                        activity?.let { adManager.showInterstitialAfterOrderSave(it) }
                    }
                }

                WholesaleOrderScreen(
                    viewModel = orderViewModel,
                    onBackClick = null,
                    onShareText = { message ->
                        shareViaWhatsApp(context, message)
                    },
                    onSharePdf = { order ->
                        orderSavedTrigger = System.currentTimeMillis()
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        pdfToShow = file
                        onSharePdfAction = { sharePdfFile(context, file, "Share Order") }
                    },
                    onSendPdfToPhone = { order ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        val phone = order.supplierPhone ?: ""
                        pdfToShow = file
                        onSharePdfAction = {
                            if (phone.isNotBlank()) sharePdfViaWhatsAppToPhone(context, file, phone)
                            else sharePdfFile(context, file, "Share Order PDF")
                        }
                    },
                    onOrderClick = { orderId ->
                        navController.navigate(Screen.OrderDetail.createRoute(orderId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.OrderDetail.route) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull() ?: return@composable
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.OrdersFlow.route) }
                val orderViewModel: OrderViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()
                OrderDetailScreen(
                    orderId = orderId,
                    viewModel = orderViewModel,
                    onBackClick = { navController.popBackStack() },
                    onShareText = { message ->
                        shareViaWhatsApp(context, message)
                    },
                    onSharePdf = { order ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        pdfToShow = file
                        onSharePdfAction = { sharePdfFile(context, file, "Share Order") }
                    },
                    onSendPdfToPhone = { order ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        val phone = order.supplierPhone ?: ""
                        pdfToShow = file
                        onSharePdfAction = {
                            if (phone.isNotBlank()) sharePdfViaWhatsAppToPhone(context, file, phone)
                            else sharePdfFile(context, file, "Share Order PDF")
                        }
                    },
                    onEditClick = { editId ->
                        navController.navigate(Screen.EditOrder.createRoute(editId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.EditOrder.route) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId")?.toLongOrNull() ?: return@composable
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.OrdersFlow.route) }
                val orderViewModel: OrderViewModel = hiltViewModel(parentEntry)
                val settingsVm: SettingsViewModel = hiltViewModel()
                val settingsState by settingsVm.uiState.collectAsState()

                LaunchedEffect(orderId) {
                    orderViewModel.loadOrderForEditing(orderId)
                }

                WholesaleOrderScreen(
                    viewModel = orderViewModel,
                    onBackClick = { navController.popBackStack() },
                    onShareText = { message ->
                        shareViaWhatsApp(context, message)
                    },
                    onSharePdf = { order ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        pdfToShow = file
                        onSharePdfAction = { sharePdfFile(context, file, "Share Order") }
                    },
                    onSendPdfToPhone = { order ->
                        val shopInfo = settingsState.toShopInfo()
                        val file = PdfGenerator.generateOrderPdf(context, shopInfo, order)
                        val phone = order.supplierPhone ?: ""
                        pdfToShow = file
                        onSharePdfAction = {
                            if (phone.isNotBlank()) sharePdfViaWhatsAppToPhone(context, file, phone)
                            else sharePdfFile(context, file, "Share Order PDF")
                        }
                    },
                    onOrderClick = { id ->
                        navController.navigate(Screen.OrderDetail.createRoute(id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // Voice Billing (bottom nav tab)
        composable(Screen.VoiceBilling.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsState()

            // Track bill saves via list count change
            val voiceBills by billingViewModel.voiceBills.collectAsState()
            var prevBillCount by remember { mutableStateOf(voiceBills.size) }
            LaunchedEffect(voiceBills.size) {
                if (voiceBills.size > prevBillCount) {
                    // A new bill was saved - show interstitial every 3rd save
                    activity?.let { adManager.showInterstitialAfterBillSave(it) }
                }
                prevBillCount = voiceBills.size
            }

            VoiceBillingScreen(
                viewModel = billingViewModel,
                onBackClick = null,
                onShareClick = { message ->
                    shareViaWhatsApp(context, message)
                },
                onShareToPhone = { message, phone ->
                    shareViaWhatsAppToPhone(context, message, phone)
                },
                onBillClick = { billId ->
                    navController.navigate(Screen.BillDetail.createRoute(billId)) {
                        launchSingleTop = true
                    }
                },
                onGeneratePdf = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfToShow = file
                    onSharePdfAction = { sharePdfFile(context, file, "Share Invoice") }
                },
                onSendPdfToWhatsApp = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfToShow = file
                    onSharePdfAction = {
                        if (bill.customerPhone.isNotBlank()) {
                            sharePdfViaWhatsAppToPhone(context, file, bill.customerPhone)
                        } else {
                            sharePdfViaWhatsApp(context, file)
                        }
                    }
                }
            )
        }

        // Edit Bill (loads into Voice Billing for editing)
        composable(Screen.EditBill.route) { backStackEntry ->
            val billId = backStackEntry.arguments?.getString("billId")?.toLongOrNull() ?: return@composable
            val billingViewModel: BillingViewModel = hiltViewModel()
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsState()

            LaunchedEffect(billId) {
                billingViewModel.loadBillForEditing(billId)
            }

            VoiceBillingScreen(
                viewModel = billingViewModel,
                onBackClick = { navController.popBackStack() },
                onShareClick = { message ->
                    shareViaWhatsApp(context, message)
                },
                onShareToPhone = { message, phone ->
                    shareViaWhatsAppToPhone(context, message, phone)
                },
                onBillClick = { id ->
                    navController.navigate(Screen.BillDetail.createRoute(id)) {
                        launchSingleTop = true
                    }
                },
                onGeneratePdf = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfToShow = file
                    onSharePdfAction = { sharePdfFile(context, file, "Share Invoice") }
                },
                onSendPdfToWhatsApp = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfToShow = file
                    onSharePdfAction = {
                        if (bill.customerPhone.isNotBlank()) {
                            sharePdfViaWhatsAppToPhone(context, file, bill.customerPhone)
                        } else {
                            sharePdfViaWhatsApp(context, file)
                        }
                    }
                }
            )
        }

        // Bills tab - with banner ad
        composable(Screen.BillHistory.route) {
            val billingViewModel: BillingViewModel = hiltViewModel()
            AdScaffold(adUnitId = AdConfig.BANNER_BILL_LIST) {
                BillHistoryScreen(
                    viewModel = billingViewModel,
                    onBillClick = { billId ->
                        navController.navigate(Screen.BillDetail.createRoute(billId)) {
                            launchSingleTop = true
                        }
                    },
                    onBackClick = null
                )
            }
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
                    pdfToShow = file
                    onSharePdfAction = { sharePdfFile(context, file, "Share Invoice") }
                },
                onSendPdfToWhatsApp = { bill ->
                    val shopInfo = settingsState.toShopInfo()
                    val file = PdfGenerator.generateBillPdf(context, shopInfo, bill)
                    pdfToShow = file
                    onSharePdfAction = {
                        if (bill.customerPhone.isNotBlank()) {
                            sharePdfViaWhatsAppToPhone(context, file, bill.customerPhone)
                        } else {
                            sharePdfViaWhatsApp(context, file)
                        }
                    }
                },
                onEditBill = { editBillId ->
                    navController.navigate(Screen.EditBill.createRoute(editBillId)) {
                        launchSingleTop = true
                    }
                },
                onEditPurchaseBill = { editBillId ->
                    navController.navigate(Screen.EditPurchaseBill.createRoute(editBillId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Edit Purchase Bill (loads into OcrResultScreen for editing)
        composable(Screen.EditPurchaseBill.route) { backStackEntry ->
            val billId = backStackEntry.arguments?.getString("billId")?.toLongOrNull() ?: return@composable
            val ocrViewModel: OcrViewModel = hiltViewModel()
            val existingSellerNames by ocrViewModel.existingSellerNames.collectAsState()
            val settingsVm: SettingsViewModel = hiltViewModel()
            val settingsState by settingsVm.uiState.collectAsState()

            LaunchedEffect(billId) {
                ocrViewModel.loadBillForEditing(billId)
            }

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
                    navController.popBackStack()
                },
                onDeleteItem = { item -> ocrViewModel.deleteItem(item) },
                onEditItem = { index, item -> ocrViewModel.editItem(index, item) },
                onAddItem = { item -> ocrViewModel.addItem(item) },
                onSellerNameChanged = { name -> ocrViewModel.updateSellerName(name) },
                onSellerPhoneChanged = { phone -> ocrViewModel.updateSellerPhone(phone) },
                onSendChatMessage = { message -> ocrViewModel.sendChatMessage(message, settingsState.languageCode) },

                onUseCalculatedSubtotal = { ocrViewModel.useCalculatedSubtotal() },
                onDismissSubtotalMismatch = { ocrViewModel.dismissSubtotalMismatch() },
                onDiscountPercentChanged = { percent -> ocrViewModel.updateDiscountPercent(percent) },
                onTaxPercentChanged = { percent -> ocrViewModel.updateTaxPercent(percent) }
            )
        }

        // Settings (sub-screen, accessed from dashboard header) - with banner ad
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            AdScaffold(adUnitId = AdConfig.BANNER_SETTINGS) {
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
                    isTranslating = isTranslating
                )
            }
        }

        // Scanned Bill History (Purchase Bills by Wholesaler) - with banner ad
        composable(Screen.ScannedBillHistory.route) {
            val historyViewModel: ScannedBillHistoryViewModel = hiltViewModel()
            AdScaffold(adUnitId = AdConfig.BANNER_TRANSACTION_HISTORY) {
                ScannedBillHistoryScreen(
                    viewModel = historyViewModel,
                    onWholesalerClick = { sellerName ->
                        navController.navigate(Screen.WholesalerBills.createRoute(sellerName))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // Wholesaler Bills - with banner ad
        composable(Screen.WholesalerBills.route) { backStackEntry ->
            val sellerName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("sellerName") ?: "",
                "UTF-8"
            )
            val historyViewModel: ScannedBillHistoryViewModel = hiltViewModel()
            AdScaffold(adUnitId = AdConfig.BANNER_TRANSACTION_HISTORY) {
                WholesalerBillsScreen(
                    sellerName = sellerName,
                    viewModel = historyViewModel,
                    onBillClick = { billId ->
                        navController.navigate(Screen.BillDetail.createRoute(billId)) {
                            launchSingleTop = true
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

}
