package com.mobilecode.app.core.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mobilecode.app.core.designsystem.VsCodeTheme
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoWebView(
    activeTab: EditorTab?,
    onContentChanged: (fileId: String, content: String, isDirty: Boolean) -> Unit,
    onCursorPosition: (line: Int, column: Int) -> Unit,
    onWebViewReady: ((WebView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPageReady by remember { mutableStateOf(false) }

    if (activeTab == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(VsCodeTheme.EditorBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No open editor. Select a file from Explorer to begin editing.",
                color = VsCodeTheme.SecondaryForeground
            )
        }
        return
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            isFocusable = true
            isFocusableInTouchMode = true

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
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isPageReady = true
                    sendOpenFile(this@apply, activeTab)
                }
            }

            addJavascriptInterface(
                EditorBridge(
                    onContentChangedCallback = onContentChanged,
                    onCursorPositionCallback = onCursorPosition,
                    onEditorReadyCallback = {
                        post {
                            isPageReady = true
                            sendOpenFile(this, activeTab)
                        }
                    }
                ),
                "AndroidEditorBridge"
            )

            loadUrl("file:///android_asset/monaco/index.html")
            onWebViewReady?.invoke(this)
        }
    }

    LaunchedEffect(activeTab.id, isPageReady) {
        if (isPageReady) {
            sendOpenFile(webView, activeTab)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            if (isPageReady) {
                sendOpenFile(view, activeTab)
            }
        }
    )
}

fun sendOpenFile(webView: WebView, tab: EditorTab) {
    val safeContent = JSONObject.quote(tab.content)
    val safeId = JSONObject.quote(tab.id)
    val safePath = JSONObject.quote(tab.file.absolutePath)
    val safeLanguage = JSONObject.quote(tab.language)

    val js = "window.monacoBridge && window.monacoBridge.openFile($safeId, $safePath, $safeContent, $safeLanguage, ${tab.isReadOnly});"
    webView.evaluateJavascript(js, null)
}

fun insertTextIntoEditor(webView: WebView, text: String) {
    val safeText = JSONObject.quote(text)
    webView.evaluateJavascript("window.monacoBridge && window.monacoBridge.insertText($safeText);", null)
}

fun deleteCharFromEditor(webView: WebView) {
    webView.evaluateJavascript("window.monacoBridge && window.monacoBridge.deleteChar();", null)
}
