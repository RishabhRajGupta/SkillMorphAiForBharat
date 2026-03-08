
package com.example.skillmorph.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A sealed class to define all the screens in the app for type-safe navigation.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home_screen", "Home", Icons.Rounded.Home)
    object Goals : Screen("goals_screen", "Goals", Icons.Rounded.CheckCircle)
    object Tasks : Screen("tasks_screen", "Tasks", Icons.Rounded.List)
    object Profile : Screen("profile_screen", "Profile", Icons.Rounded.AccountCircle)
    object Settings : Screen("settings_screen", "Settings", Icons.Rounded.Settings)
}
