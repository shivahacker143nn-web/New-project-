package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.CloudUserProfile
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.local.AssistantRuleEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncUserProfile_authenticatedOwner_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.syncUserProfile(
                CloudUserProfile(
                    assistantName = "AISHU",
                    preferredLanguage = "BILINGUAL",
                    responseLength = "BALANCED",
                    personalizationNotes = "Prefer concise Telugu replies"
                )
            )
        }
        assertTrue("Expected profile sync to succeed: ${result.exceptionOrNull()}", result.isSuccess)
    }

    @Test
    fun upsertAndObserveRules_authenticatedOwner_emitsSavedRule() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore)
        val ruleId = System.currentTimeMillis()
        val rule = AssistantRuleEntity(
            id = ruleId,
            title = "Truthfulness Rule",
            instruction = "Always answer honestly and never fabricate.",
            isEnabled = true,
            isProtected = true
        )

        val upsertRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.upsertRule(rule) }
        assertTrue("Expected rule upsert to succeed: ${upsertRes.exceptionOrNull()}", upsertRes.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCloudRules().first { list -> list.any { it.id == "rule_$ruleId" } }
        }
        assertTrue(observed.any { it.title == "Truthfulness Rule" })
    }

    @Test
    fun crossUserRuleRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreSyncRepository(firestore)
        val ruleId = 9001L
        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertRule(
                AssistantRuleEntity(
                    id = ruleId,
                    title = "Alice Private Rule",
                    instruction = "Private instruction",
                    isEnabled = true,
                    isProtected = true
                )
            )
        }
        assertTrue(res.isSuccess)

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("rules").document("rule_$ruleId")
                    .get().await()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's rule")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun observeCloudRules_unauthenticatedUser_failsBeforeQuery() = runBlocking {
        auth.signOut()
        val repo = FirestoreSyncRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCloudRules().first()
            }
            fail("Expected failure when unauthenticated")
        } catch (e: Exception) {
            // Either IllegalStateException from requireUserId() or PERMISSION_DENIED is valid
            assertTrue(e is IllegalStateException || e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    @Test
    fun directUnauthenticatedFirestoreRead_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document("some_uid")
                    .collection("rules").get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated Firestore read")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) { "Expected FirebaseFirestoreException, got $e" }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_aishu@test.com"
        const val BOB_EMAIL = "bob_aishu@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
