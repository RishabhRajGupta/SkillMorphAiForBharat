package com.example.skillmorph.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.skillmorph.utils.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    appNavController: NavController
) {
    val context = LocalContext.current
    val isLoggedOut by viewModel.isLoggedOut.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedOut) {
        if (isLoggedOut) {
            onLogout()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F1014),
                        Color(0xFF16181D)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection("Account") {
                        SettingsItem(
                            icon = Icons.Rounded.Person,
                            title = "Profile Information",
                            subtitle = if (userProfile.dob.isEmpty()) userProfile.name else "${userProfile.name} • ${userProfile.dob}",
                            onClick = { showProfileDialog = true }
                        )
                        SettingsItem(
                            icon = Icons.Rounded.Security,
                            title = "Security",
                            subtitle = "Password and authentication",
                            onClick = {}
                        )
                    }
                }

                item {
                    SettingsSection("Preferences") {
                        SettingsItem(
                            icon = Icons.Rounded.Notifications,
                            title = "Notifications",
                            subtitle = "Manage your alerts",
                            onClick = {}
                        )
                        SettingsItem(
                            icon = Icons.Rounded.Palette,
                            title = "Appearance",
                            subtitle = "Theme and styling",
                            onClick = {}
                        )
                    }
                }

                item {
                    SettingsSection("Danger Zone") {
                        SettingsItem(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Delete Account",
                            subtitle = "Permanently remove your account and data",
                            onClick = { showDeleteConfirmDialog = true },
                            titleColor = Color(0xFFFF5252)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassyLogoutButton(onClick = { showLogoutConfirmDialog = true })
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            userProfile = userProfile,
            onDismiss = { showProfileDialog = false },
            onSave = { name, dob ->
                viewModel.updateProfile(name, dob, userProfile.avatarRes)
                showProfileDialog = false
            },
            onChangeAvatar = {
                showAvatarDialog = true
            }
        )
    }

    if (showAvatarDialog) {
        AvatarSelectionDialog(
            currentAvatar = userProfile.avatarRes,
            onDismiss = { showAvatarDialog = false },
            onAvatarSelected = { avatarRes ->
                viewModel.updateProfile(userProfile.name, userProfile.dob, avatarRes)
                showAvatarDialog = false
            }
        )
    }

    if (showLogoutConfirmDialog) {
        ConfirmationDialog(
            title = "Log Out",
            message = "Are you sure you want to log out of your account?",
            confirmText = "Log Out",
            onConfirm = {
                showLogoutConfirmDialog = false
                viewModel.signOut()
            },
            onDismiss = { showLogoutConfirmDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        ConfirmationDialog(
            title = "Delete Account",
            message = "Are you sure you want to delete your account? This will open your email app to send a deletion request.",
            confirmText = "Delete",
            confirmColor = Color(0xFFFF5252),
            onConfirm = {
                showDeleteConfirmDialog = false
                
                val recipient = "altofrisssu@gmail.com"
                val subject = "Account Deletion Request - SkillMorph"
                val body = "Hello,\n\nI would like to request the permanent deletion of my SkillMorph account associated with the email: ${userProfile.email}.\n\nThank you."
                
                // Use mailto: with URI encoding for subject and body
                val uriText = "mailto:$recipient" +
                        "?subject=" + Uri.encode(subject) +
                        "&body=" + Uri.encode(body)
                
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse(uriText)
                }

                context.startActivity(Intent.createChooser(intent, "Send Email"))
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color = Color(0xFF00E5FF),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect()
                .background(Color(0xFF16181D).copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = Color.Gray,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor.copy(alpha = 0.1f), contentColor = confirmColor),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, confirmColor.copy(alpha = 0.5f))
                    ) {
                        Text(confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileEditDialog(
    userProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onChangeAvatar: () -> Unit
) {
    var name by remember { mutableStateOf(userProfile.name) }
    var dob by remember { mutableStateOf(userProfile.dob) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect()
                .background(Color(0xFF16181D).copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Edit Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Avatar Selection
                Box(contentAlignment = Alignment.BottomEnd) {
                    AvatarView(
                        avatarRes = userProfile.avatarRes,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { onChangeAvatar() }
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (DD/MM/YYYY)", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = userProfile.email,
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { onSave(name, dob) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarSelectionDialog(
    currentAvatar: Int,
    onDismiss: () -> Unit,
    onAvatarSelected: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect()
                .background(Color(0xFF16181D).copy(alpha = 0.95f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Choose Your Avatar",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(250.dp)
                ) {
                    items((0..5).toList()) { index ->
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (currentAvatar == index) 2.dp else 0.dp,
                                    color = if (currentAvatar == index) Color(0xFF00E5FF) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onAvatarSelected(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            AvatarView(avatarRes = index, modifier = Modifier.size(70.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = Color(0xFF00E5FF))
                }
            }
        }
    }
}

@Composable
fun AvatarView(avatarRes: Int, modifier: Modifier = Modifier) {
    val avatars = listOf(
        Icons.Rounded.Pets to listOf(Color(0xFF00E5FF), Color(0xFF00B8D4)), // Cyan Cat/Dog
        Icons.Rounded.RocketLaunch to listOf(Color(0xFFFF5252), Color(0xFFD32F2F)), // Red Rocket
        Icons.Rounded.Psychology to listOf(Color(0xFF7C4DFF), Color(0xFF512DA8)), // Purple Brain
        Icons.Rounded.AutoAwesome to listOf(Color(0xFF69F0AE), Color(0xFF388E3C)), // Green Magic
        Icons.Rounded.Bolt to listOf(Color(0xFFFFD740), Color(0xFFFFA000)), // Yellow Bolt
        Icons.Rounded.Extension to listOf(Color(0xFF40C4FF), Color(0xFF1976D2)) // Blue Puzzle
    )
    
    val (icon, selectedColors) = avatars[avatarRes % avatars.size]

    Box(
        modifier = modifier
            .background(Brush.linearGradient(selectedColors))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF00E5FF),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect()
                .background(Color.White.copy(alpha = 0.05f))
                .padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color.White
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (titleColor != Color.White) titleColor else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun GlassyLogoutButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF5252).copy(alpha = 0.1f),
            contentColor = Color(0xFFFF5252)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out", fontWeight = FontWeight.Bold)
        }
    }
}
