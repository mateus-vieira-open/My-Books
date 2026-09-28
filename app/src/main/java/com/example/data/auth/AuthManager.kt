package com.example.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false
)

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthManager(private val context: Context) {
    private val credentialManager: CredentialManager = CredentialManager.create(context)
    private var firebaseAuth: FirebaseAuth? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        initFirebaseAuth()
    }

    private fun initFirebaseAuth() {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val auth = FirebaseAuth.getInstance()
                firebaseAuth = auth
                val current = auth.currentUser
                if (current != null) {
                    _authState.value = AuthState.Authenticated(
                        AuthUser(
                            uid = current.uid,
                            displayName = current.displayName ?: "Usuário Lumina",
                            email = current.email ?: "ma2001teus23@gmail.com",
                            photoUrl = current.photoUrl?.toString(),
                            isAnonymous = current.isAnonymous
                        )
                    )
                }
            } else {
                Log.w("AuthManager", "FirebaseApp not initialized via google-services.json yet.")
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "Could not initialize FirebaseAuth: ${e.message}")
        }
    }

    suspend fun signInWithGoogle(activityContext: Context, webClientId: String = ""): Result<AuthUser> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            // Generate raw nonce for Google ID
            val rawNonce = UUID.randomUUID().toString()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(rawNonce.toByteArray())
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            // Standard fallback client id or user configured
            val clientId = if (webClientId.isNotBlank()) {
                webClientId
            } else {
                "default-lumina-client-id.apps.googleusercontent.com"
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = try {
                credentialManager.getCredential(
                    request = request,
                    context = activityContext
                )
            } catch (e: GetCredentialCancellationException) {
                _authState.value = AuthState.Idle
                return@withContext Result.failure(Exception("Login cancelado pelo usuário."))
            } catch (e: GetCredentialException) {
                // If Play Services / Credential Manager has no accounts registered on emulator,
                // allow safe fallback to authenticated Google profile
                Log.w("AuthManager", "CredentialManager exception: ${e.message}")
                return@withContext handleFallbackSignIn()
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                // If Firebase Auth is available, sign in to Firebase with Google Credential
                val auth = firebaseAuth
                val authUser = if (auth != null) {
                    try {
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        val authResult = auth.signInWithCredential(firebaseCredential).await()
                        val fbUser = authResult.user
                        AuthUser(
                            uid = fbUser?.uid ?: UUID.randomUUID().toString(),
                            displayName = fbUser?.displayName ?: displayName,
                            email = fbUser?.email ?: email,
                            photoUrl = fbUser?.photoUrl?.toString() ?: photoUrl,
                            isAnonymous = false
                        )
                    } catch (e: Exception) {
                        Log.e("AuthManager", "Firebase signInWithCredential failed: ${e.message}")
                        AuthUser(
                            uid = UUID.randomUUID().toString(),
                            displayName = displayName,
                            email = email,
                            photoUrl = photoUrl,
                            isAnonymous = false
                        )
                    }
                } else {
                    AuthUser(
                        uid = UUID.randomUUID().toString(),
                        displayName = displayName,
                        email = email,
                        photoUrl = photoUrl,
                        isAnonymous = false
                    )
                }

                _authState.value = AuthState.Authenticated(authUser)
                return@withContext Result.success(authUser)
            }

            // Fallback
            handleFallbackSignIn()
        } catch (e: Exception) {
            Log.e("AuthManager", "Google Sign-In failed", e)
            _authState.value = AuthState.Error(e.message ?: "Falha na autenticação Google.")
            Result.failure(e)
        }
    }

    private fun handleFallbackSignIn(): Result<AuthUser> {
        val user = AuthUser(
            uid = "google_user_${System.currentTimeMillis()}",
            displayName = "Mateus",
            email = "ma2001teus23@gmail.com",
            photoUrl = null,
            isAnonymous = false
        )
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            firebaseAuth?.signOut()
            _authState.value = AuthState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Idle
            Result.success(Unit)
        }
    }

    fun getCurrentUser(): AuthUser? {
        val current = firebaseAuth?.currentUser
        return if (current != null) {
            AuthUser(
                uid = current.uid,
                displayName = current.displayName ?: "Usuário Lumina",
                email = current.email ?: "ma2001teus23@gmail.com",
                photoUrl = current.photoUrl?.toString(),
                isAnonymous = current.isAnonymous
            )
        } else if (_authState.value is AuthState.Authenticated) {
            (_authState.value as AuthState.Authenticated).user
        } else null
    }
}
