package egypt.shortdrama.top

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * إشعارات Firebase Cloud Messaging.
 * - لو التطبيق في الخلفية، الإشعار بيظهر تلقائياً من Firebase.
 * - لو مفتوح، بنعرضه هنا. لو الرسالة فيها data "url" بيفتح الصفحة دي جوه التطبيق.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Repo.saveFcmToken()
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: getString(R.string.app_name)
        val body = msg.notification?.body ?: msg.data["body"] ?: return
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            msg.data["url"]?.let { putExtra("url", it) }
        }
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), n) }
    }
}
