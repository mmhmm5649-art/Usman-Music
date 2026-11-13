package com.usman.musicplayer.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.usman.musicplayer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    fun isFirebaseAvailable(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context.applicationContext).isNotEmpty() ||
                FirebaseApp.initializeApp(context.applicationContext) != null
        } catch (e: Exception) {
            Log.w(TAG, "Firebase unavailable: ${e.message}")
            false
        }
    }

    fun attemptSilentSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (!isFirebaseAvailable(context)) {
            onUnauthenticated()
            return
        }
        val auth = try {
            Firebase.auth
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }
        if (auth.currentUser != null) {
            onAuthSuccess()
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        if (clientId.isBlank()) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthCancelled: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        if (!isFirebaseAvailable(context)) {
            onAuthError("Firebase is not initialized on this device.")
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
            return
        }
        if (clientId.isBlank()) {
            onAuthError("Google Sign-In Web Client ID is empty.")
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Unable to open Google Sign-In dialog from current context.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val credentialManager = CredentialManager.create(activity)
        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        val credentialManager = CredentialManager.create(context.applicationContext)
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    try {
                        if (isFirebaseAvailable(context)) {
                            Firebase.auth.signOut()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase signOut warning: ${e.message}")
                    }
                    onSignOutComplete()
                }
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
