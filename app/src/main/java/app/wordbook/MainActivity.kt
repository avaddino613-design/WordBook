package app.wordbook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.speech.tts.TextToSpeech
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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

        // Backs the speaker button for words with no recorded pronunciation audio.
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordbook")
    }

    // Runs JS back in the page. Safe to call from any thread; swallowed if the
    // activity is already gone (e.g. a lookup finishing after the user left the app).
    fun runJs(script: String) {
        runOnUiThread {
            try { web.evaluateJavascript(script, null) } catch (e: Exception) { /* webview gone */ }
        }
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
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
    fun speak(text: String) {
        activity.runOnUiThread { activity.speak(text) }
    }

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

    // Looks the word up from native code instead of the page's own fetch(). WebView's
    // fetch() is a browser call and can be blocked by cross-origin rules that never
    // apply to a plain server-to-server HTTP request made from Kotlin, so this is the
    // reliable path for the in-app lookup. Runs on a background thread; delivers the
    // result back into the page as window.onNativeLookupResult(status, body), where
    // status is the HTTP status code, or -1 if the request never reached a server.
    @JavascriptInterface
    fun lookupWord(word: String) {
        Thread {
            var status = -1
            var body = ""
            try {
                val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(word, "UTF-8"))
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
            } catch (e: Exception) {
                status = -1
                body = e.message ?: "network error"
            }
            activity.runJs("window.onNativeLookupResult && window.onNativeLookupResult($status, ${JSONObject.quote(body)})")
        }.start()
    }
}
