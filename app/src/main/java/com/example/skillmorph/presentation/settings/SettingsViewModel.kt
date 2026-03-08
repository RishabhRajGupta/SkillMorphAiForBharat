package com.example.skillmorph.presentation.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillmorph.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class UserProfile(
    val name: String = "",
    val email: String = "",
    val dob: String = "",
    val avatarRes: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _isLoggedOut = MutableStateFlow(false)
    val isLoggedOut = _isLoggedOut.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile = _userProfile.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            // Set initial state from Auth immediately
            _userProfile.value = UserProfile(
                name = user.displayName ?: "User",
                email = user.email ?: "",
                dob = ""
            )
            
            viewModelScope.launch {
                try {
                    Log.d("SettingsVM", "Fetching profile for UID: ${user.uid}")
                    // Load from Firestore
                    val doc = firestore.collection("users").document(user.uid).get().await()
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: user.displayName ?: "User"
                        val dob = doc.getString("dob") ?: ""
                        val avatarRes = doc.getLong("avatarRes")?.toInt() ?: 0
                        
                        Log.d("SettingsVM", "Profile fetched: name=$name, dob=$dob, avatar=$avatarRes")
                        
                        _userProfile.value = UserProfile(
                            name = name,
                            email = user.email ?: "",
                            dob = dob,
                            avatarRes = avatarRes
                        )
                    } else {
                        Log.d("SettingsVM", "No Firestore document found for user")
                    }
                } catch (e: Exception) {
                    Log.e("SettingsVM", "Error loading profile: ${e.message}", e)
                }
            }
        }
    }

    fun updateProfile(name: String, dob: String, avatarRes: Int) {
        val user = firebaseAuth.currentUser ?: return
        
        Log.d("SettingsVM", "Updating profile: name=$name, dob=$dob, avatar=$avatarRes")

        // 1. Update UI state immediately for responsiveness
        _userProfile.value = _userProfile.value.copy(
            name = name,
            dob = dob,
            avatarRes = avatarRes
        )

        viewModelScope.launch {
            try {
                // 2. Update Firebase Auth (Name)
                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }
                user.updateProfile(profileUpdates).await()
                Log.d("SettingsVM", "Firebase Auth updated")
                
                // 3. Update Firestore
                val userData = hashMapOf(
                    "name" to name,
                    "dob" to dob,
                    "avatarRes" to avatarRes,
                    "email" to user.email
                )
                
                firestore.collection("users").document(user.uid)
                    .set(userData, SetOptions.merge())
                    .await()

                Log.d("SettingsVM", "Firestore update complete for UID: ${user.uid}")
            } catch (e: Exception) {
                Log.e("SettingsVM", "Error in updateProfile: ${e.message}", e)
                // Optionally revert UI state on failure
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _isLoggedOut.value = true
        }
    }
}
