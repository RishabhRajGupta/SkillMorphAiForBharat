
package com.example.skillmorph.presentation.main

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.skillmorph.HomeScreen
import com.example.skillmorph.data.remote.SessionResponse
import com.example.skillmorph.presentation.Profile.ProfileScreen
import com.example.skillmorph.presentation.goals.GoalsScreen
import com.example.skillmorph.presentation.main.viewModel.AgentViewModel
import com.example.skillmorph.presentation.navigation.Screen
import com.example.skillmorph.presentation.tasks.TasksScreen
import com.example.skillmorph.ui.theme.NeonBlue
import com.example.skillmorph.ui.theme.TransparentWhite
import com.example.skillmorph.utils.glassEffect
import kotlinx.coroutines.launch


@Composable
fun MainScreen(
    appNavController: NavController,
    // We get the VM here to populate the sidebar list
    agentViewModel: AgentViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    // 1. Drawer State & Data
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pastSessions by agentViewModel.pastSessions.collectAsState()

    val currentStreak by agentViewModel.currentStreak.collectAsState()

    // 2. ROOT DRAWER (Wraps the whole screen)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GlassyNavigationDrawerContent(
                pastSessions = pastSessions,
                onSessionClick = { sessionId ->
                    agentViewModel.loadSession(sessionId)
                    scope.launch { drawerState.close() }

                    // Navigate home if needed
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id)
                    }
                },
                onSettingsClick = {
                    scope.launch {
                        drawerState.close()
                        appNavController.navigate(Screen.Settings.route)
                    }
                }
            )
        }
    ) {
        // 3. YOUR EXISTING SCAFFOLD (Now inside the drawer)
        Scaffold(
            topBar = {
                TopAppBar(
                    streakCount = currentStreak,
                    isStreakActive = currentStreak > 0,
                    hasNotification = true,
                    // 🟢 CONNECTED: Clicking Menu opens the Drawer
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationClick = { /* ... */ }
                )
            },
            bottomBar = {
                BottomNavBar(navController = navController)
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            // 4. CONTENT AREA
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                // Pass the SHARED ViewModel so the Agent screen shows the loaded session
                composable(Screen.Home.route) {
                    HomeScreen(agentViewModel)
                }

                composable(Screen.Goals.route) {
                    GoalsScreen(onGoalClick = { goalId ->
                        appNavController.navigate("metro_map_screen/$goalId")
                    })
                }
                composable(Screen.Tasks.route) { TasksScreen() }
                composable(Screen.Profile.route) { ProfileScreen() }
            }
        }
    }
}


@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    streakCount: Int,
    isStreakActive: Boolean, // True = Orange Fire, False = Gray/White
    hasNotification: Boolean,
    onMenuClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    // Standard TopAppBar has elevation/shadow by default.
    // We use a simple Row to get full control over transparency.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // Respect the notch area
            .padding(horizontal = 16.dp, vertical = 12.dp), // Add breathing room
        verticalAlignment = Alignment.CenterVertically
    ) {

        // 1. LEFT: Side Sheet Trigger (Hamburger)
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 2. APP NAME: "SkillMorph"
        Text(
            text = "SkillMorph",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f) // Pushes right-side items to the end
        )

        // 3. STREAK COUNTER
        StreakBadge(count = streakCount, isActive = isStreakActive)

        Spacer(modifier = Modifier.width(16.dp))

        // 4. NOTIFICATION BELL (With Badge)
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF00E5FF), // Cyan tint like your screenshot
                    modifier = Modifier.size(28.dp)
                )
            }
            // The Red Dot Badge
            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 8.dp) // Adjust position slightly
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
        }
    }
}

@Composable
fun StreakBadge(count: Int, isActive: Boolean) {
    // Dynamic Colors based on active state
    val backgroundColor = if (isActive) {
        // Active: Low opacity Orange background
        Color(0xFFFF9800).copy(alpha = 0.2f)
    } else {
        // Inactive: Transparent White background
        Color.White.copy(alpha = 0.1f)
    }

    val iconColor = if (isActive) Color(0xFFFF9800) else Color.LightGray.copy(alpha = 0.7f)
    val textColor = if (isActive) Color(0xFFFF9800) else Color.LightGray.copy(alpha = 0.7f)

    // Border for active state
    val borderStroke = if (isActive) {
        BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
    } else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50)) // Pill shape
            .background(backgroundColor)
            .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp) // Internal padding
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = "Streak",
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = count.toString(),
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}


@Composable
fun InputModeChip(
    isVoiceMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // --- GLASSY COLORS ---
    val neonCyan = Color(0xFF00E5FF)

    // 1. The Glass Gradient (Top is slightly lighter to simulate reflection)
    val glassGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.15f), // Top: lighter
            Color.White.copy(alpha = 0.05f)  // Bottom: darker/transparent
        )
    )

    // 2. The Border Gradient (Shiny top rim, fading bottom)
    val borderGradient = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.3f),
            Color.Transparent
        )
    )

    // Dimensions
    val chipHeight = 54.dp
    val indicatorWidth = 100.dp
    val totalWidth = 200.dp

    // Animation State
    val indicatorOffset by animateDpAsState(
        targetValue = if (isVoiceMode) 0.dp else indicatorWidth,
        animationSpec = tween(300),
        label = "offset"
    )

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(chipHeight)
            .clip(CircleShape)
            // Apply the Glass Gradient Background
            .background(glassGradient)
            // Add the Shiny Border
            .border(1.dp, borderGradient, CircleShape)
            .clickable { onToggle() }
    ) {
        // --- ACTIVE INDICATOR (Cyan Pill) ---
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(CircleShape)
                .background(neonCyan.copy(alpha = 0.8f)) // Slightly see-through cyan
            // Add a subtle glow/blur to the indicator if desired
        )

        // --- CONTENT LAYER ---
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipOption(
                text = "Voice",
                icon = Icons.Rounded.Mic,
                isSelected = isVoiceMode,
                modifier = Modifier.width(indicatorWidth)
            )

            ChipOption(
                text = "Type",
                icon = Icons.Rounded.Keyboard,
                isSelected = !isVoiceMode,
                modifier = Modifier.width(indicatorWidth)
            )
        }
    }
}

@Composable
private fun ChipOption(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier
) {
    // Text Color Animation: Black on Cyan, White on Glass
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
        animationSpec = tween(300),
        label = "color"
    )

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.Goals,
        Screen.Tasks,
        Screen.Profile
    )

    NavigationBar(
        modifier = Modifier.glassEffect(),
        containerColor = TransparentWhite // Use a semi-transparent color for the glass effect
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { screen ->
            NavigationBarItem(
                label = { Text(screen.title) },
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        // Pop up to the start destination of the graph to avoid building up a large back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when re-selecting the same item
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonBlue,
                    selectedTextColor = NeonBlue,
                    unselectedIconColor = Color.LightGray,
                    unselectedTextColor = Color.LightGray,
                    indicatorColor = Color.Transparent // Hide the selection indicator
                )
            )
        }
    }
}

// Side Bar / Drawer
@Composable
fun GlassyNavigationDrawerContent(
    pastSessions: List<SessionResponse>, // Assuming you have this data class
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent, // Crucial: Remove default solid background
        drawerContentColor = Color.White,
        modifier = Modifier
            .width(300.dp) // Set a fixed width or fillMaxWidth(0.8f)
            .padding(end = 16.dp) // Gap between drawer and screen edge
    ) {
        // The Glass Container
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .glassEffect() // Your existing extension
                .background(
                    // Add a subtle vertical gradient for depth
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F1014).copy(alpha = 0.8f),
                            Color(0xFF16181D).copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // 1. Header Section
            DrawerHeader()

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Scrollable History List
            Text(
                "Recent Sessions",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f), // Takes all available space
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pastSessions) { session ->
                    GlassySessionItem(
                        session = session,
                        onClick = { onSessionClick(session.sessionId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // 3. Settings Button
            GlassySettingsButton(onClick = onSettingsClick)
        }
    }
}

// --- Sub-Components ---

@Composable
fun DrawerHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A simple "Time Travel" Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF9D00FF)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Time Travel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Restore past context",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF00E5FF) // Neon Cyan
            )
        }
    }
}

@Composable
fun GlassySessionItem(session: SessionResponse, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.05f)) // Very subtle hover effect
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = session.date, // e.g., "Oct 24 • 2:30 PM"
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun GlassySettingsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings", fontWeight = FontWeight.Bold)
        }
    }
}
