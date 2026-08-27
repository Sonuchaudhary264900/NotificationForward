package com.sonuchaudhary.notificationforward.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging

/** Thin lazy-singleton access points, mirroring the project's existing HttpClientProvider pattern. */
object FirebaseModule {
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    val messaging: FirebaseMessaging by lazy { FirebaseMessaging.getInstance() }
}
