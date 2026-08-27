package com.sonuchaudhary.notificationforward.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sonuchaudhary.notificationforward.firebase.FirebaseModule
import com.sonuchaudhary.notificationforward.settings.RoleStore
import kotlinx.coroutines.tasks.await

/**
 * Periodic Firestore poll for pending commands targeting this device — the fallback command
 * channel for sideloaded installs on devices without reliable Google Play Services / FCM
 * delivery. Phase 4 (remote app control) will dispatch each pending command through a
 * CommandHandler and ack its status back to Firestore; for Phase 1 this just fetches and
 * confirms the channel works end to end.
 */
class CommandPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val roleStore = RoleStore(applicationContext)
        val familyId = roleStore.familyId ?: return Result.success()

        return try {
            FirebaseModule.firestore.collection("families").document(familyId)
                .collection("commands")
                .whereEqualTo("targetDeviceId", roleStore.deviceId)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
