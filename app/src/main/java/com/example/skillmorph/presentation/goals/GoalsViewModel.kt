
package com.example.skillmorph.presentation.goals

import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.data.local.entities.GoalEntity
import com.example.skillmorph.data.remote.GoalDto
import com.example.skillmorph.data.remote.SkillMorphApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val api: SkillMorphApi
) : ViewModel() {

    // CHANGE: Use GoalDto instead of GoalEntity
    private val _goals = MutableStateFlow<List<GoalDto>>(emptyList())
    val goals = _goals.asStateFlow()

    fun fetchGoals() {
        viewModelScope.launch {
            try {
                // Direct assignment! No more mapping needed.
                _goals.value = api.getGoals()
            } catch (e: Exception) {
                Log.e("GoalsVM", "Error fetching goals: ${e.message}")
            }
        }
    }
}
