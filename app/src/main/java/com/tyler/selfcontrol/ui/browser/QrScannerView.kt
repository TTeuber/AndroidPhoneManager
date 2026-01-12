package com.tyler.selfcontrol.ui.browser

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Custom view that provides QR code scanning functionality using CameraX and ML Kit.
 *
 * Usage:
 * 1. Add to layout or create programmatically
 * 2. Call startScanning() with a callback to receive detected QR codes
 * 3. Call stopScanning() when done
 */
class QrScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var previewView: PreviewView? = null
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var onQrCodeDetected: ((String) -> Unit)? = null
    private var isScanning = false
    private var lastDetectedCode: String? = null
    private var lastDetectionTime: Long = 0

    // Minimum delay between processing the same QR code (to avoid repeated triggers)
    private val detectionCooldownMs = 2000L

    init {
        setupView()
    }

    private fun setupView() {
        previewView = PreviewView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(previewView)

        // Configure barcode scanner for QR codes
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)
    }

    /**
     * Start scanning for QR codes.
     *
     * @param onDetected Callback invoked when a QR code is detected
     */
    fun startScanning(onDetected: (String) -> Unit) {
        if (isScanning) return

        this.onQrCodeDetected = onDetected
        isScanning = true

        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Stop scanning and release camera resources.
     */
    fun stopScanning() {
        isScanning = false
        onQrCodeDetected = null

        cameraProvider?.unbindAll()
        cameraProvider = null

        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val previewView = previewView ?: return

        // Unbind any existing use cases
        cameraProvider.unbindAll()

        // Camera selector - use back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        // Image analysis use case for QR detection
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
            if (!isScanning) {
                imageProxy.close()
                return@setAnalyzer
            }

            @androidx.camera.core.ExperimentalGetImage
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                barcodeScanner?.process(image)
                    ?.addOnSuccessListener { barcodes ->
                        processBarcodes(barcodes)
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scanning failed", e)
                    }
                    ?.addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        // Get lifecycle owner from context
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            Log.e(TAG, "Context is not a LifecycleOwner")
            return
        }

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    private fun processBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isEmpty()) return

        val currentTime = System.currentTimeMillis()

        for (barcode in barcodes) {
            val rawValue = barcode.rawValue ?: continue

            // Check if this is a new QR code or enough time has passed
            if (rawValue != lastDetectedCode ||
                (currentTime - lastDetectionTime) > detectionCooldownMs
            ) {
                lastDetectedCode = rawValue
                lastDetectionTime = currentTime

                // Invoke callback on main thread
                post {
                    onQrCodeDetected?.invoke(rawValue)
                }

                // Only process one barcode at a time
                break
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopScanning()
        barcodeScanner?.close()
    }

    companion object {
        private const val TAG = "QrScannerView"
    }
}
