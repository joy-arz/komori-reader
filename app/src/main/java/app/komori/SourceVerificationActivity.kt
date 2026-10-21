package app.komori

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.komori.data.*
import kotlinx.coroutines.*

/** Visible verification, exact source hosts and real native-access feedback. */
class SourceVerificationActivity : Activity() {
    private var browser: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("source").orEmpty()
        val origin = sourceOrigins[key] ?: run { finish(); return }
        val app = application as KomoriApp
        val ink = android.graphics.Color.BLACK
        val paper = android.graphics.Color.rgb(242, 246, 239)
        val lime = android.graphics.Color.rgb(213, 250, 57)
        val panel = android.graphics.Color.rgb(24, 50, 34)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        val padding = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        val status = TextView(this).apply {
            setTextColor(paper); textSize = 14f
            text = "${sourceNames[key]} · $origin\nComplete verification, then test native access. External-browser cookies stay separate."
        }
        layout.addView(status)
        val web = WebView(this).also { browser = it }
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = app.sessions.userAgent
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !allowsVerificationNavigation(key, request.url.toString(), request.isForMainFrame)
        }
        fun action(label: String, primary: Boolean = false, block: (Button) -> Unit): Button = Button(this).apply {
            text = label; isAllCaps = false
            minHeight = (48 * resources.displayMetrics.density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) lime else panel)
            setTextColor(if (primary) ink else paper)
            setOnClickListener { block(this) }
        }
        val access = LinearLayout(this)
        access.addView(action("Test native access", true) { button ->
            button.isEnabled = false
            status.text = "Checking ${sourceNames[key]} through the app's native client…"
            scope.launch {
                try {
                    CookieManager.getInstance().flush()
                    val result = app.source.browse(key, "", 1, BrowseFilter(), null)
                    if (result.titles.isEmpty()) throw ContentFailure("The source responded, but no titles were parsed. Native catalog access is not confirmed.")
                    android.widget.Toast.makeText(this@SourceVerificationActivity, "Native catalog access verified", android.widget.Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK, Intent().putExtra("source", key)); finish()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { status.text = connectionMessage(e) }
                finally { button.isEnabled = true }
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        access.addView(action("Open browser") {
            try { startActivity(Intent(Intent.ACTION_VIEW, origin.toUri())) }
            catch (_: android.content.ActivityNotFoundException) { status.text = "No browser is available. Install a browser or complete verification here." }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(access)
        val resets = LinearLayout(this)
        resets.addView(action("Reset this source") { button ->
            android.app.AlertDialog.Builder(this).setTitle("Reset ${sourceNames[key]}?")
                .setMessage("Clear this source's used cookies and site storage. Other sources are kept. Clear all sessions remains available if access still fails.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset") { _, _ ->
                    button.isEnabled = false
                    scope.launch {
                        try {
                            web.stopLoading(); app.sessions.resetSource(key); web.clearHistory(); web.loadUrl(origin)
                            status.text = "Source session reset. Complete verification, then test native access."
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { status.text = connectionMessage(e) }
                        finally { button.isEnabled = true }
                    }
                }.show()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        resets.addView(action("Clear all sessions") {
            android.app.AlertDialog.Builder(this).setTitle("Clear all source sessions?")
                .setMessage("All source browser sessions and site storage will be cleared. Library, follows, downloads and progress are kept.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear all") { _, _ ->
                    web.stopLoading()
                    CookieManager.getInstance().removeAllCookies {
                        CookieManager.getInstance().flush(); web.clearCache(true)
                        android.webkit.WebStorage.getInstance().deleteAllData(); web.loadUrl(origin)
                    }
                }.show()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(resets)
        layout.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        ViewCompat.requestApplyInsets(layout)
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(origin)
    }

    override fun onSaveInstanceState(outState: Bundle) { browser?.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onPause() { CookieManager.getInstance().flush(); browser?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onDestroy() {
        scope.cancel()
        browser?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.stopLoading(); it.destroy() }
        browser = null
        super.onDestroy()
    }
}
