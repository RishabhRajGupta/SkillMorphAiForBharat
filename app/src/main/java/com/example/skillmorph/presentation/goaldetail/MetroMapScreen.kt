
package com.example.skillmorph.presentation.goaldetail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skillmorph.presentation.goaldetail.models.DayPlan
import com.example.skillmorph.presentation.goaldetail.models.TimelineStatus

// --- Style Guide Colors ---
val DarkBackground = Color(0xFF0F1014)
val NeonBlue = Color(0xFF2D8CFF)
val NeonGreen = Color(0xFF00C853)
val CyberPurple = Color(0xFF9D00FF) // Added for subtle accents
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFF888888)

// --- The New Top-Level Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetroMapScreen(
    goalId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToResources: (String) -> Unit = {},
    viewModel: MetroMapViewModel = hiltViewModel()
) {
    val dayPlans by viewModel.dayPlans.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MISSION ROADMAP", // More gamified title
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Learning Resources Button
                    IconButton(onClick = { goalId?.let { onNavigateToResources(it) } }) {
                        Icon(
                            Icons.Rounded.MenuBook,
                            contentDescription = "Learning Resources",
                            tint = NeonBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        // Added a subtle background gradient for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color(0xFF13151B))
                    )
                )
                .padding(innerPadding)
        ) {
            MetroMapTimeline(
                days = dayPlans,
                onToggleSubtask = { dayNum, index -> viewModel.toggleSubtask(dayNum, index) },
                onDayComplete = { dayNum -> viewModel.completeDay(dayNum, context) }
            )
        }
    }
}

// --- Main Composable ---
@Composable
fun MetroMapTimeline(
    days: List<DayPlan>,
    onDayComplete: (Int) -> Unit,
    onToggleSubtask: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
    ) {
        itemsIndexed(days) { index, day ->
            TimelineItem(
                day = day,
                onDayComplete = onDayComplete,
                onToggleSubtask = onToggleSubtask,
                isLastItem = index == days.size - 1,
                isNext = if(index > 0) days[index-1].status == TimelineStatus.COMPLETED && day.status == TimelineStatus.LOCKED else false
            )
        }
    }
}

@Composable
private fun TimelineItem(
    day: DayPlan,
    onDayComplete: (Int) -> Unit,
    onToggleSubtask: (Int, Int) -> Unit,
    isLastItem: Boolean,
    isNext: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        MetroLine(status = day.status, isLastItem = isLastItem)

        Spacer(modifier = Modifier.width(16.dp))

        Box(modifier = Modifier.padding(bottom = 32.dp).weight(1f)) {
            NodeCard(node = day, onDayComplete = onDayComplete, onToggleSubtask = onToggleSubtask)
        }
    }
}

// --- 2. The UI Components (Line and Nodes) ---

@Composable
private fun MetroLine(status: TimelineStatus, isLastItem: Boolean) {
    val connectorBrush = when (status) {
        TimelineStatus.COMPLETED -> Brush.verticalGradient(listOf(NeonGreen, NeonGreen.copy(alpha = 0.5f)))
        TimelineStatus.CURRENT -> Brush.verticalGradient(listOf(NeonBlue, DarkBackground))
        else -> Brush.verticalGradient(listOf(Color.Gray.copy(0.2f), Color.Gray.copy(0.1f)))
    }

    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        // The Line
        if (!isLastItem) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nodeRadius = 12.dp.toPx()
                drawLine(
                    brush = connectorBrush,
                    start = Offset(center.x, nodeRadius * 2),
                    end = Offset(center.x, size.height),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // The Node Icon
        when (status) {
            TimelineStatus.COMPLETED -> NodeIcon(icon = Icons.Default.Check, color = NeonGreen, glow = true)
            TimelineStatus.CURRENT -> PulsatingNodeIcon()
            TimelineStatus.LOCKED -> NodeIcon(icon = Icons.Default.Lock, color = Color.Gray.copy(alpha = 0.3f), glow = false)
        }
    }
}

@Composable
private fun NodeIcon(icon: ImageVector, color: Color, glow: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(
                elevation = if (glow) 10.dp else 0.dp,
                spotColor = if (glow) color else Color.Transparent,
                shape = CircleShape
            )
            .background(DarkBackground, CircleShape)
            .border(2.dp, color, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PulsatingNodeIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow/ripple
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(scale)
                .background(NeonBlue.copy(alpha = alpha), CircleShape)
        )
        // Main Core
        Box(
            modifier = Modifier
                .size(24.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = NeonBlue)
                .background(NeonBlue, CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

// --- 3. The Cards (Content) ---

@Composable
private fun NodeCard(node: DayPlan, onDayComplete: (Int) -> Unit, onToggleSubtask: (Int, Int) -> Unit) {
    when (node.status) {
        TimelineStatus.COMPLETED -> CompletedTaskCard(node)
        TimelineStatus.CURRENT -> CurrentTaskCard(node, onDayComplete, onToggleSubtask)
        TimelineStatus.LOCKED -> LockedTaskCard(node)
    }
}

@Composable
private fun CompletedTaskCard(node: DayPlan) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassMetroEffect(color = Color.White.copy(alpha = 0.05f), borderColor = NeonGreen.copy(0.3f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("DAY ${node.dayLabel}", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(node.topic, color = TextSecondary, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.LineThrough)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("DONE", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CurrentTaskCard(
    node: DayPlan,
    onDayComplete: (Int) -> Unit,
    onToggleSubtask: (Int, Int) -> Unit
) {
    // Calculate progress for the bar
    val totalTasks = node.subTaskStates.size
    val completedTasks = node.subTaskStates.count { it }
    val progress = if (totalTasks > 0) completedTasks / totalTasks.toFloat() else 0f
    val isReadyToComplete = totalTasks > 0 && totalTasks == completedTasks

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassMetroEffect(color = NeonBlue.copy(alpha = 0.08f), borderColor = NeonBlue.copy(0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DAY ${node.dayLabel}",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${(progress * 100).toInt()}%",
                    color = NeonBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(node.topic, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 20.sp)

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = NeonBlue,
                trackColor = Color.White.copy(alpha = 0.1f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tasks
            if (node.subTasks.isNotEmpty()) {
                node.subTasks.forEachIndexed { index, task ->
                    val isChecked = node.subTaskStates.getOrElse(index) { false }
                    SubtaskRow(task, isChecked) { onToggleSubtask(node.dayNumber, index) }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Complete Button
            Button(
                onClick = { onDayComplete(node.dayNumber) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isReadyToComplete) NeonBlue else Color.White.copy(alpha = 0.1f),
                    contentColor = if (isReadyToComplete) DarkBackground else Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if(isReadyToComplete) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UNLOCK NEXT STATION", fontWeight = FontWeight.Bold)
                } else {
                    Text("${totalTasks - completedTasks} TASKS REMAINING", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SubtaskRow(text: String, isChecked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onToggle)
    ) {
        // Custom Checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (isChecked) NeonBlue else Color.Transparent,
                    RoundedCornerShape(6.dp)
                )
                .border(
                    1.5.dp,
                    if (isChecked) NeonBlue else Color.Gray.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(Icons.Default.Check, null, tint = DarkBackground, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            color = if (isChecked) TextSecondary else TextPrimary,
            textDecoration = if (isChecked) TextDecoration.LineThrough else null,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun LockedTaskCard(node: DayPlan) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassMetroEffect(color = Color.Black.copy(alpha = 0.3f), borderColor = Color.White.copy(alpha = 0.05f))
            .padding(vertical = 16.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("DAY ${node.dayLabel}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Locked Station", color = Color.Gray, fontSize = 14.sp)
            }
        }
    }
}

// --- Enhanced Glass Modifier ---
fun Modifier.glassMetroEffect(
    color: Color = Color.White.copy(alpha = 0.1f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    shape: RoundedCornerShape = RoundedCornerShape(16.dp)
): Modifier = this
    .shadow(8.dp, shape, spotColor = Color.Black.copy(alpha = 0.25f))
    .clip(shape)
    .background(color)
    .border(1.dp, borderColor, shape)

// --- Preview ---

@Preview(showBackground = true)
@Composable
private fun MetroMapScreenPreview() {
    MetroMapScreen(goalId = "1", onNavigateBack = {})
}
