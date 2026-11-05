package egypt.shortdrama.top

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import egypt.shortdrama.top.databinding.ActivityPaymentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * الدفع اليدوي (مطلوب حالياً): InstaPay + فودافون كاش.
 * المستخدم يحول -> يرفع سكرين شوت -> طلب pending في subscriptions -> الأدمن يوافق من لوحة التحكم -> العملات تتضاف.
 */
class PaymentActivity : AppCompatActivity() {
    private lateinit var b: ActivityPaymentBinding
    private var plan: Plan = Plans.byId("pro")!!
    private var method = "instapay"
    private var screenshot: String? = null
    private var requestsListener: ListenerRegistration? = null
    private val cardViews = mutableMapOf<String, View>()

    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) loadScreenshot(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        Plans.byId(intent.getStringExtra("plan"))?.let { plan = it }

        buildPlanCards()
        b.methodGroup.check(b.mInstapay.id)
        b.methodGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            method = when (id) { b.mVodafone.id -> "vodafone"; b.mPaymob.id -> "paymob"; else -> "instapay" }
            render()
        }
        b.copyBtn.setOnClickListener { copy(b.payAddress.text.toString()) }
        b.openAppBtn.setOnClickListener { openPaymentApp() }
        b.pickShotBtn.setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        b.submitBtn.setOnClickListener { if (method == "paymob") payWithPaymob() else submit() }

        requestsListener = Repo.listenMyPayments { renderRequests(it) }
        render()
    }

    override fun onDestroy() { requestsListener?.remove(); super.onDestroy() }

    private fun buildPlanCards() {
        val dp = resources.displayMetrics.density
        Plans.all.forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (10 * dp).toInt() }
                setOnClickListener { plan = p; render() }
            }
            val left = TextView(this).apply {
                text = buildString {
                    append(p.name)
                    if (p.id == "pro") append("  ⭐ الأكثر شيوعاً")
                    append("\n${p.coins} عملة")
                    if (p.bonus > 0) append(" + ${p.bonus} هدية")
                }
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val price = TextView(this).apply {
                text = "${p.priceEgp} ج"
                setTextColor(ContextCompat.getColor(context, R.color.gold))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            card.addView(left); card.addView(price)
            b.plansContainer.addView(card)
            cardViews[p.id] = card
        }
    }

    private fun render() {
        cardViews.forEach { (id, v) -> v.isSelected = id == plan.id }
        val manual = method != "paymob"
        b.manualBox.visibility = if (manual) View.VISIBLE else View.GONE
        listOf(b.step3, b.senderLayout, b.refLayout, b.pickShotBtn).forEach { it.visibility = if (manual) View.VISIBLE else View.GONE }
        b.shotPreview.visibility = if (manual && screenshot != null) View.VISIBLE else View.GONE

        when (method) {
            "instapay" -> {
                b.payTitle.text = "حوّل على عنوان InstaPay ده (أو امسح الـ QR من تطبيق InstaPay):"
                b.payAddress.text = BuildConfig.INSTAPAY_ADDRESS
                b.qr.visibility = View.VISIBLE
                b.qr.setImageBitmap(instapayQr())
                b.openAppBtn.text = "افتح تطبيق InstaPay"
                b.senderLayout.hint = "عنوان InstaPay أو رقمك اللي حولت منه"
            }
            "vodafone" -> {
                b.payTitle.text = "حوّل على محفظة فودافون كاش:"
                b.payAddress.text = BuildConfig.VODAFONE_CASH
                b.qr.visibility = View.GONE
                b.openAppBtn.text = "حوّل بكود *9*7# (فودافون)"
                b.senderLayout.hint = "رقم المحفظة اللي حولت منها"
            }
            "paymob" -> {}
        }
        b.amountText.text = "المبلغ المطلوب: ${plan.priceEgp} جنيه — هيتضافلك ${plan.totalCoins} عملة بعد المراجعة"
        b.submitBtn.text = when {
            method != "paymob" -> "ابعت طلب التفعيل"
            Paymob.enabled -> "ادفع ${plan.priceEgp} ج بالفيزا / المحفظة"
            else -> "الدفع بالكارت قريباً"
        }
        b.submitBtn.isEnabled = method != "paymob" || Paymob.enabled
    }

    /**
     * لو المالك بعت صورة QR الرسمية من تطبيق InstaPay حطها في res/drawable/instapay_qr.png وهتظهر بدل المولّدة.
     * المولّد هنا بيكتب العنوان نفسه في الـ QR.
     */
    private fun instapayQr(): Bitmap {
        val resId = resources.getIdentifier("instapay_qr", "drawable", packageName)
        if (resId != 0) return BitmapFactory.decodeResource(resources, resId)
        val size = 512
        val matrix = QRCodeWriter().encode(BuildConfig.INSTAPAY_ADDRESS, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("pay", text))
        Toast.makeText(this, "اتنسخ: $text", Toast.LENGTH_SHORT).show()
    }

    private fun openPaymentApp() {
        try {
            if (method == "vodafone") {
                val ussd = "*9*7*${BuildConfig.VODAFONE_CASH}*${plan.priceEgp}#"
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussd))))
            } else {
                copy(BuildConfig.INSTAPAY_ADDRESS)
                val pkg = "com.egyptianbanks.instapay"
                val launch = packageManager.getLaunchIntentForPackage(pkg)
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                startActivity(launch)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "مش لاقي التطبيق", Toast.LENGTH_SHORT).show()
        }
    }

    /** نضغط الصورة لـ JPEG صغير (أقل من ~400KB) ونخزنه في Firestore — مش محتاجين Firebase Storage (مدفوع) */
    private fun loadScreenshot(uri: Uri) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var sample = 1
                    while (maxOf(opts.outWidth, opts.outHeight) / sample > 1600) sample *= 2
                    val bmp = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching null
                    val scale = 900f / maxOf(bmp.width, bmp.height)
                    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                    var quality = 70
                    var bytes: ByteArray
                    do {
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                        bytes = out.toByteArray()
                        quality -= 10
                    } while (bytes.size > 400_000 && quality > 20)
                    Pair(scaled, "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
                }.getOrNull()
            }
            if (data == null) { Toast.makeText(this@PaymentActivity, "مقدرناش نقرا الصورة", Toast.LENGTH_SHORT).show(); return@launch }
            b.shotPreview.setImageBitmap(data.first)
            screenshot = data.second
            render()
        }
    }

    private fun submit() {
        val sender = b.sender.text?.toString()?.trim().orEmpty()
        val ref = b.reference.text?.toString()?.trim().orEmpty()
        if (sender.isEmpty()) { b.senderLayout.error = "مطلوب علشان نلاقي التحويل"; return }
        b.senderLayout.error = null
        if (screenshot == null) { Toast.makeText(this, "ارفع سكرين شوت التحويل", Toast.LENGTH_SHORT).show(); return }
        b.progress.visibility = View.VISIBLE
        b.submitBtn.isEnabled = false
        Repo.submitManualPayment(plan, method, sender, ref, screenshot) { err ->
            b.progress.visibility = View.GONE
            b.submitBtn.isEnabled = true
            if (err != null) {
                Toast.makeText(this, "مبعتش: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            } else {
                screenshot = null
                b.sender.setText(""); b.reference.setText("")
                render()
                AlertDialog.Builder(this)
                    .setTitle("تم استلام طلبك ✅")
                    .setMessage("هنراجع التحويل ونضيف ${plan.totalCoins} عملة لحسابك في أقرب وقت، وهيوصلك إشعار.")
                    .setPositiveButton("تمام") { _, _ -> finish() }
                    .show()
            }
        }
    }

    private fun payWithPaymob() {
        val user = Repo.currentUser ?: return
        b.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { Paymob.createCheckout(user, plan, "card") }
                .onSuccess { startActivity(Intent(Intent.ACTION_VIEW, it)) }
                .onFailure { Toast.makeText(this@PaymentActivity, "Paymob: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
            b.progress.visibility = View.GONE
        }
    }

    private fun renderRequests(list: List<Map<String, Any?>>) {
        if (list.isEmpty()) { b.myRequests.text = "مفيش طلبات لسه"; return }
        b.myRequests.text = list.joinToString("\n") {
            val status = when (it["status"]) {
                "approved" -> "✅ اتفعّل"
                "rejected" -> "❌ مرفوض${(it["note"] as? String)?.let { n -> " ($n)" } ?: ""}"
                else -> "⏳ قيد المراجعة"
            }
            "${it["planName"]} - ${it["priceEgp"]}ج - $status"
        }
    }
}
