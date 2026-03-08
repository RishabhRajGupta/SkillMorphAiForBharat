
package com.example.skillmorph.presentation.auth

import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents the state of the authentication screen.
 */
data class AuthState(
    val isLoading: Boolean = false,
    val user: FirebaseUser? = null,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    init {
        // Check if a user is already logged in when the ViewModel is created.
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        val currentUser = repository.getLoggedInUser()
        _authState.value = AuthState(user = currentUser)
    }

    /**
     * Initiates the sign-in process with the credential response from Google.
     */
    fun signInWithGoogle(result: GetCredentialResponse) {
        viewModelScope.launch {
            // Set loading state to true
            _authState.value = AuthState(isLoading = true)

            repository.signInWithGoogle(result).collectLatest { result ->
                result.onSuccess {
                    // On success, update the state with the user and stop loading
                    _authState.value = AuthState(isLoading = false, user = it)
                }.onFailure {
                    // On failure, update the state with the error and stop loading
                    _authState.value = AuthState(isLoading = false, error = it.message)
                }
            }
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _authState.value = AuthState(user = null)
        }
    }
}
