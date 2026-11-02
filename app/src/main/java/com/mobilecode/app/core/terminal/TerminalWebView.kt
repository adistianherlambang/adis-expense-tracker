package com.mobilecode.app.core.terminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalWebView(
    activeSessionId: String,
    outputEvents: SharedFlow<Pair<String, String>>,
    onTerminalInput: (sessionId: String, data: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()

            addJavascriptInterface(
                TerminalBridge(
                    onInputCallback = onTerminalInput,
                    onTerminalReadyCallback = {
                        post {
                            evaluateJavascript(
                                "window.terminalBridge && window.terminalBridge.setSession(${JSONObject.quote(activeSessionId)});",
                                null
                            )
                        }
                    }
                ),
                "AndroidTerminalBridge"
            )

            loadUrl("file:///android_asset/terminal/index.html")
        }
    }

    LaunchedEffect(activeSessionId) {
        val js = "window.terminalBridge && window.terminalBridge.setSession(${JSONObject.quote(activeSessionId)});"
        webView.evaluateJavascript(js, null)
    }

    LaunchedEffect(outputEvents) {
        outputEvents.collect { (sessionId, data) ->
            val safeData = JSONObject.quote(data)
            val safeSession = JSONObject.quote(sessionId)
            val js = "window.terminalBridge && window.terminalBridge.write($safeSession, $safeData);"
            webView.evaluateJavascript(js, null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}

