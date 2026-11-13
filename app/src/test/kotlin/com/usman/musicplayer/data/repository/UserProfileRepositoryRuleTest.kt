package com.usman.musicplayer.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.usman.musicplayer.base.FirestoreEmulatorTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncSignedInUserProfile_createsUpdatesAndObservesOwnerProfile() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = UserProfileRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection(UserProfileRepository.COLLECTION_USERS).document(aliceUid).delete().await()
        }

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.syncSignedInUserProfile(
                googleDisplayName = "Alice Google",
                email = ALICE_EMAIL,
                photoUrl = "https://example.com/alice.png"
            )
        }
        assertTrue(created.isSuccess)
        assertEquals("Alice Google", created.getOrNull()?.googleDisplayName)
        assertEquals(ALICE_EMAIL, created.getOrNull()?.email)

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.syncSignedInUserProfile(
                googleDisplayName = "Alice Updated",
                email = ALICE_EMAIL,
                photoUrl = "https://example.com/alice2.png"
            )
        }
        assertTrue(updated.isSuccess)
        val doc = updated.getOrNull()
        assertNotNull(doc)
        assertEquals("Alice Updated", doc?.googleDisplayName)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(aliceUid).first { it?.googleDisplayName == "Alice Updated" }
        }
        assertEquals("Alice Updated", observed?.googleDisplayName)
    }

    @Test
    fun getUserProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = UserProfileRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncSignedInUserProfile(
                googleDisplayName = "Alice Only",
                email = ALICE_EMAIL,
                photoUrl = null
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = UserProfileRepository(firestore)
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getUserProfile(aliceUid) }
        assertTrue(crossRead.isFailure)
        val exception = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(exception)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception?.code)

        var writeFailed = false
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection(UserProfileRepository.COLLECTION_USERS)
                    .document(aliceUid)
                    .update(
                        mapOf(
                            "googleDisplayName" to "Hijacked",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                writeFailed = true
            }
        }
        assertTrue(writeFailed)
    }

    @Test
    fun observeUserProfile_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = UserProfileRepository(firestore)

        var gotPermissionDenied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProfile(aliceUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                gotPermissionDenied = true
            }
        }
        assertTrue(gotPermissionDenied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_profile_test@usman.test"
        const val BOB_EMAIL = "bob_profile_test@usman.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
