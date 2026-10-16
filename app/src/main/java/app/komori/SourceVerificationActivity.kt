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
import app.komori.data.sourceNames
import app.komori.data.sourceOrigins

/** Visible, user-operated verification. No JavaScript bridge or automated challenge solving. */
class SourceVerificationActivity : Activity() {
    private var browser: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("source")
        val origin =
            sourceOrigins[key]
                ?: run {
                    finish()
                    return
                }
        val host = origin.toUri().host
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding, padding, padding)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            view.setPadding(
                padding + bars.left,
                padding + bars.top,
                padding + bars.right,
                padding + bars.bottom,
            )
            insets
        }
        layout.addView(
            TextView(this).apply {
                text =
                    getString(
                        app.komori.R.string.source_verification_intro,
                        sourceNames[key],
                        origin,
                    )
                setTextColor(android.graphics.Color.rgb(242, 246, 239))
                textSize = 14f
            }
        )
        val web = WebView(this).also { browser = it }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = (application as KomoriApp).sessions.userAgent
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    return uri.scheme != "https" ||
                        (uri.host != host &&
                            !(uri.host == "challenges.cloudflare.com" && !request.isForMainFrame))
                }
            }
        val actions = LinearLayout(this)
        actions.addView(
            Button(this).apply {
                setText(app.komori.R.string.retry_native_access)
                backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.rgb(213, 250, 57)
                    )
                setTextColor(android.graphics.Color.BLACK)
                isAllCaps = false
                setOnClickListener {
                    CookieManager.getInstance().flush()
                    setResult(RESULT_OK, Intent().putExtra("source", key))
                    finish()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            Button(this).apply {
                setText(app.komori.R.string.reset_source_sessions)
                backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.rgb(24, 50, 34)
                    )
                setTextColor(android.graphics.Color.rgb(242, 246, 239))
                isAllCaps = false
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@SourceVerificationActivity)
                        .setMessage(
                            "Clear all source browser sessions? Library, follows, downloads and reading progress are retained."
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear") { _, _ ->
                            web.stopLoading()
                            CookieManager.getInstance().removeAllCookies {
                                CookieManager.getInstance().flush()
                                web.clearCache(true)
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                web.loadUrl(origin)
                            }
                        }
                        .show()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        layout.addView(actions)
        layout.addView(
            web,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(layout)
        ViewCompat.requestApplyInsets(layout)
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null)
            web.loadUrl(origin)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        browser?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        browser?.onResume()
    }

    override fun onDestroy() {
        browser?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        browser = null
        super.onDestroy()
    }
}
