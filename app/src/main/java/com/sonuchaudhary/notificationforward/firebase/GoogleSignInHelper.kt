package com.sonuchaudhary.notificationforward.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.sonuchaudhary.notificationforward.R
import kotlinx.coroutines.tasks.await

/**
 * Parent-only sign-in. The child device never sees this — it stays fully anonymous (see
 * AnonymousAuthManager), no login screen at all, exactly as before.
 *
 * Uses Credential Manager (Google's current recommended API, not the deprecated
 * GoogleSignInClient) to get a Google ID token, then tries to *link* it to the device's existing
 * anonymous Firebase user rather than replacing it — so a fresh install's anonymous UID becomes
 * the parent's permanent UID instead of being thrown away. If that Google account already
 * belongs to a different Firebase user (the parent signing in again after a reinstall, or on a
 * second device), linking fails with a collision error and we fall back to a plain sign-in that
 * adopts the existing account — which is exactly the recovery behavior a "real account" is for.
 */
object GoogleSignInHelper {
    suspend fun signIn(context: Context): Result<Unit> {
        return try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(context).getCredential(context, request)

            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return Result.failure(IllegalStateException("Unexpected credential type from Credential Manager"))
            }
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

            val currentUser = FirebaseModule.auth.currentUser
            if (currentUser != null && currentUser.isAnonymous) {
                try {
                    currentUser.linkWithCredential(firebaseCredential).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    FirebaseModule.auth.signInWithCredential(firebaseCredential).await()
                }
            } else {
                FirebaseModule.auth.signInWithCredential(firebaseCredential).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
