package io.github.kgma74.relaix.ui.enroll

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Camera preview that reports the first QR code it decodes.
 *
 * [onScanned] fires at most once: the analyzer stops looking as soon as it
 * has a payload. Enrollment tokens are single-use, so a second frame
 * decoding the same code and firing a second `Enroll` would spend the token
 * on a call whose result is thrown away, and the retry would then fail with
 * "already used" for no visible reason.
 */
@Composable
fun QrScanner(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Owned here rather than per-frame: a scanner and a thread created for
    // every image would churn hard at 30fps.
    val scanner = remember { BarcodeScanning.getClient() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)

            providerFuture.addListener({
                val provider = providerFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    // Dropping stale frames matters more than seeing all of
                    // them: the code is static, and a backlog would only add
                    // latency between pointing the camera and reacting.
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy, scanner, delivered, onScanned)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, androidx.core.content.ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun processFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    delivered: java.util.concurrent.atomic.AtomicBoolean,
    onScanned: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || delivered.get()) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val payload = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            // compareAndSet, not a plain flag: success callbacks land on the
            // main thread while frames keep arriving, so two decodes can race
            // to report the same code.
            if (payload != null && delivered.compareAndSet(false, true)) {
                onScanned(payload)
            }
        }
        // The image must be closed whatever happens, or the pipeline stalls
        // after a couple of frames with no error to explain it.
        .addOnCompleteListener { imageProxy.close() }
}
