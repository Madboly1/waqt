package com.example.glassproductivity

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Optional Google sign-in used only for the personal Drive backup.
 * The app works fully without it, and sign-out never touches local data.
 */
class GoogleAccountManager(private val activity: Activity) {

    companion object {
        /**
         * Web OAuth client ID from the WAQT Google Cloud project. Required by
         * Credential Manager as the server client ID. Not a secret.
         * TODO(cloud-setup): fill in once the OAuth client exists.
         */
        const val SERVER_CLIENT_ID: String = "668845299175-bmcf43kph5uvncj106aj5kfh3od3bflk.apps.googleusercontent.com"
        val configured: Boolean get() = SERVER_CLIENT_ID.isNotBlank()

        private const val PREFS = "waqt_productivity_prefs"
        private const val KEY_GOOGLE_EMAIL = "waqt_google_email"
        private const val KEY_GOOGLE_NAME = "waqt_google_name"
    }

    data class GoogleAccount(val email: String, val name: String?)

    private val credentialManager = CredentialManager.create(activity)

    fun current(context: Context): GoogleAccount? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_GOOGLE_EMAIL, null) ?: return null
        return GoogleAccount(email, prefs.getString(KEY_GOOGLE_NAME, null))
    }

    suspend fun signIn(context: Context): GoogleAccount {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(activity, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        val account = GoogleAccount(credential.id, credential.displayName)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_GOOGLE_EMAIL, account.email)
            .putString(KEY_GOOGLE_NAME, account.name ?: "")
            .apply()
        return account
    }

    /** Signs out only. Local data and previous Drive backups stay untouched. */
    suspend fun signOut(context: Context) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_NAME)
            .apply()
    }
}
