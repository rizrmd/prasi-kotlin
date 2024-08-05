package com.avolut.prasi

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.avolut.prasi.ui.theme.PrasiTheme

class MainActivity : ComponentActivity() {
  private lateinit var app: PrasiApplication

  override fun onResume() {
    this.app.currentActivity = this;
    super.onResume()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    this.app = this.getApplicationContext() as PrasiApplication;
    this.app.currentActivity = this;

    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (this.app.server != null) {
      return showWebview()
    }
  }

  fun showWebview() {
    setContent {
      PrasiTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          WebViewContent(
            url = "http://localhost:${Server.port}",
            modifier = Modifier.padding(innerPadding),
            onBackPressedDispatcher,
            this
          )
        }
      }
    }
  }
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(
  url: String,
  modifier: Modifier = Modifier,
  backDispatch: OnBackPressedDispatcher,
  activity: MainActivity,
) {
  var fileUploadCallback: ValueCallback<Array<Uri>>? = null

  val getContent =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent(),
      onResult = { result ->
        if (result != null) {
          fileUploadCallback?.onReceiveValue(arrayOf(result))
        } else {
          fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
      })

  return AndroidView(
    factory = { context ->
      val webview = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        webViewClient = WebViewClient()
        loadUrl(url)
      }

      backDispatch.addCallback(activity) {
        if (webview.canGoBack()) {
          webview.goBack()
        } else {
          activity.onBackPressed()
        }
      }

      val webSettings: WebSettings = webview.settings
      with(webSettings) {
        javaScriptEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        javaScriptCanOpenWindowsAutomatically = true
        mediaPlaybackRequiresUserGesture = false
        domStorageEnabled = true
      }

      webview.webChromeClient = (object : WebChromeClient() {
        override fun onShowFileChooser(
          webView: WebView?,
          filePathCallback: ValueCallback<Array<Uri>>?,
          fileChooserParams: FileChooserParams?,
        ): Boolean {
          fileUploadCallback?.onReceiveValue(null)
          fileUploadCallback = filePathCallback
          val type =
            when (fileChooserParams?.acceptTypes!![0] === "") {
              true -> "*/*"
              false -> fileChooserParams?.acceptTypes!![0]
            }
          getContent.launch(type)
          return true
        }
      });

      webview;
    }, modifier = modifier.fillMaxSize()
  )
}
