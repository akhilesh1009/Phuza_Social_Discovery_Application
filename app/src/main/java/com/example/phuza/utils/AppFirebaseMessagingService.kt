package com.example.phuza.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.phuza.PubGolfScorecardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val ACTION_NEW_MESSAGE = "com.example.phuza.NEW_MESSAGE"
        const val EXTRA_PEER_UID    = "peerUid"
        const val EXTRA_CHAT_ID     = "chatId"
        private const val TAG       = "AppFMS"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "onNewToken: No user logged in; skipping token save")
            return
        }

        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(mapOf("createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Log.d(TAG, "Token saved for user=$uid token=$token")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save token", e)
                }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while saving token", se)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "onMessageReceived: from=${message.from} data=${message.data}")

        val type  = message.data["type"]
        val title = message.notification?.title ?: message.data["title"] ?: "Notification"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""

        // Permission check for Android 13+
        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!canPost) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping foreground notification")
            return
        }

        when (type) {
            "chat_message" -> {
                handleChatMessageNotification(message, title, body)
            }
            "pubgolf_invite" -> {
                handlePubGolfInviteNotification(message, title, body)
            }
            else -> {
                // Fallback: generic notification, no special routing
                try {
                    NotificationUtils.show(this, title, body)
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException while showing notification", se)
                }
            }
        }
    }

    private fun handleChatMessageNotification(
        message: RemoteMessage,
        title: String,
        body: String
    ) {
        // Keys must match backend FCM data payload
        val peerUid = message.data["peerUid"] ?: message.data["fromUid"]
        val chatId  = message.data["chatId"]

        // ---- Local broadcast for in-app updates ----
        if (!peerUid.isNullOrEmpty()) {
            val intent = Intent(ACTION_NEW_MESSAGE).apply {
                putExtra(EXTRA_PEER_UID, peerUid)
                if (!chatId.isNullOrEmpty()) {
                    putExtra(EXTRA_CHAT_ID, chatId)
                }
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "Sent local broadcast for new message from peer=$peerUid chatId=$chatId")
        } else {
            Log.w(
                TAG,
                "handleChatMessageNotification: no peerUid/fromUid in data payload; cannot broadcast"
            )
        }

        // Simple foreground notification (no special tap routing yet)
        try {
            NotificationUtils.show(this, title, body)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while showing notification", se)
        }
    }

    private fun handlePubGolfInviteNotification(
        message: RemoteMessage,
        title: String,
        body: String
    ) {
        val gameId   = message.data["gameId"]
        val inviteId = message.data["inviteId"]

        if (gameId.isNullOrEmpty() || inviteId.isNullOrEmpty()) {
            Log.w(TAG, "pubgolf_invite missing gameId or inviteId in data payload")
            return
        }

        // Intent to open the scorecard screen, which will show the join dialog
        val tapIntent = Intent(this, PubGolfScorecardActivity::class.java).apply {
            putExtra(PubGolfScorecardActivity.EXTRA_GAME_ID, gameId)
            putExtra(PubGolfScorecardActivity.EXTRA_INVITE_ID, inviteId)
            putExtra(
                PubGolfScorecardActivity.EXTRA_FROM_INVITE_NOTIFICATION,
                true
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            flags
        )

        try {
            // Use named argument so the Intent is passed as pendingIntent, not as the ID
            NotificationUtils.show(
                context = this,
                title = title,
                text = body,
                pendingIntent = pendingIntent
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while showing pubgolf invite notification", se)
        }
    }
}

//https://developer.android.com/reference/android/app/PendingIntent