package com.example.data.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

data class FirebaseMaskedConfig(
    val isConfigured: Boolean,
    val projectId: String,
    val maskedAppId: String,
    val maskedApiKeyStatus: String,
    val appCheckMode: String
)

fun readMaskedFirebaseConfig(context: Context): FirebaseMaskedConfig {
    val options = runCatching {
        FirebaseApp.getApps(context).firstOrNull()?.options
            ?: com.google.firebase.FirebaseOptions.fromResource(context)
    }.getOrNull()

    if (options == null) {
        return FirebaseMaskedConfig(
            isConfigured = false,
            projectId = "Not initialized",
            maskedAppId = "Unavailable",
            maskedApiKeyStatus = "Configured via google-services.json",
            appCheckMode = if (BuildConfig.DEBUG) "Debug App Check Provider" else "Play / reCAPTCHA App Check"
        )
    }

    val appId = options.applicationId.orEmpty()
    val maskedAppId = if (appId.length > 14) {
        "${appId.take(10)}••••${appId.takeLast(6)}"
    } else {
        appId.ifBlank { "Unavailable" }
    }

    val rawKey = options.apiKey.orEmpty()
    val keyStatus = if (rawKey.length > 10) {
        "Configured (${rawKey.take(4)}••••••••${rawKey.takeLast(3)})"
    } else if (rawKey.isNotBlank()) {
        "Configured (masked)"
    } else {
        "Managed by Firebase"
    }

    return FirebaseMaskedConfig(
        isConfigured = appId.isNotBlank(),
        projectId = options.projectId.orEmpty().ifBlank { "Unavailable" },
        maskedAppId = maskedAppId,
        maskedApiKeyStatus = keyStatus,
        appCheckMode = if (BuildConfig.DEBUG) "Debug App Check Provider" else "Recaptcha / App Check Active"
    )
}

