package com.example.carscanner.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.carscanner.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthManager"

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

class AuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(appContext)

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val user: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            Log.d(
                TAG,
                "Auth state changed. Signed in: ${firebaseAuth.currentUser != null}"
            )
            trySend(firebaseAuth.currentUser)
        }

        auth.addAuthStateListener(listener)

        awaitClose {
            auth.removeAuthStateListener(listener)
            Log.d(TAG, "Auth state listener removed.")
        }
    }

    suspend fun signIn(activityContext: Context): SignInResult {
        Log.d(TAG, "Starting Google sign-in.")

        return try {
            val webClientId = appContext.getString(
                R.string.default_web_client_id
            )

            Log.d(
                TAG,
                "Web client ID resource loaded: ${webClientId.isNotBlank()}"
            )

            val option = GetSignInWithGoogleOption.Builder(
                webClientId
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            Log.d(TAG, "Requesting Google credential.")

            val credential = credentialManager
                .getCredential(activityContext, request)
                .credential

            Log.d(
                TAG,
                "Credential received. Type: ${credential.type}"
            )

            if (
                credential is CustomCredential &&
                credential.type ==
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Log.d(TAG, "Google ID token credential identified.")

                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)

                val idToken = googleCredential.idToken

                Log.d(
                    TAG,
                    "Google ID token parsed. Token present: ${idToken.isNotBlank()}"
                )

                if (idToken.isBlank()) {
                    Log.e(TAG, "Google returned an empty ID token.")
                    return SignInResult.Failure(
                        "Google sign-in failed: ID token is empty."
                    )
                }

                Log.d(TAG, "Creating Firebase Google credential.")

                val firebaseCredential =
                    GoogleAuthProvider.getCredential(idToken, null)

                Log.d(TAG, "Starting Firebase authentication.")

                val authResult = auth
                    .signInWithCredential(firebaseCredential)
                    .await()

                val firebaseUser = authResult.user

                if (firebaseUser != null) {
                    Log.i(
                        TAG,
                        "Firebase sign-in successful. " +
                                "UID present: ${firebaseUser.uid.isNotBlank()}, " +
                                "Email present: ${firebaseUser.email != null}, " +
                                "Provider: ${firebaseUser.providerId}"
                    )
                    SignInResult.Success
                } else {
                    Log.e(
                        TAG,
                        "Firebase authentication completed without a user."
                    )
                    SignInResult.Failure(
                        "Firebase sign-in failed: No user returned."
                    )
                }
            } else {
                Log.e(
                    TAG,
                    "Unexpected credential response. " +
                            "Type: ${credential.type}"
                )

                SignInResult.Failure(
                    "Unexpected sign-in response: ${credential.type}"
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "Google sign-in cancelled by the user.")
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(
                TAG,
                "Credential Manager error. " +
                        "Type: ${e.type}, Message: ${e.message}",
                e
            )

            SignInResult.Failure(
                "Google sign-in failed: ${e.type}"
            )
        } catch (e: FirebaseAuthException) {
            Log.e(
                TAG,
                "Firebase authentication error. " +
                        "Code: ${e.errorCode}, Message: ${e.message}",
                e
            )

            SignInResult.Failure(
                "Firebase sign-in failed: ${e.errorCode}"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected sign-in error. " +
                        "Class: ${e.javaClass.simpleName}, " +
                        "Message: ${e.message}",
                e
            )

            SignInResult.Failure(
                "Sign-in failed: " +
                        (e.localizedMessage ?: "Unknown error")
            )
        }
    }

    suspend fun signOut() {
        Log.d(TAG, "Starting sign-out.")

        try {
            auth.signOut()
            Log.i(TAG, "Firebase sign-out successful.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-out failed.", e)
            throw e
        }

        try {
            credentialManager.clearCredentialState(
                ClearCredentialStateRequest()
            )
            Log.d(TAG, "Credential Manager state cleared.")
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not clear Credential Manager state.",
                e
            )
        }
    }
}