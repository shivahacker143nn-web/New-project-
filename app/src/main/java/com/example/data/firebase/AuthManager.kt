
package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
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

            if (resourceId != 0) {
                context.getString(resourceId).takeIf { it.isNotBlank() }
            } else {
                Log.e(TAG, "default_web_client_id resource not found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Web Client ID not found", e)
            null
        }
    }

    private val _currentUser =
        MutableStateFlow<AuthUserInfo?>(mapUser(runCatching { auth.currentUser }.getOrNull()))

    val currentUser: StateFlow<AuthUserInfo?> = _currentUser.asStateFlow()

    private val _phoneOtpState =
        MutableStateFlow<PhoneOtpState>(PhoneOtpState.Idle)

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

            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken =
                    GoogleIdTokenCredential.createFrom(credential.data).idToken

                val firebaseCred =
                    GoogleAuthProvider.getCredential(idToken, null)

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

    suspend fun signInWithGoogleInteractive(
        activity: Activity
    ): Result<AuthUserInfo> {

        val clientId = getDefaultWebClientId()
            ?: return Result.failure(
                IllegalStateException(
                    "Google Sign-In configuration missing. Check Firebase google-services.json and Web Client ID."
                )
            )

        val signInOption =
            GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            if (credential !is CustomCredential ||
                credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(
                    IllegalStateException("Invalid Google credential")
                )
            }

            val idToken =
                GoogleIdTokenCredential.createFrom(credential.data).idToken

            val firebaseCredential =
                GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(firebaseCredential).await()

            val user = mapUser(auth.currentUser)
                ?: return Result.failure(
                    IllegalStateException("User information unavailable")
                )

            _currentUser.value = user
            Result.success(user)

        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String
    ) {
        _phoneOtpState.value = PhoneOtpState.SendingCode

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    _phoneOtpState.value = PhoneOtpState.Verifying
                    signInWithPhoneCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _phoneOtpState.value =
                        PhoneOtpState.Error(e.message ?: "Phone verification failed")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _phoneOtpState.value =
                        PhoneOtpState.CodeSent(verificationId, phoneNumber)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun verifyPhoneOtp(
        verificationId: String,
        code: String
    ): Result<AuthUserInfo> {
        _phoneOtpState.value = PhoneOtpState.Verifying

        return try {
            val credential =
                PhoneAuthProvider.getCredential(verificationId, code)

            val result = auth.signInWithCredential(credential).await()

            val user = mapUser(result.user)
                ?: throw IllegalStateException("User information unavailable")

            _currentUser.value = user
            _phoneOtpState.value = PhoneOtpState.Idle

            Result.success(user)
        } catch (e: Exception) {
            _phoneOtpState.value =
                PhoneOtpState.Error(e.message ?: "OTP verification failed")

            Result.failure(e)
        }
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                _currentUser.value = mapUser(result.user)
                _phoneOtpState.value = PhoneOtpState.Idle
            }
            .addOnFailureListener { e ->
                _phoneOtpState.value =
                    PhoneOtpState.Error(e.message ?: "Phone sign-in failed")
            }
    }

    suspend fun updateDisplayName(name: String): Result<Unit> {
        return try {
            val request = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()

            auth.currentUser?.updateProfile(request)?.await()
                ?: throw IllegalStateException("User not signed in")

            _currentUser.value = mapUser(auth.currentUser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()

            runCatching {
                credentialManager.clearCredentialState(
                    androidx.credentials.ClearCredentialStateRequest()
                )
            }

            _currentUser.value = null
            _phoneOtpState.value = PhoneOtpState.Idle

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapUser(user: FirebaseUser?): AuthUserInfo? {
        if (user == null) return null

        return AuthUserInfo(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty(),
            phoneNumber = user.phoneNumber.orEmpty(),
            providerId = user.providerData
                .lastOrNull { it.providerId != "firebase" }
                ?.providerId.orEmpty()
        )
    }

    companion object {
        private const val TAG = "AuthManager"
    }
}
