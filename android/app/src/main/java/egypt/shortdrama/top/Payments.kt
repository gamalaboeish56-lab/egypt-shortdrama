package egypt.shortdrama.top

import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * تجهيز Paymob (يتفعل لما Merchant ID 1248669 يتفعل).
 *
 * الفكرة: التطبيق مش بيشيل أي مفتاح سري. بيكلم السيرفر (Vercel function في paymob-later/)
 * اللي بيعمل "Payment Intention" ويرجع checkout URL. المستخدم يدفع (كارت أو محفظة) في صفحة Paymob،
 * والـ webhook على السيرفر هو اللي بيضيف العملات في Firestore بعد التأكد من HMAC.
 *
 * للتفعيل: gradle.properties -> PAYMOB_ENABLED=true  و PAYMOB_BACKEND_URL
 */
object Paymob {
    val enabled get() = BuildConfig.PAYMOB_ENABLED

    /** method = "card" أو "wallet". بيرجع رابط صفحة الدفع */
    suspend fun createCheckout(user: FirebaseUser, plan: Plan, method: String): Uri = withContext(Dispatchers.IO) {
        val idToken = user.getIdToken(false).await().token
            ?: error("no id token")
        val conn = (URL("${BuildConfig.PAYMOB_BACKEND_URL}/intention").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        conn.outputStream.use {
            it.write(JSONObject().put("planId", plan.id).put("method", method).toString().toByteArray())
        }
        val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) error("Paymob backend ${conn.responseCode}: $body")
        Uri.parse(JSONObject(body).getString("checkoutUrl"))
    }
}
