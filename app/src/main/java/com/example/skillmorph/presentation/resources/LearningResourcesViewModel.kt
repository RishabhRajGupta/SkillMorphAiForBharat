package com.example.skillmorph.presentation.resources

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.data.remote.ResourceDto
import com.example.skillmorph.data.remote.SkillMorphApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResourcesUiState(
    val isLoading: Boolean = true,
    val resources: List<ResourceDto> = emptyList(),
    val errorMessage: String? = null,
    val selectedFilter: String = "all" // "all", "course", "article", "video", "exercise"
)

@HiltViewModel
class LearningResourcesViewModel @Inject constructor(
    private val api: SkillMorphApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResourcesUiState())
    val uiState = _uiState.asStateFlow()

    private val goalId: String? = savedStateHandle["goalId"]

    init {
        fetchResources()
    }

    fun fetchResources(refresh: Boolean = false) {
        if (goalId == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = api.getLearningResources(goalId, refresh)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    resources = response.resources
                )
            } catch (e: Exception) {
                Log.e("ResourcesVM", "Error fetching resources: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Could not load resources. Please try again."
                )
            }
        }
    }

    fun setFilter(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun getFilteredResources(): List<ResourceDto> {
        val state = _uiState.value
        return if (state.selectedFilter == "all") {
            state.resources
        } else {
            state.resources.filter { it.type == state.selectedFilter }
        }
    }
}
