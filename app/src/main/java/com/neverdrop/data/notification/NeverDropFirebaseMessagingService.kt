package com.neverdrop.data.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NeverDropFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("NeverDropFCM", "New FCM token: $token")
        // Store token for future server-side push notifications
        val prefs = getSharedPreferences("neverdrop_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "NeverDrop"
        val body = data["body"] ?: message.notification?.body ?: ""

        val notification = android.app.Notification.Builder(this, NotificationChannelManager.CHANNEL_MEDIUM)
            .setSmallIcon(com.neverdrop.R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
