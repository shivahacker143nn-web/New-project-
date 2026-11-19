package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AssistantRuleEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

data class CloudUserProfile(
    val userId: String = "",
    val assistantName: String = "AI SHU",
    val preferredLanguage: String = "BILINGUAL",
    val responseLength: String = "BALANCED",
    val personalizationNotes: String = ""
)

data class CloudAssistantRule(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val instruction: String = "",
    val isEnabled: Boolean = true,
    val isProtected: Boolean = true
)

class FirestoreSyncRepository private constructor(
    dbProvider: () -> FirebaseFirestore
) {
    private val db: FirebaseFirestore by lazy(dbProvider)

    constructor(db: FirebaseFirestore) : this({ db })

    constructor(context: Context) : this({
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    })

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before syncing with Firestore.")
    }

    suspend fun syncUserProfile(profile: CloudUserProfile): Result<Unit> {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "assistantName" to profile.assistantName.take(40).ifBlank { "AI SHU" },
                "preferredLanguage" to profile.preferredLanguage,
                "responseLength" to profile.responseLength,
                "personalizationNotes" to profile.personalizationNotes.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existingSnap.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.set(payload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeCloudRules(): Flow<List<CloudAssistantRule>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/rules"
        emitAll(
            db.collection("users").document(uid).collection("rules")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        CloudAssistantRule(
                            id = doc.id,
                            userId = doc.getString("userId").orEmpty(),
                            title = doc.getString("title").orEmpty(),
                            instruction = doc.getString("instruction").orEmpty(),
                            isEnabled = doc.getBoolean("isEnabled") ?: true,
                            isProtected = doc.getBoolean("isProtected") ?: true
                        )
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertRule(rule: AssistantRuleEntity): Result<Unit> {
        val uid = requireUserId()
        val docId = "rule_${rule.id}"
        val docRef = db.collection("users").document(uid).collection("rules").document(docId)
        return try {
            val snap = docRef.get().await()
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to rule.title.take(80).ifBlank { "Rule" },
                "instruction" to rule.instruction.take(400),
                "isEnabled" to rule.isEnabled,
                "isProtected" to rule.isProtected,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!snap.exists()) {
                payload["createdAt"] = FieldValue.serverTimestamp()
                docRef.set(payload).await()
            } else {
                docRef.set(payload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRule(ruleId: Long): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("rules").document("rule_$ruleId")
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
