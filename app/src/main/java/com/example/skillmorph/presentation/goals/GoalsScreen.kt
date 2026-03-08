package com.example.skillmorph.presentation.goals

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.skillmorph.data.local.entities.GoalEntity
import com.example.skillmorph.presentation.goals.components.GoalCard
import com.example.skillmorph.ui.theme.gradientBrush

// Change 'Long' to 'String' in the signature
@Composable
fun GoalsScreen(onGoalClick: (String) -> Unit) {
    val viewModel: GoalsViewModel = hiltViewModel()
    val goals by viewModel.goals.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchGoals()
    }

    if(!goals.isEmpty()){
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(goals) { goal ->
                // Pass the DTO directly
                GoalCard(
                    goal = goal,
                    onGoalClick = onGoalClick
                )
            }
        }
    }
    else{
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Text("No Goals Created Yet", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}