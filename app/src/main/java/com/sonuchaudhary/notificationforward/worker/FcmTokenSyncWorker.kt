package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.data.FamilyRepository
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import com.sonuchaudhary.notificationforward.settings.RoleStore
import kotlinx.coroutines.tasks.await

/** Pushes this device's current FCM token (and a presence heartbeat) to its Firestore device doc. */
class FcmTokenSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val roleStore = RoleStore(applicationContext)
        val familyId = roleStore.familyId ?: return Result.success()

        return try {
            val token = roleStore.lastFcmToken ?: FirebaseModule.messaging.token.await()
            FamilyRepository(applicationContext).heartbeat(familyId, roleStore.deviceId, token)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
