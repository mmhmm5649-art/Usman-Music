package com.usman.musicplayer.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.usman.musicplayer.R
import com.usman.musicplayer.data.firebase.OperationType
import com.usman.musicplayer.data.firebase.handleFirestoreError
import com.usman.musicplayer.model.UserProfileDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserProfileRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: IllegalStateException) {
            null
        }
        return currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfileDocument?> {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val docRef = db.collection(COLLECTION_USERS).document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfileDocument::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val currentUid = try {
                    Firebase.auth.currentUser?.uid
                } catch (_: IllegalStateException) {
                    null
                }
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && currentUid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfileDocument?> {
        val docRef = db.collection(COLLECTION_USERS).document(userId)
        return try {
            val snapshot = docRef.get().await()
            val profile = if (snapshot.exists()) {
                snapshot.toObject(
                    UserProfileDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncSignedInUserProfile(
        googleDisplayName: String?,
        email: String?,
        photoUrl: String?
    ): Result<UserProfileDocument?> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection(COLLECTION_USERS).document(uid)

        return try {
            val existingSnapshot = docRef.get().await()
            val cleanGoogleName = googleDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
            val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val cleanPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

            if (!existingSnapshot.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "googleDisplayName" to cleanGoogleName,
                    "email" to cleanEmail,
                    "photoUrl" to cleanPhotoUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>

                docRef.set(createPayload).await()
            } else {
                val existingDoc = existingSnapshot.toObject(
                    UserProfileDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val updates = mutableMapOf<String, Any>()
                if (cleanGoogleName != null && cleanGoogleName != existingDoc?.googleDisplayName) {
                    updates["googleDisplayName"] = cleanGoogleName
                }
                if (cleanEmail != null && cleanEmail != existingDoc?.email) {
                    updates["email"] = cleanEmail
                }
                if (cleanPhotoUrl != null && cleanPhotoUrl != existingDoc?.photoUrl) {
                    updates["photoUrl"] = cleanPhotoUrl
                }

                if (updates.isNotEmpty()) {
                    updates["updatedAt"] = FieldValue.serverTimestamp()
                    docRef.update(updates).await()
                }
            }

            val refreshed = docRef.get().await().toObject(
                UserProfileDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_USERS = "users"
    }
}
