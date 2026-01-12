package com.tyler.selfcontrol.ui.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.tyler.selfcontrol.R
import com.tyler.selfcontrol.domain.UrlBlocker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Utility Browser Activity with QR scanner as primary interface.
 *
 * Features:
 * - QR scanner view by default (requires camera permission)
 * - Hidden URL bar (toggle to show)
 * - WebView with URL blocking
 * - Session cleared on start (no cookies/history persist)
 * - Download support via DownloadManager
 */
@AndroidEntryPoint
class BrowserActivity : ComponentActivity() {

    @Inject
    lateinit var urlBlocker: UrlBlocker

    private lateinit var webView: WebView
    private lateinit var urlBar: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var scannerContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleUrlBarButton: ImageButton
    private lateinit var scanQrButton: ImageButton
    private lateinit var goButton: ImageButton

    private var qrScannerView: QrScannerView? = null
    private var isUrlBarVisible = false
    private var isScannerMode = true

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startQrScanner()
        } else {
            // Camera permission denied - show URL bar instead
            showUrlBar()
            Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        initViews()
        setupWebView()
        clearSessionData()

        // Check camera permission and start scanner or show URL bar
        checkCameraPermissionAndStartScanner()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        urlInput = findViewById(R.id.urlInput)
        scannerContainer = findViewById(R.id.scannerContainer)
        progressBar = findViewById(R.id.progressBar)
        toggleUrlBarButton = findViewById(R.id.toggleUrlBarButton)
        scanQrButton = findViewById(R.id.scanQrButton)
        goButton = findViewById(R.id.goButton)

        // URL input enter key handling
        urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadUrl(urlInput.text.toString())
                true
            } else {
                false
            }
        }

        // Button click handlers
        toggleUrlBarButton.setOnClickListener {
            toggleUrlBar()
        }

        scanQrButton.setOnClickListener {
            if (isScannerMode) {
                // Already in scanner mode, do nothing or refresh scanner
            } else {
                checkCameraPermissionAndStartScanner()
            }
        }

        goButton.setOnClickListener {
            loadUrl(urlInput.text.toString())
        }

        // Initially hide URL bar
        urlBar.visibility = View.GONE
        isUrlBarVisible = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webViewClient = BlockingWebViewClient(
            urlBlocker = urlBlocker,
            coroutineScope = lifecycleScope,
            onUrlBlocked = { url ->
                // Additional handling when URL is blocked
            }
        )

        webView.webViewClient = webViewClient

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.isVisible = newProgress < 100
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE

            // Block popups
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)

            // Allow mixed content (some utility sites might not be HTTPS)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // User agent - use default WebView user agent
            userAgentString = userAgentString
        }

        // Set up download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            handleDownload(url, userAgent, contentDisposition, mimetype)
        }
    }

    private fun clearSessionData() {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun checkCameraPermissionAndStartScanner() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startQrScanner()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show explanation then request
                Toast.makeText(
                    this,
                    "Camera permission is needed for QR code scanning",
                    Toast.LENGTH_SHORT
                ).show()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startQrScanner() {
        isScannerMode = true
        webView.visibility = View.GONE
        scannerContainer.visibility = View.VISIBLE

        // Create and add QR scanner view if not already present
        if (qrScannerView == null) {
            qrScannerView = QrScannerView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            scannerContainer.addView(qrScannerView)
        }

        qrScannerView?.startScanning { detectedUrl ->
            onQrCodeDetected(detectedUrl)
        }
    }

    private fun stopQrScanner() {
        qrScannerView?.stopScanning()
    }

    private fun onQrCodeDetected(content: String) {
        // Check if it's a URL
        if (content.startsWith("http://") || content.startsWith("https://") ||
            content.contains(".") && !content.contains(" ")) {
            stopQrScanner()
            loadUrl(content)
        } else {
            // Not a URL - show toast
            Toast.makeText(this, "Scanned: $content", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUrl(url: String) {
        var processedUrl = url.trim()

        // Add https:// if no scheme provided
        if (!processedUrl.startsWith("http://") && !processedUrl.startsWith("https://")) {
            processedUrl = "https://$processedUrl"
        }

        isScannerMode = false
        stopQrScanner()
        scannerContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE

        urlInput.setText(processedUrl)
        webView.loadUrl(processedUrl)
    }

    private fun toggleUrlBar() {
        isUrlBarVisible = !isUrlBarVisible
        urlBar.visibility = if (isUrlBarVisible) View.VISIBLE else View.GONE

        if (isUrlBarVisible) {
            urlInput.requestFocus()
        }
    }

    private fun showUrlBar() {
        isUrlBarVisible = true
        urlBar.visibility = View.VISIBLE
        isScannerMode = false
        scannerContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file...")
                setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype)
                )
            }

            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            // If URL bar is visible, hide it first
            isUrlBarVisible -> {
                toggleUrlBar()
            }
            // If WebView can go back, navigate back
            webView.canGoBack() -> {
                webView.goBack()
            }
            // If we're viewing a page, go back to scanner
            !isScannerMode -> {
                checkCameraPermissionAndStartScanner()
            }
            // Otherwise, close the activity
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isScannerMode && qrScannerView != null) {
            qrScannerView?.startScanning { detectedUrl ->
                onQrCodeDetected(detectedUrl)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopQrScanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    companion object {
        private const val TAG = "BrowserActivity"
    }
}
