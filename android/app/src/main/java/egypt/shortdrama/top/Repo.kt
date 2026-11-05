package egypt.shortdrama.top

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/** بيانات المستخدم من users/{uid} */
data class UserDoc(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val coins: Long = 0,
    val plan: String? = null,
    val banned: Boolean = false,
)

/**
 * كل التعامل مع Firestore. الكولكشنز حسب المتطلبات:
 *  users, subscriptions, coins, videos  (+ admins لصلاحيات لوحة التحكم)
 */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = runCatching { auth.currentUser }.getOrNull()

    /** إنشاء users/{uid} أول مرة (coins=0, banned=false) وتحديث البيانات العامة بعد كده */
    fun ensureUserDoc(user: FirebaseUser, onDone: (Throwable?) -> Unit = {}) {
        val ref = db.collection("users").document(user.uid)
        ref.get().addOnSuccessListener { snap ->
            val profile = mapOf(
                "name" to (user.displayName ?: ""),
                "email" to (user.email ?: ""),
                "phone" to (user.phoneNumber ?: ""),
                "platform" to "android ${Build.VERSION.RELEASE}",
                "lastSeen" to FieldValue.serverTimestamp(),
            )
            val task = if (snap.exists()) {
                ref.update(profile)
            } else {
                ref.set(profile + mapOf(
                    "uid" to user.uid,
                    "coins" to 0,
                    "plan" to null,
                    "banned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                ))
            }
            task.addOnSuccessListener { saveFcmToken(); onDone(null) }
                .addOnFailureListener { onDone(it) }
        }.addOnFailureListener { onDone(it) }
    }

    fun saveFcmToken() {
        val uid = currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
        }
    }

    fun listenUser(onChange: (UserDoc?) -> Unit): ListenerRegistration? {
        val uid = currentUser?.uid ?: return null
        return db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            if (snap == null || !snap.exists()) { onChange(null); return@addSnapshotListener }
            onChange(
                UserDoc(
                    uid = uid,
                    name = snap.getString("name") ?: "",
                    email = snap.getString("email") ?: "",
                    phone = snap.getString("phone") ?: "",
                    coins = snap.getLong("coins") ?: 0,
                    plan = snap.getString("plan"),
                    banned = snap.getBoolean("banned") ?: false,
                )
            )
        }
    }

    /** طلب شحن يدوي (InstaPay / فودافون كاش) -> subscriptions بحالة pending لحد ما الأدمن يوافق */
    fun submitManualPayment(
        plan: Plan,
        method: String,
        senderInfo: String,
        reference: String,
        screenshotDataUrl: String?,
        onDone: (Throwable?) -> Unit,
    ) {
        val user = currentUser ?: return onDone(IllegalStateException("not signed in"))
        val data = hashMapOf<String, Any?>(
            "uid" to user.uid,
            "userName" to (user.displayName ?: ""),
            "userEmail" to (user.email ?: ""),
            "userPhone" to (user.phoneNumber ?: ""),
            "planId" to plan.id,
            "planName" to plan.name,
            "priceEgp" to plan.priceEgp,
            "coins" to plan.coins,
            "bonus" to plan.bonus,
            "method" to method,
            "senderInfo" to senderInfo,
            "reference" to reference,
            "screenshot" to screenshotDataUrl,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("subscriptions").add(data)
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { onDone(it) }
    }

    /** آخر طلبات المستخدم علشان يشوف حالتها */
    fun listenMyPayments(onChange: (List<Map<String, Any?>>) -> Unit): ListenerRegistration? {
        val uid = currentUser?.uid ?: return null
        return db.collection("subscriptions")
            .whereEqualTo("uid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                onChange(snap?.documents?.map { it.data.orEmpty() } ?: emptyList())
            }
    }
}
