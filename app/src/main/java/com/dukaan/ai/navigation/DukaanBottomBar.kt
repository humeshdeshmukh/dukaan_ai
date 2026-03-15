package com.dukaan.ai.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dukaan.core.ui.translation.LocalAppStrings

@Composable
fun DukaanBottomBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val strings = LocalAppStrings.current
    val tabs = listOf(
        BottomNavItem.Home,
        BottomNavItem.Scan,
        BottomNavItem.Bills,
        BottomNavItem.Khata,
        BottomNavItem.Orders
    )

    val labelMap = mapOf(
        BottomNavItem.Home to strings.navHome,
        BottomNavItem.Scan to strings.navScan,
        BottomNavItem.Bills to strings.navBills,
        BottomNavItem.Khata to strings.navKhata,
        BottomNavItem.Orders to strings.navOrders
    )

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 0.5.dp.toPx()
                )
            },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { item ->
            val isSelected = when (item) {
                is BottomNavItem.Scan -> currentRoute == Screen.ScanBill.route
                is BottomNavItem.Khata -> currentRoute == Screen.SmartKhata.route
                else -> currentRoute == item.route
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = labelMap[item] ?: item.label
                    )
                },
                label = {
                    Text(
                        text = labelMap[item] ?: item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
