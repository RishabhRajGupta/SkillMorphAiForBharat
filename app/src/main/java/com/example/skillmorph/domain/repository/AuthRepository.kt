
package com.example.skillmorph.domain.repository

import androidx.credentials.GetCredentialResponse
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

/**
 * A repository for handling all authentication-related operations.
 */
interface AuthRepository {

    /**
     * A flow that emits the currently logged-in user or null if logged out.
     */
    val currentUserFlow: Flow<FirebaseUser?>

    /**
     * Signs in the user with Google using the credential from Credential Manager.
     * @param result The response from the Credential Manager API.
     * @return A Flow that emits the result of the sign-in attempt.
     */
    suspend fun signInWithGoogle(result: GetCredentialResponse): Flow<Result<FirebaseUser>>

    /**
     * Checks if a user is currently logged in.
     * @return True if a user is logged in, false otherwise.
     */
    fun isUserLoggedIn(): Boolean

    /**
     * Gets the currently logged-in user.
     * @return The FirebaseUser object if logged in, null otherwise.
     */
    fun getLoggedInUser(): FirebaseUser?

    /**
     * Signs out the current user.
     */
    suspend fun signOut()
}
