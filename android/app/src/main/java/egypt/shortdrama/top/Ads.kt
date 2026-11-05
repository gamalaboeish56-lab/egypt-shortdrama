package egypt.shortdrama.top

import android.app.Activity
import android.os.SystemClock
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * بانر ثابت تحت + إعلان بيني (Interstitial) بحد أقصى مرة كل 3 دقايق.
 * المشتركين في أي باقة مبيظهرلهمش إعلانات منبثقة (زي ما الموقع بيقول "بدون إعلانات منبثقة").
 */
class Ads(private val activity: Activity, private val container: ViewGroup) {
    private var banner: AdView? = null
    private var interstitial: InterstitialAd? = null
    private var lastShown = 0L
    var isSubscriber = false
        set(value) {
            field = value
            if (value) hideBanner() else showBanner()
        }

    fun showBanner() {
        if (banner != null || isSubscriber) return
        val width = (activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt()
        banner = AdView(activity).apply {
            adUnitId = BuildConfig.BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width))
            adListener = object : AdListener() {}
            loadAd(AdRequest.Builder().build())
        }
        container.removeAllViews()
        container.addView(banner)
    }

    private fun hideBanner() {
        banner?.destroy()
        banner = null
        container.removeAllViews()
    }

    fun preloadInterstitial() {
        if (interstitial != null || isSubscriber) return
        InterstitialAd.load(
            activity, BuildConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitial = null }
            },
        )
    }

    /** يتنادى عند التنقل بين الحلقات/التبويبات أو من الموقع عن طريق AndroidApp.showInterstitial() */
    fun maybeShowInterstitial() {
        if (isSubscriber) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShown < MIN_GAP_MS) return
        val ad = interstitial ?: return preloadInterstitial()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitial = null; preloadInterstitial() }
            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) { interstitial = null }
        }
        lastShown = now
        ad.show(activity)
    }

    fun onPause() = banner?.pause()
    fun onResume() = banner?.resume()
    fun onDestroy() = banner?.destroy()

    companion object { private const val MIN_GAP_MS = 3 * 60 * 1000L }
}
