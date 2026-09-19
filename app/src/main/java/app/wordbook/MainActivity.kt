package app.wordbook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#12141B"))
        web = WebView(this)
        web.setBackgroundColor(Color.TRANSPARENT)
        root.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        // Serves index.html from the app's assets over a stable https address,
        // so saved words and the clipboard behave like a normal secure website.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: Exception) {
                    // no app can open it
                }
                return true
            }
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false

        web.addJavascriptInterface(NativeBridge(this), "WordbookNative")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    fun applyBars(color: Int, light: Boolean) {
        window.statusBarColor = color
        window.navigationBarColor = color
        root.setBackgroundColor(color)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onResume() {
        super.onResume()
        web.onResume()
        // Lets the page pick up a word the widget rotated while the app was closed.
        web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        web.evaluateJavascript("window.onNativeTheme && window.onNativeTheme()", null)
        WordWidget.updateAll(this)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.onNativeBack && window.onNativeBack()) === true") { result ->
            if (result != "true") finish()
        }
    }
}

/** What the web page can call. Runs on a background thread, so anything touching views is posted. */
class NativeBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun sync(json: String) {
        WordStore.saveRaw(activity, json)
        WordWidget.updateAll(activity)
    }

    @JavascriptInterface
    fun getState(): String = WordStore.raw(activity) ?: ""

    @JavascriptInterface
    fun isNight(): Boolean = activity.isNight()

    @JavascriptInterface
    fun setBars(color: String, light: Boolean) {
        activity.runOnUiThread {
            try {
                activity.applyBars(Color.parseColor(color), light)
            } catch (e: Exception) {
                // bad color string, ignore
            }
        }
    }
}
