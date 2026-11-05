package egypt.shortdrama.top

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.firestore.ListenerRegistration
import egypt.shortdrama.top.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity(), WebBridge.Host {

    private lateinit var b: ActivityMainBinding
    private lateinit var ads: Ads
    private var userListener: ListenerRegistration? = null
    private var user: UserDoc? = null

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val siteHost = Uri.parse(BuildConfig.SITE_URL).host ?: "egypt-shortdrama.top"

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uris = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val mediaPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val req = pendingWebPermission ?: return@registerForActivityResult
        pendingWebPermission = null
        val allowed = req.resources.filter {
            when (it) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> granted[Manifest.permission.CAMERA] == true || hasPerm(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> granted[Manifest.permission.RECORD_AUDIO] == true || hasPerm(Manifest.permission.RECORD_AUDIO)
                else -> false
            }
        }
        if (allowed.isEmpty()) req.deny() else req.grant(allowed.toTypedArray())
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onAuthChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ads = Ads(this, b.adContainer)
        setupWebView()
        b.coinsChip.setOnClickListener { openPayment(null) }
        b.accountBtn.setOnClickListener { openAccount() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    b.webView.canGoBack() -> b.webView.goBack()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })

        if (savedInstanceState != null) b.webView.restoreState(savedInstanceState)
        else b.webView.loadUrl(intent?.data?.takeIf { it.host == siteHost }?.toString() ?: BuildConfig.SITE_URL)

        askNotificationPermission()
        onAuthChanged()
        // أول تشغيل: نعرض تسجيل الدخول (المستخدم يقدر يكمل كضيف)
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (Repo.currentUser == null && !prefs.getBoolean("askedLogin", false)) {
            prefs.edit().putBoolean("askedLogin", true).apply()
            authLauncher.launch(Intent(this, AuthActivity::class.java))
        }
        ads.showBanner()
        ads.preloadInterstitial()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf { it.host == siteHost }?.let { b.webView.loadUrl(it.toString()) }
        intent.getStringExtra("url")?.let { b.webView.loadUrl(it) } // من الإشعارات
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = b.webView
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString EgyptShortDramaApp/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        wv.addJavascriptInterface(WebBridge(this), "AndroidApp")

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url
                val host = url.host ?: ""
                if ((url.scheme == "https" || url.scheme == "http") &&
                    (host == siteHost || host.endsWith(".$siteHost") || host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") || host.endsWith("vercel.app"))
                ) return false
                if (url.scheme == "file") return false
                openExternal(url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                b.progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                b.progress.visibility = View.GONE
                if (url?.startsWith("http") == true) {
                    view.evaluateJavascript(assets.open("inject.js").bufferedReader().use { it.readText() }, null)
                }
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) view.loadUrl("file:///android_asset/offline.html")
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { b.progress.progress = p }

            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try { filePicker.launch(params.createIntent()); true }
                catch (e: ActivityNotFoundException) { fileCallback = null; false }
            }

            // كاميرا/مايك للمستقبل - بنسمح بس لموقعنا
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.origin.host != siteHost) { request.deny(); return@runOnUiThread }
                    val needed = mutableListOf<String>()
                    request.resources.forEach {
                        if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) needed += Manifest.permission.CAMERA
                        if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) needed += Manifest.permission.RECORD_AUDIO
                    }
                    if (needed.isEmpty()) { request.deny(); return@runOnUiThread }
                    pendingWebPermission = request
                    mediaPermissions.launch(needed.toTypedArray())
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                b.fullscreen.addView(view)
                b.fullscreen.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            override fun onHideCustomView() = hideCustomView()
        }

        wv.setDownloadListener { url, ua, contentDisposition, mime, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mime)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mime)
                    .addRequestHeader("User-Agent", ua)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                getSystemService(DownloadManager::class.java).enqueue(req)
                Toast.makeText(this, "جاري التحميل: $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                openExternal(Uri.parse(url))
            }
        }
    }

    private fun hideCustomView() {
        val v = customView ?: return
        b.fullscreen.removeView(v)
        b.fullscreen.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun openExternal(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (e: ActivityNotFoundException) { Toast.makeText(this, "مفيش تطبيق يفتح الرابط ده", Toast.LENGTH_SHORT).show() }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------------- Auth / user ----------------
    private fun onAuthChanged() {
        userListener?.remove()
        userListener = null
        user = null
        val fbUser = Repo.currentUser
        if (fbUser == null) { renderUser(); return }
        Repo.ensureUserDoc(fbUser)
        userListener = Repo.listenUser { doc ->
            user = doc
            renderUser()
            if (doc?.banned == true) showBanned()
        }
    }

    private fun renderUser() {
        val u = user
        b.coinsChip.text = if (u == null) "شحن 🪙" else getString(R.string.coins_fmt, u.coins)
        ads.isSubscriber = u?.plan != null
        // نبلغ الموقع بالتحديث
        b.webView.evaluateJavascript(
            "window.egyptAppUser=${userJson()};window.dispatchEvent(new CustomEvent('egypt-app-user',{detail:window.egyptAppUser}));",
            null,
        )
    }

    private var bannedDialog: AlertDialog? = null
    private fun showBanned() {
        if (bannedDialog?.isShowing == true) return
        bannedDialog = AlertDialog.Builder(this)
            .setTitle("الحساب موقوف")
            .setMessage("تم إيقاف حسابك من الإدارة. للتواصل: gamalaboeish56@gmail.com")
            .setCancelable(false)
            .setPositiveButton("تسجيل خروج") { _, _ -> Repo.auth.signOut(); onAuthChanged() }
            .setNegativeButton("خروج") { _, _ -> finishAffinity() }
            .show()
    }

    // ---------------- WebBridge.Host ----------------
    override fun openPayment(planId: String?) = runOnUiThread {
        if (Repo.currentUser == null) {
            Toast.makeText(this, "سجل دخول الأول علشان نضيف العملات لحسابك", Toast.LENGTH_SHORT).show()
            authLauncher.launch(Intent(this, AuthActivity::class.java))
            return@runOnUiThread
        }
        startActivity(Intent(this, PaymentActivity::class.java).putExtra("plan", planId))
    }

    override fun openAccount() = runOnUiThread {
        val fb = Repo.currentUser
        if (fb == null) { authLauncher.launch(Intent(this, AuthActivity::class.java)); return@runOnUiThread }
        val u = user
        val plan = Plans.byId(u?.plan)
        val msg = buildString {
            appendLine("الاسم: ${fb.displayName ?: u?.name ?: "-"}")
            fb.email?.takeIf { it.isNotBlank() }?.let { appendLine("الإيميل: $it") }
            fb.phoneNumber?.takeIf { it.isNotBlank() }?.let { appendLine("الموبايل: $it") }
            appendLine("الرصيد: ${u?.coins ?: 0} عملة")
            append("الباقة: ${plan?.let { "${it.name} (شارة ${it.badge})" } ?: "مفيش"}")
        }
        AlertDialog.Builder(this)
            .setTitle("حسابي")
            .setMessage(msg)
            .setPositiveButton("شحن عملات") { _, _ -> openPayment(null) }
            .setNeutralButton("تسجيل خروج") { _, _ -> Repo.auth.signOut(); onAuthChanged() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    override fun showInterstitial() = runOnUiThread { ads.maybeShowInterstitial() }

    override fun userJson(): JSONObject {
        val u = user
        return JSONObject().apply {
            put("signedIn", Repo.currentUser != null)
            put("uid", Repo.currentUser?.uid ?: JSONObject.NULL)
            put("name", Repo.currentUser?.displayName ?: u?.name ?: JSONObject.NULL)
            put("coins", u?.coins ?: 0)
            put("plan", u?.plan ?: JSONObject.NULL)
        }
    }

    // ---------------- lifecycle ----------------
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        b.webView.saveState(outState)
    }

    override fun onPause() { super.onPause(); b.webView.onPause(); ads.onPause() }
    override fun onResume() { super.onResume(); b.webView.onResume(); ads.onResume() }
    override fun onDestroy() {
        userListener?.remove()
        ads.onDestroy()
        b.webView.destroy()
        super.onDestroy()
    }
}
