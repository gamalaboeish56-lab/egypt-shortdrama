package egypt.shortdrama.top

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * window.AndroidApp داخل الموقع. أي تحديث مستقبلي للموقع يقدر يستخدمه مباشرة:
 *   if (window.AndroidApp) AndroidApp.openPayment("pro")
 */
class WebBridge(private val host: Host) {
    interface Host {
        fun openPayment(planId: String?)
        fun openAccount()
        fun showInterstitial()
        fun userJson(): JSONObject
    }

    @JavascriptInterface fun isApp(): Boolean = true
    @JavascriptInterface fun appVersion(): String = BuildConfig.VERSION_NAME
    @JavascriptInterface fun openPayment(planId: String?) = host.openPayment(planId)
    @JavascriptInterface fun openAccount() = host.openAccount()
    @JavascriptInterface fun showInterstitial() = host.showInterstitial()
    /** {"signedIn":true,"uid":"..","name":"..","coins":300,"plan":"pro"} */
    @JavascriptInterface fun getUser(): String = host.userJson().toString()
}
