package com.example.steam_vault_app.feature.steamsession

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONTokener

private const val RSA_RETRY_COUNT_TAG_KEY = -10303

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SteamEmbeddedLoginWebView(
    initialUrl: String,
    assistRequest: SteamWebLoginAssistRequest?,
    onPageObserved: (observation: SteamWebLoginObservation) -> Unit,
    onAssistResult: (SteamWebLoginAssistResult) -> Unit,
    onConsoleObserved: (message: String) -> Unit = {},
    startFreshSession: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookieManager = CookieManager.getInstance()
            WebView.setWebContentsDebuggingEnabled(
                (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            )
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                setTag(STEAM_WEB_LOGIN_SESSION_READY_TAG_KEY, false)
                setTag(RSA_RETRY_COUNT_TAG_KEY, 0)

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.message()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { message ->
                                if (
                                    message.contains("rsa key", ignoreCase = true) ||
                                    message.contains("auth session", ignoreCase = true) ||
                                    message.contains("axioserror", ignoreCase = true)
                                ) {
                                    Log.w(STEAM_WEB_LOGIN_TAG, "steamConsole: $message")

                                    if (message.contains("failed to get rsa key", ignoreCase = true) ||
                                        message.contains("cannot start auth session", ignoreCase = true)) {
                                        val currentCount = (getTag(RSA_RETRY_COUNT_TAG_KEY) as? Int) ?: 0
                                        if (currentCount < MAX_RSA_RETRY_COUNT) {
                                            Log.w(STEAM_WEB_LOGIN_TAG, "RSA key failed, retrying in ${RSA_RETRY_DELAY_MS}ms (attempt ${currentCount + 1})")
                                            setTag(RSA_RETRY_COUNT_TAG_KEY, currentCount + 1)
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                reload()
                                            }, RSA_RETRY_DELAY_MS)
                                        } else {
                                            Log.w(STEAM_WEB_LOGIN_TAG, "RSA key failed max times, giving up")
                                        }
                                    }
                                }
                                onConsoleObserved(message)
                            }
                        return super.onConsoleMessage(consoleMessage)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val effectiveUrl = url ?: initialUrl
                        val cookieHeader = cookieManager.getCookie(
                            SteamWebLoginSessionCapture.cookiesUrl,
                        )
                        val currentView = view
                        if (currentView == null) {
                            onPageObserved(
                                SteamWebLoginObservation(
                                    currentUrl = effectiveUrl,
                                    cookieHeader = cookieHeader,
                                    pageTitle = null,
                                    pageTextSnippet = null,
                                ),
                            )
                            return
                        }
                        currentView.evaluateJavascript(STEAM_WEB_LOGIN_BODY_TEXT_SCRIPT) { rawValue ->
                            currentView.evaluateJavascript(STEAM_WEB_LOGIN_DIAGNOSTICS_SCRIPT) { rawDiagnostics ->
                                logSteamEmbeddedLoginPageDiagnostics(
                                    currentUrl = effectiveUrl,
                                    pageTitle = currentView.title,
                                    cookieHeader = cookieHeader,
                                    rawDiagnostics = rawDiagnostics,
                                )
                            }
                            onPageObserved(
                                SteamWebLoginObservation(
                                    currentUrl = effectiveUrl,
                                    cookieHeader = cookieHeader,
                                    pageTitle = currentView.title,
                                    pageTextSnippet = decodeJavascriptStringResult(rawValue),
                                ),
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val targetUrl = request?.url?.toString().orEmpty()
                        return !(targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))
                    }
                }
                prepareSteamEmbeddedLoginSession(
                    context = context,
                    webView = this,
                    cookieManager = cookieManager,
                    startFreshSession = startFreshSession,
                ) {
                    loadUrl(initialUrl)
                }
            }
        },
        update = { webView ->
            assistRequest?.let { request ->
                val lastProcessedRequestId = webView.getTag(
                    STEAM_WEB_LOGIN_LAST_ASSIST_REQUEST_TAG_KEY,
                ) as? Int
                if (lastProcessedRequestId != request.requestId) {
                    Log.d(
                        STEAM_WEB_LOGIN_TAG,
                        "assistRequest id=${request.requestId} stage=${request.stage} hasSecondary=${!request.secondaryValue.isNullOrBlank()}",
                    )
                    webView.setTag(STEAM_WEB_LOGIN_LAST_ASSIST_REQUEST_TAG_KEY, request.requestId)
                    webView.evaluateJavascript(
                        SteamWebLoginAssist.buildJavascript(request),
                    ) { rawValue ->
                        val result = SteamWebLoginAssist.parseJavascriptResult(rawValue)
                        Log.d(
                            STEAM_WEB_LOGIN_TAG,
                            "assistResult id=${request.requestId} success=${result.success} submitted=${result.submitted} matched=${result.matchedField.orEmpty()} message=${result.message.orEmpty()}",
                        )
                        onAssistResult(result)
                    }
                }
            }
            val isSessionReady = webView.getTag(
                STEAM_WEB_LOGIN_SESSION_READY_TAG_KEY,
            ) as? Boolean ?: false
            if (isSessionReady && webView.url == null) {
                webView.loadUrl(initialUrl)
            }
        },
    )
}

private const val STEAM_WEB_LOGIN_LAST_ASSIST_REQUEST_TAG_KEY = -10301
private const val STEAM_WEB_LOGIN_SESSION_READY_TAG_KEY = -10302
private const val STEAM_WEB_LOGIN_TAG = "SteamEmbeddedLogin"

// RSA 密钥失败自动重试配置
private const val MAX_RSA_RETRY_COUNT = 3  // 最多重试 3 次
private const val RSA_RETRY_DELAY_MS = 2000L  // 每次重试间隔 2 秒

private const val STEAM_WEB_LOGIN_BODY_TEXT_SCRIPT = """
    (function() {
        const text = document.body && document.body.innerText ? document.body.innerText : '';
        return text.replace(/\s+/g, ' ').trim().slice(0, 800);
    })();
"""

private const val STEAM_WEB_LOGIN_DIAGNOSTICS_SCRIPT = """
    (function() {
        const isVisible = (element) => {
            if (!element || !element.isConnected) return false;
            let current = element;
            while (current) {
                const style = window.getComputedStyle(current);
                if (style && (style.display === 'none' || style.visibility === 'hidden')) {
                    return false;
                }
                current = current.parentElement;
            }
            const rects = typeof element.getClientRects === 'function' ? element.getClientRects() : null;
            return !!((rects && rects.length > 0) || element.offsetWidth > 0 || element.offsetHeight > 0);
        };
        const describe = (element) => ({
            tag: (element.tagName || '').toLowerCase(),
            id: element.id || '',
            name: element.getAttribute('name') || '',
            type: element.getAttribute('type') || '',
            placeholder: element.getAttribute('placeholder') || '',
            autocomplete: element.getAttribute('autocomplete') || '',
            ariaLabel: element.getAttribute('aria-label') || '',
            text: (element.innerText || element.textContent || '').trim().slice(0, 80)
        });
        const inputs = Array.from(document.querySelectorAll('input, textarea'))
            .filter(isVisible)
            .map(describe)
            .slice(0, 8);
        const buttons = Array.from(document.querySelectorAll('button, input[type="submit"]'))
            .filter(isVisible)
            .map(describe)
            .slice(0, 8);
        return JSON.stringify({ inputs, buttons });
    })();
"""

private fun decodeJavascriptStringResult(rawValue: String?): String? {
    val candidate = rawValue?.trim().orEmpty()
    if (candidate.isEmpty() || candidate == "null") {
        return null
    }
    val parsed = runCatching {
        JSONTokener(candidate).nextValue()
    }.getOrNull() as? String ?: return null
    return parsed.trim().takeIf { it.isNotEmpty() }
}

private fun decodeJavascriptJsonObject(rawValue: String?): org.json.JSONObject? {
    val candidate = rawValue?.trim().orEmpty()
    if (candidate.isEmpty() || candidate == "null") {
        return null
    }
    val parsed = runCatching {
        JSONTokener(candidate).nextValue()
    }.getOrNull() as? String ?: return null
    return runCatching { org.json.JSONObject(parsed) }.getOrNull()
}

private fun logSteamEmbeddedLoginPageDiagnostics(
    currentUrl: String,
    pageTitle: String?,
    cookieHeader: String?,
    rawDiagnostics: String?,
) {
    val diagnostics = decodeJavascriptJsonObject(rawDiagnostics)
    val inputs = diagnostics?.optJSONArray("inputs")?.toString().orEmpty()
    val buttons = diagnostics?.optJSONArray("buttons")?.toString().orEmpty()
    val hasSessionId = cookieHeader?.contains("sessionid=", ignoreCase = true) == true
    val hasAuthCookie =
        cookieHeader?.contains("steamLoginSecure=", ignoreCase = true) == true ||
            cookieHeader?.contains("steamLogin=", ignoreCase = true) == true
    Log.d(
        STEAM_WEB_LOGIN_TAG,
        "page url=$currentUrl title=${pageTitle.orEmpty()} hasSessionId=$hasSessionId hasAuthCookie=$hasAuthCookie inputs=$inputs buttons=$buttons",
    )
}

private fun prepareSteamEmbeddedLoginSession(
    context: Context,
    webView: WebView,
    cookieManager: CookieManager,
    startFreshSession: Boolean,
    onReady: () -> Unit,
) {
    if (!startFreshSession) {
        webView.setTag(STEAM_WEB_LOGIN_SESSION_READY_TAG_KEY, true)
        onReady()
        return
    }

    webView.stopLoading()
    webView.clearHistory()
    webView.clearCache(true)
    webView.clearFormData()
    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(context).clearFormData()
    WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    runCatching { WebViewDatabase.getInstance(context).clearUsernamePassword() }
    cookieManager.removeSessionCookies(null)
    cookieManager.removeAllCookies {
        cookieManager.flush()
        webView.setTag(STEAM_WEB_LOGIN_SESSION_READY_TAG_KEY, true)
        onReady()
    }
}
