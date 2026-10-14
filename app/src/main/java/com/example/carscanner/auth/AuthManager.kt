package com.example.carscanner.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.carscanner.domain.AppUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

class AuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // Looked up by name so the project compiles even when google-services.json is missing.
    private val webClientId: String? = listOf("default_web_client_id", "web_client_id")
        .firstNotNullOfOrNull { name ->
            appContext.resources.getIdentifier(name, "string", appContext.packageName)
                .takeIf { it != 0 }
                ?.let { appContext.getString(it) }
        }

    val googleAvailable: Boolean = webClientId != null && FirebaseApp.getApps(appContext).isNotEmpty()

    private val auth: FirebaseAuth? = if (googleAvailable) FirebaseAuth.getInstance() else null
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    private val guestUser = AppUser(uid = "local", displayName = "Driver", email = null, photoUrl = null)
    private val guest = MutableStateFlow(if (prefs.getBoolean(KEY_GUEST, false)) guestUser else null)

    private fun FirebaseUser.toAppUser() = AppUser(uid, displayName, email, photoUrl?.toString())

    private val googleUsers: Flow<AppUser?> = run {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            flowOf<AppUser?>(null)
        } else {
            callbackFlow<AppUser?> {
                val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAppUser()) }
                firebaseAuth.addAuthStateListener(listener)
                awaitClose { firebaseAuth.removeAuthStateListener(listener) }
            }
        }
    }

    val user: Flow<AppUser?> = combine(googleUsers, guest) { google, local -> google ?: local }

    val currentUser: AppUser? get() = auth?.currentUser?.toAppUser() ?: guest.value

    fun continueAsGuest() {
        prefs.edit { putBoolean(KEY_GUEST, true) }
        guest.value = guestUser
    }

    /** [activityContext] must be an Activity context so the Google sheet can show. */
    suspend fun signIn(activityContext: Context): SignInResult {
        val firebaseAuth = auth
        val clientId = webClientId
        if (firebaseAuth == null || clientId == null) {
            return SignInResult.Failure("Google sign-in isn't set up in this build. Continue as guest, or add google-services.json (see README).")
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = credentialManager.getCredential(activityContext, request).credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                SignInResult.Success
            } else {
                SignInResult.Failure("Unexpected sign-in response.")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            SignInResult.Failure("Google sign-in failed. Check your SHA-1 key in Firebase. (${e.type})")
        } catch (e: Exception) {
            SignInResult.Failure("Sign-in failed: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    suspend fun signOut() {
        auth?.let {
            it.signOut()
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        }
        prefs.edit { putBoolean(KEY_GUEST, false) }
        guest.value = null
    }

    private companion object {
        const val KEY_GUEST = "guest"
    }
}
