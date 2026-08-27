package com.sonuchaudhary.notificationforward.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sonuchaudhary.notificationforward.settings.RoleStore
import com.sonuchaudhary.notificationforward.worker.WorkerScheduler

/**
 * Scaffold for the parent -> child command push channel. Phase 4 (remote app control) will
 * dispatch by `message.data["type"]` into a CommandHandler; for now, a push just nudges
 * CommandPollWorker to run immediately so the push path and the Play-Services-less poll
 * fallback converge on the same Firestore state.
 */
class FcmMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        RoleStore(applicationContext).lastFcmToken = token
        WorkerScheduler.enqueueFcmTokenSync(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        WorkerScheduler.enqueueCommandPollNow(applicationContext)
    }
}
