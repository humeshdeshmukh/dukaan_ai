package com.dukaan.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dukaan.ai.navigation.AppNavigation
import com.dukaan.ai.navigation.DukaanBottomBar
import com.dukaan.ai.navigation.Screen
import com.dukaan.ai.translation.TranslationManager
import com.dukaan.core.db.dao.ShopProfileDao
import com.dukaan.core.ui.theme.DukaanTheme
import com.dukaan.core.ui.translation.LocalAppStrings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shopProfileDao: ShopProfileDao

    @Inject
    lateinit var translationManager: TranslationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val profile by shopProfileDao.getProfile().collectAsState(initial = null)
            val isDark = profile?.isDarkTheme ?: false
            val appStrings by translationManager.currentStrings.collectAsState()

            // Load translation on startup — uses cache if fresh, re-translates if stale
            LaunchedEffect(profile?.languageCode) {
                val code = profile?.languageCode ?: "en"
                translationManager.loadOrTranslate(code)
            }

            DukaanTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalAppStrings provides appStrings) {
                    val navController = rememberNavController()
                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    val topLevelRoutes = setOf(
                        Screen.Dashboard.route,
                        Screen.VoiceBilling.route,
                        Screen.SmartKhata.route,
                        Screen.WholesaleOrder.route,
                        Screen.ScanBill.route,
                        Screen.BillHistory.route,
                        Screen.BillDetail.route
                    )
                    val showBottomBar = currentRoute in topLevelRoutes

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it })
                            ) {
                                DukaanBottomBar(
                                    navController = navController,
                                    currentRoute = currentRoute
                                )
                            }
                        }
                    ) { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            translationManager = translationManager,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
