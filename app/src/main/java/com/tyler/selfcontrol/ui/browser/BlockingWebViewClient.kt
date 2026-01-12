package com.tyler.selfcontrol.ui.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.tyler.selfcontrol.domain.UrlBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

/**
 * Custom WebViewClient that intercepts URL loading and blocks URLs based on WebsiteRules.
 *
 * Blocking is implemented in two places:
 * 1. shouldOverrideUrlLoading - Blocks navigation to blocked URLs
 * 2. shouldInterceptRequest - Blocks JavaScript-loaded content from blocked domains
 */
class BlockingWebViewClient(
    private val urlBlocker: UrlBlocker,
    private val coroutineScope: CoroutineScope,
    private val onUrlBlocked: (String) -> Unit = {}
) : WebViewClient() {

    private var currentUrl: String? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentUrl = url
    }

    /**
     * Intercept navigation requests before the URL is loaded.
     * This handles user clicks, redirects, and form submissions.
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Check if URL should be blocked
        val shouldBlock = runBlocking {
            urlBlocker.shouldBlockUrl(url)
        }

        if (shouldBlock) {
            coroutineScope.launch(Dispatchers.Main) {
                onUrlBlocked(url)
                showBlockedMessage(view)

                // Go back if possible, otherwise just stay on current page
                if (view.canGoBack()) {
                    view.goBack()
                }
            }
            return true // Prevent navigation
        }

        return false // Allow navigation
    }

    /**
     * Intercept resource requests (JavaScript, images, etc.) and block requests to blocked domains.
     * This catches content loaded via JavaScript that wouldn't trigger shouldOverrideUrlLoading.
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()

        // Skip blocking for the main document (handled by shouldOverrideUrlLoading)
        if (request.isForMainFrame) {
            return super.shouldInterceptRequest(view, request)
        }

        // Check if URL should be blocked
        val shouldBlock = runBlocking {
            urlBlocker.shouldBlockUrl(url)
        }

        if (shouldBlock) {
            // Return an empty response to block the request
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }

        return super.shouldInterceptRequest(view, request)
    }

    /**
     * Handle errors during page load.
     */
    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        @Suppress("DEPRECATION")
        super.onReceivedError(view, errorCode, description, failingUrl)
        // Could show error UI here if needed
    }

    private fun showBlockedMessage(view: WebView) {
        Toast.makeText(view.context, "This website is blocked", Toast.LENGTH_SHORT).show()
    }

    /**
     * Get the current URL being displayed.
     */
    fun getCurrentUrl(): String? = currentUrl
}
