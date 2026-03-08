package com.example.skillmorph.presentation.Profile

import android.annotation.SuppressLint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min // Fixes the minOf error
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.skillmorph.presentation.main.StreakBadge
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// --- Colors matching your Theme ---
val NeonBlue = Color(0xFF00E5FF)
val NeonPurple = Color(0xFF9D00FF)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.1f)
val TransparentBlack = Color(0x80000000) // 50% Black for Glass Background

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    // This forces a refresh every time you navigate to this tab
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Main Container - Transparent to let App Gradient show through
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = 80.dp), // Padding for bottom nav
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Header (Avatar & Level)
        ProfileHeader(state.user)

        // 2. Stats Grid (Updated with Max Streak)
        StatsRow(state.stats)

        // 3. LeetCode Style Heatmap
        val totalTasks = state.heatmap.sumOf{it.count}
        GlassCard(title = "$totalTasks tasks completed in the last year") {
            HeatmapGraph(state.heatmap)
        }

        // 4. Skills & Badges Split
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Radar Chart (60% width)
            Box(modifier = Modifier.weight(0.6f)) {
                GlassCard(title = "Skill Matrix", modifier = Modifier.fillMaxHeight()) {
                    SkillRadarChart(state.skillRadar)
                }
            }

            // Badges (40% width)
            Box(modifier = Modifier.weight(0.4f)) {
                GlassCard(title = "Badges", modifier = Modifier.fillMaxHeight()) {
                    BadgesList(state.badges)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// UI COMPONENTS
// -----------------------------------------------------------------------------

@Composable
fun ProfileHeader(user: UserInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Avatar Ring with Gradient
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(86.dp)) {
                drawCircle(
                    brush = Brush.linearGradient(listOf(NeonBlue, NeonPurple)),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.take(1),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Text Info
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(user.title, fontSize = 14.sp, color = NeonBlue)

            Spacer(modifier = Modifier.height(12.dp))

            // Level & XP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lvl ${user.currentLevel}", fontWeight = FontWeight.Bold, color = NeonPurple, fontSize = 14.sp)
                Text("${user.currentXp} / ${user.maxXp} XP", color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))

            // XP Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(user.currentXp / user.maxXp.toFloat())
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)))
                )
            }
        }
    }
}

@Composable
fun StatsRow(stats: UserStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(label = "Current Streak", value = "${stats.currentStreak} \uD83D\uDD25", modifier = Modifier.weight(1f))
        StatCard(label = "Active Days", value = "${stats.totalActiveDays}", modifier = Modifier.weight(1f))
        StatCard(label = "Max Streak", value = "${stats.maxStreak}", modifier = Modifier.weight(1f)) // New Stat
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier) {
    // Re-implementing Glass Effect manually here to ensure it works inside the row
    Column(
        modifier = modifier
            .glassEffect() // Using your extension
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
fun HeatmapGraph(data: List<DailyActivity>) {
    // 1. Group Data by Month
    val groupedByMonth = remember(data) {
        data.groupBy { YearMonth.from(it.date) }
    }

    // 2. Prepare keys for Reverse Layout
    // Since layout is reversed (Right-to-Left), the "First" item we pass
    // will appear on the far RIGHT. So we want the Latest Month (Dec/Jan) first.
    val monthKeys = remember(groupedByMonth) {
        groupedByMonth.keys.sorted().reversed() // e.g. [Jan 2026, Dec 2025, Nov 2025...]
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            reverseLayout = true // 🔴 THE FIX: Starts content from the Right Edge
        ) {
            items(monthKeys) { yearMonth ->
                val daysInMonth = groupedByMonth[yearMonth] ?: emptyList()

                // We render the months normally.
                // Because of reverseLayout + reversed list, they appear visually as:
                // [Oct] [Nov] [Dec] | <-- Screen Edge
                MonthBlock(yearMonth, daysInMonth)
            }
        }
    }
}

@Composable
fun MonthBlock(yearMonth: YearMonth, days: List<DailyActivity>) {
    // Calculate layout for this specific month
    val firstDayDate = days.firstOrNull()?.date ?: return

    // DayOfWeek value: Mon=1, ... Sun=7.
    // We want Sun=0, Mon=1... Sat=6 for our grid logic.
    val startDayOfWeek = firstDayDate.dayOfWeek.value % 7

    // We pad the start with "null" to align the first day correctly
    val paddedDays = List(startDayOfWeek) { null } + days

    // Chunk into columns of 7 (Weeks)
    val weeks = paddedDays.chunked(7)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The Grid
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Render 7 cells per column.
                    // If the week is incomplete (end of month), we pad with spacers.
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        if (day != null) {
                            // Actual Data Day
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(getHeatmapColor(day.intensity))
                            )
                        } else {
                            // Empty Placeholder (Padding)
                            // We make it transparent so it just takes up space
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Month Label at Bottom
        Text(
            text = yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

fun getHeatmapColor(intensity: Int): Color {
    return when (intensity) {
        // White tint for empty days (0) to be visible on dark glass background
        0 -> Color.White.copy(alpha = 0.1f)
        1 -> Color(0xFF0E4429) // Deep Green
        2 -> Color(0xFF006D32) // Medium Green
        3 -> Color(0xFF26A641) // Bright Green
        4 -> Color(0xFF39D353) // Neon Green
        else -> Color.White.copy(alpha = 0.1f)
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SkillRadarChart(skills: Map<String, Float>) {
    val labels = skills.keys.toList()
    val values = skills.values.toList()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        // Failsafe radius calc
        val minDim = if (maxWidth < maxHeight) maxWidth else maxHeight
        val chartRadius = minDim / 2 * 0.65f
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radiusPx = with(density) { chartRadius.toPx() }
            val center = Offset(size.width / 2, size.height / 2)
            val angleStep = (2 * Math.PI / labels.size).toFloat()

            // Web
            for (i in 1..4) {
                val r = radiusPx * (i / 4f)
                val path = Path()
                for (j in labels.indices) {
                    val angle = j * angleStep - (Math.PI / 2).toFloat()
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, Color.Gray.copy(alpha = 0.2f), style = Stroke(1.dp.toPx()))
            }

            // Data
            val dataPath = Path()
            for (j in labels.indices) {
                val r = radiusPx * values[j]
                val angle = j * angleStep - (Math.PI / 2).toFloat()
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (j == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            }
            dataPath.close()
            drawPath(dataPath, NeonBlue.copy(alpha = 0.3f))
            drawPath(dataPath, NeonBlue, style = Stroke(2.dp.toPx()))
        }

        // Labels
        labels.forEachIndexed { index, label ->
            val angleStep = (2 * Math.PI / labels.size).toFloat()
            val angle = index * angleStep - (Math.PI / 2).toFloat()
            val labelRadius = chartRadius * 1.35f
            val xOffset = labelRadius * cos(angle)
            val yOffset = labelRadius * sin(angle)

            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(x = xOffset, y = yOffset)
            )
        }
    }
}

@Composable
fun BadgesList(badges: List<Badge>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        badges.forEach { badge ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = badge.icon,
                    contentDescription = null,
                    tint = if (badge.isUnlocked) NeonBlue else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = badge.name,
                    color = if (badge.isUnlocked) Color.White else Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HELPERS
// -----------------------------------------------------------------------------

@Composable
fun GlassCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .glassEffect() // Using your extension
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// --- Placeholder for your custom modifier to ensure compilation ---
// You already have this in your Utils file, but I include the logic here for reference.
fun Modifier.glassEffect(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(Color(0x40000000)) // Fallback transparent black
    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))


@Preview(showSystemUi = true)
@Composable
fun Preview(){
    ProfileScreen()
}