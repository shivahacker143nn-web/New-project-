
package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AuthUserInfo(
    val uid: String,
    val displayName: String,
    val email: String,
    val phoneNumber: String,
    val providerId: String
)

sealed class PhoneOtpState {
    data object Idle : PhoneOtpState()
    data object SendingCode : PhoneOtpState()
    data class CodeSent(val verificationId: String, val phoneNumber: String) : PhoneOtpState()
    data object Verifying : PhoneOtpState()
    data class Error(val message: String) : PhoneOtpState()
}

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val credentialManager by lazy { CredentialManager.create(context) }

    private fun getDefaultWebClientId(): String? {
        return try {
            val resourceId = context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )
            if (resourceId != 0) context.getString(resourceId) else null
        } catch (e: Exception) {
            Log.e(TAG, "Google Web Client ID not found", e)
            null
        }
    }

    private val _currentUser = MutableStateFlow<AuthUserInfo?>(mapUser(runCatching { auth.currentUser }.getOrNull()))
    val currentUser: StateFlow<AuthUserInfo?> = _currentUser.asStateFlow()

    private val _phoneOtpState = MutableStateFlow<PhoneOtpState>(PhoneOtpState.Idle)
    val phoneOtpState: StateFlow<PhoneOtpState> = _phoneOtpState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = mapUser(firebaseAuth.currentUser)
    }

    init {
        runCatching { auth.addAuthStateListener(authListener) }
    }

    fun isSignedIn(): Boolean = _currentUser.value != null

    suspend fun attemptSilentGoogleSignIn(): Boolean {
        if (runCatching { auth.currentUser }.getOrNull() != null) return true
        val clientId = getDefaultWebClientId() ?: return false

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(firebaseCred).await()
                _currentUser.value = mapUser(auth.currentUser)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google sign-in skipped: ${e.message}")
            false
        }
    }

    suspend fun signInWithGoogleInteractive(activity: Activity): Result<AuthUserInfo> {
        val clientId = getDefaultWebClientId()
            ?: return Result.failure(
                IllegalStateException(
                    "Google Sign-In configuration missing (default_web_client_id). Check Firebase configuration."
                )
            )

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                val mapped = mapUser(auth.currentUser)
                    ?: return Result.failure(IllegalStateException("Signed in but user profile unavailable."))
                _currentUser.value = mapped
                Result.success(mapped)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type received."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    fun startPhoneNumberVerification(
        activity: Activity,
        phoneNumber: String,
        onAutoVerified: () -> Unit
    ) {
        val cleaned = phoneNumber.trim()
        if (!cleaned.startsWith("+") || cleaned.length < 10) {
            _phoneOtpState.value = PhoneOtpState.Error("Enter phone number in international E.164 format (e.g. +919876543210).")
            return
        }
        _phoneOtpState.value = PhoneOtpState.SendingCode

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(cleaned)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    _phoneOtpState.value = PhoneOtpState.Verifying
                    auth.signInWithCredential(credential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            _phoneOtpState.value = PhoneOtpState.Idle
                            _currentUser.value = mapUser(auth.currentUser)
                            onAutoVerified()
                        } else {
                            _phoneOtpState.value = PhoneOtpState.Error(
                                task.exception?.localizedMessage ?: "OTP verification failed."
                            )
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(TAG, "Phone OTP verification failed", e)
                    _phoneOtpState.value = PhoneOtpState.Error(
                        "${e.localizedMessage ?: "Phone verification failed."} Note: Phone Sign-In must be enabled in Firebase Console > Authentication > Sign-in method."
                    )
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _phoneOtpState.value = PhoneOtpState.CodeSent(verificationId, cleaned)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun verifyPhoneOtpCode(verificationId: String, otpCode: String): Result<AuthUserInfo> {
        val code = otpCode.trim()
        if (code.length < 6) {
            return Result.failure(IllegalArgumentException("Enter the 6-digit OTP verification code."))
        }
        _phoneOtpState.value = PhoneOtpState.Verifying
        return try {
            val cred = PhoneAuthProvider.getCredential(verificationId, code)
            auth.signInWithCredential(cred).await()
            _phoneOtpState.value = PhoneOtpState.Idle
            val mapped = mapUser(auth.currentUser)
                ?: return Result.failure(IllegalStateException("OTP verified but user unavailable."))
            _currentUser.value = mapped
            Result.success(mapped)
        } catch (e: Exception) {
            _phoneOtpState.value = PhoneOtpState.Error(e.localizedMessage ?: "Invalid OTP code.")
            Result.failure(e)
        }
    }

    fun resetPhoneOtpState() {
        _phoneOtpState.value = PhoneOtpState.Idle
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleaned = email.trim()
        if (!cleaned.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        return try {
            auth.sendPasswordResetEmail(cleaned).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(newName: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val req = UserProfileChangeRequest.Builder()
                .setDisplayName(newName.trim())
                .build()
            user.updateProfile(req).await()
            _currentUser.value = mapUser(auth.currentUser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                runCatching { auth.signOut() }
                _currentUser.value = null
                _phoneOtpState.value = PhoneOtpState.Idle
            }
        }
    }

    fun cleanup() {
        runCatching { auth.removeAuthStateListener(authListener) }
    }

    private fun mapUser(user: FirebaseUser?): AuthUserInfo? {
        if (user == null) return null
        val provider = user.providerData.lastOrNull()?.providerId ?: "firebase"
        return AuthUserInfo(
            uid = user.uid,
            displayName = user.displayName?.ifBlank { null } ?: "AI SHU User",
            email = user.email.orEmpty(),
            phoneNumber = user.phoneNumber.orEmpty(),
            providerId = provider
        )
    }

    companion object {
        private const val TAG = "AuthManager"
    }
}
