package com.dukaan.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dukaan.ai.navigation.AppNavigation
import com.dukaan.ai.navigation.DukaanBottomBar
import com.dukaan.ai.navigation.Screen
import com.dukaan.core.db.dao.ShopProfileDao
import com.dukaan.core.ui.theme.DukaanTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shopProfileDao: ShopProfileDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val profile by shopProfileDao.getProfile().collectAsState(initial = null)
            val isDark = profile?.isDarkTheme ?: false

            DukaanTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val topLevelRoutes = setOf(
                    Screen.Dashboard.route,
                    Screen.BillHistory.route,
                    Screen.SmartKhata.route,
                    Screen.WholesaleOrder.route,
                    Screen.ScanBill.route
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
