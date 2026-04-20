package com.neverdrop.data.google

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleUser(
    val email: String,
    val displayName: String?,
    val idToken: String
)

class GoogleAuthManager(private val context: Context) {

    companion object {
        const val WEB_CLIENT_ID =
            "204869979080-qvojotrvfhhhamourktfcchki2e97and.apps.googleusercontent.com"

        private val SCOPES = listOf(
            GmailScopes.GMAIL_READONLY,
            CalendarScopes.CALENDAR_READONLY
        )
    }

    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    val isSignedIn: Boolean get() = _currentUser.value != null

    suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val googleIdTokenCredential =
                GoogleIdTokenCredential.createFrom(result.credential.data)

            val user = GoogleUser(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName,
                idToken = googleIdTokenCredential.idToken
            )

            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        _currentUser.value = null
    }

    fun getAccountCredential(): GoogleAccountCredential? {
        val user = _currentUser.value ?: return null
        return GoogleAccountCredential.usingOAuth2(context, SCOPES)
            .setBackOff(ExponentialBackOff())
            .also { it.selectedAccountName = user.email }
    }
}
