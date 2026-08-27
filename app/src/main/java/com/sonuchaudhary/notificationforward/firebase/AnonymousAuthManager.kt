package com.sonuchaudhary.notificationforward.firebase

import kotlinx.coroutines.tasks.await

/**
 * Ensures the device holds a Firebase Auth identity before any Firestore/Functions call that
 * relies on request.auth.uid in security rules (family membership, pairing). One anonymous
 * identity per install; it is never signed out (pairing is scoped by family membership, not by
 * having a "real" account).
 */
object AnonymousAuthManager {
    suspend fun ensureSignedIn(): String {
        val current = FirebaseModule.auth.currentUser
        if (current != null) return current.uid

        val result = FirebaseModule.auth.signInAnonymously().await()
        return result.user?.uid
            ?: error("Anonymous sign-in succeeded but returned no user")
    }
}
