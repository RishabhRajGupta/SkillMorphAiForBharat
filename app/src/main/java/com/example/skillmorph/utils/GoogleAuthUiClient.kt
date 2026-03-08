
package com.example.skillmorph.utils

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption

object GoogleAuthUiClient {

    private const val WEB_CLIENT_ID = "119333691668-1hug4q4e6231mond38sgl5jhgvjv1239.apps.googleusercontent.com"

    fun getGoogleIdOption(): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
    }

    fun getCredentialRequest(googleIdOption: GetGoogleIdOption): GetCredentialRequest {
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    fun getCredentialManager(context: Context): CredentialManager {
        return CredentialManager.create(context)
    }
}
