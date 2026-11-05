package egypt.shortdrama.top

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.messaging.FirebaseMessaging

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                getString(R.string.notif_channel_id),
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        MobileAds.initialize(this) {}
        // كل الأجهزة مشتركة في topic "all" علشان الأدمن يبعت إشعار للكل من Firebase Console
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnFailureListener { Log.w(TAG, "FCM topic subscribe failed", it) }
        }.onFailure { Log.w(TAG, "Firebase not configured", it) }
    }

    companion object { const val TAG = "EgyptDrama" }
}
