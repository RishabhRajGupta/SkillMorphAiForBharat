
package com.example.skillmorph.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.skillmorph.presentation.auth.AuthScreen
import com.example.skillmorph.presentation.auth.AuthViewModel
import com.example.skillmorph.presentation.goaldetail.MetroMapScreen
import com.example.skillmorph.presentation.main.MainScreen
import com.example.skillmorph.presentation.resources.LearningResourcesScreen
import com.example.skillmorph.presentation.settings.SettingsScreen

/**
 * The main navigation graph for the entire application.
 * It decides whether to show the authentication flow or the main app content.
 */
@Composable
fun AppNavigation(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState()

    // This effect observes the authentication state. On app start, if the user is
    // already logged in, it will navigate to the main screen.
    LaunchedEffect(key1 = authState.user) {
        if (authState.user != null) {
            navController.navigate("main_route") {
                // Clear the authentication screen from the back stack so the user can't go back to it.
                popUpTo("auth_route") { inclusive = true }
            }
        } else {
            // If user logs out, go back to the authentication screen
            navController.navigate("auth_route") {
                popUpTo("main_route") { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        // The app always starts at the auth route. The LaunchedEffect handles redirection.
        startDestination = "auth_route"
    ) {
        composable(route = "auth_route") {
            AuthScreen(appNavController = navController)
        }
        composable(route = "main_route") {
            MainScreen(navController)
        }
        // Settings Screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    // Force navigation to auth_route
                    navController.navigate("auth_route") {
                        popUpTo("main_route") { inclusive = true }
                    }
                },
                appNavController = navController
            )
        }
        // The new destination for the Metro Map screen
        composable("metro_map_screen/{goalId}") { backStackEntry ->
            val goalId = backStackEntry.arguments?.getString("goalId")

            // Call the new top-level screen
            MetroMapScreen(
                goalId = goalId,
                onNavigateBack = {
                    // This allows the back button to work
                    navController.popBackStack()
                },
                onNavigateToResources = { gId ->
                    navController.navigate("resources_screen/$gId")
                }
            )
        }
        // Learning Resources screen
        composable("resources_screen/{goalId}") { backStackEntry ->
            val goalId = backStackEntry.arguments?.getString("goalId")
            LearningResourcesScreen(
                goalId = goalId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
