package com.usman.musicplayer.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AuthGateDestination {
    HOME_AUTHENTICATED,
    HOME_GUEST
}

@IgnoreExtraProperties
data class UserProfileDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val googleDisplayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class UserProfileState(
    val gateDestination: AuthGateDestination = AuthGateDestination.HOME_GUEST,
    val isSignedIn: Boolean = false,
    val isGuestMode: Boolean = true,
    val uid: String? = null,
    val googleDisplayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isAuthInProgress: Boolean = false,
    val authError: String? = null,
    val sessionNotice: String? = null
)
