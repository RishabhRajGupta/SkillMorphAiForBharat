package com.example.skillmorph.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skillmorph.data.remote.TaskDto
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    // Split for the new UI logic
    val goalTasks = uiState.tasks.filter { it.type == "GOAL" }
    val sideQuests = uiState.tasks.filter { it.type == "SIDE_QUEST" }

    if (showCalendarDialog) {
        CalendarDialog(
            initialDate = uiState.selectedDate,
            onDateSelected = {
                viewModel.selectDate(it)
                showCalendarDialog = false
            },
            onDismiss = { showCalendarDialog = false }
        )
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = {
                viewModel.addSideQuest(it)
                showAddTaskDialog = false
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.fetchTasksForDate(Date())
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = Color(0xFF00E5FF)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- YOUR CALENDAR HEADER ---
            WeekCalendar(
                calendarDates = uiState.calendarDates,
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.selectDate(it) }
            )

            IconButton(onClick = { showCalendarDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Open calendar", tint = Color.White)
            }

            // --- THE TASK LIST ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SECTION 1: MAIN QUESTS (Goals)
                if (goalTasks.isNotEmpty()) {
                    item { Text("MAIN QUESTS", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                    // USE TaskCard HERE
                    items(items = goalTasks, key = { task -> task.id ?: java.util.UUID.randomUUID().toString() }) { task ->
                        TaskCard(task = task, isMain = true, onChecked = { viewModel.onTaskChecked(task, true)})
                    }
                }
                else{
                    item { Text("MAIN QUESTS", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    item { Text(text = "Create Goals to add Main Quest", color = Color.Gray, fontSize = 12.sp) }
                }

                // SECTION 2: SIDE QUESTS
                if (sideQuests.isNotEmpty()) {
                    item { Text("SIDE QUESTS", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                    // USE TaskCard HERE TOO
                    items(items = sideQuests, key = { it.id ?: it.hashCode()}) { task ->
                        TaskCard(task = task, isMain = false, onChecked = { viewModel.onTaskChecked(task, true) })
                    }
                }
                else{
                    item { Text("SIDE QUESTS", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    item { Text(text = "Add Your task to add Side Quest", color = Color.Gray, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: TaskDto, isMain: Boolean, onChecked: () -> Unit) {
    val borderColor = if (isMain) Color(0xFF00E5FF) else Color.Gray.copy(alpha = 0.5f)
    val isDone = task.isCompleted == true

    val alpha = if (isDone) 0.5f else 1.0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(16.dp)
            .alpha(alpha)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!isDone) onChecked() }) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Complete",
                    tint = if (isDone) Color.Green else borderColor
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = task.title ?: "Untitled",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    // Optional: Strikethrough if done
                    textDecoration = if (isDone) TextDecoration.LineThrough else null
                )
                if (isMain) {
                    Text("Goal: ${task.goalTitle ?: "Unknown"}", color = borderColor, fontSize = 12.sp)
                }
            }
        }
    }
}
// --- YOUR EXISTING COMPONENTS (Unchanged logic, just restored) ---


@Composable
fun WeekCalendar(
    calendarDates: List<CalendarDate>,
    selectedDate: Date,
    onDateSelected: (Date) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        calendarDates.forEach { date ->
            Day(
                date = date,
                isSelected = isSameDay(date.date, selectedDate),
                onDateSelected = onDateSelected
            )
        }
    }
}

@Composable
fun Day(date: CalendarDate, isSelected: Boolean, onDateSelected: (Date) -> Unit) {
    val backgroundColor = if (isSelected) Color(0xFF00E5FF) else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.White

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onDateSelected(date.date) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = date.dayOfWeek, color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(text = date.dayOfMonth, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// Helper for Date Comparison
private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = date1 }
    val cal2 = Calendar.getInstance().apply { time = date2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

// Simple Dialog for adding side quests
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Side Quest") },
        text = { TextField(value = text, onValueChange = { text = it }, placeholder = { Text("Task name...") }) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDialog(initialDate: Date, onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.time)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { datePickerState.selectedDateMillis?.let { onDateSelected(Date(it)) } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}
