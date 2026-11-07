package net.olamaelcu.livtet.sync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scans a pairing QR code and hands the decoded text back as [EXTRA_PAYLOAD].
 *
 * The decode is ZXing over CameraX analysis frames rather than ML Kit's barcode scanner: ML Kit is
 * closed-source and this app ships an F-Droid flavor. Only the QR format is enabled — that is all a
 * pairing code is.
 *
 * The raw payload is returned, not a node id: [PairPayload] decides what the text means, so the
 * parser stays testable off-device and the caller can tell "not a pairing code" apart from "the
 * reader cancelled".
 */
class QrScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var granted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }
                var refused by remember { mutableStateOf(false) }
                val request =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { allowed ->
                        granted = allowed
                        if (!allowed) refused = true
                    }

                LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

                if (granted) {
                    QrScanner(
                        onDecoded = { payload -> finishWith(payload) },
                        onCancel = { cancel() },
                    )
                } else {
                    CameraRefusal(
                        refused = refused,
                        onRetry = { request.launch(Manifest.permission.CAMERA) },
                        onCancel = { cancel() },
                    )
                }
            }
        }
    }

    private fun finishWith(payload: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PAYLOAD, payload))
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        /** The decoded QR text, unparsed. */
        const val EXTRA_PAYLOAD = "payload"
    }
}

@Composable
private fun QrScanner(
    onDecoded: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val decoded = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (cameraError == null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                    providerFuture.addListener(
                        {
                            cameraError =
                                bindPreview(
                                    providerFuture,
                                    previewView,
                                    lifecycleOwner,
                                    analysisExecutor,
                                    decoded,
                                    onDecoded,
                                )
                        },
                        ContextCompat.getMainExecutor(viewContext),
                    )
                    previewView
                },
            )
        } else {
            CameraUnavailable(cameraError!!, onCancel)
        }
        Column(
            modifier =
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Point the camera at the pairing code on your desktop.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/**
 * Bind the preview and analysis to whichever camera exists.
 *
 * Returns the reason nothing could be bound, or `null` on success. The back camera is preferred
 * (that is the one pointed at a screen), but front-only devices — including most emulator images —
 * would otherwise show a black rectangle with no explanation.
 */
private fun bindPreview(
    providerFuture: com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    analysisExecutor: java.util.concurrent.Executor,
    decoded: AtomicBoolean,
    onDecoded: (String) -> Unit,
): String? {
    val provider =
        runCatching { providerFuture.get() }
            .getOrElse { error ->
                return "The camera could not be opened: ${error.message ?: "unknown error"}"
            }
    val selector =
        when {
            provider.hasCameraOrNull(CameraSelector.DEFAULT_BACK_CAMERA) == true ->
                CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCameraOrNull(CameraSelector.DEFAULT_FRONT_CAMERA) == true ->
                CameraSelector.DEFAULT_FRONT_CAMERA
            else ->
                return "This device reports no camera, so a code cannot be scanned. " +
                    "Enter the node id by hand instead."
        }
    val preview =
        Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
    val analysis =
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, QrFrameAnalyzer(decoded, onDecoded)) }
    return runCatching {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    }
        .exceptionOrNull()
        ?.let { error -> "The camera could not start: ${error.message ?: error::class.simpleName}" }
}

private fun ProcessCameraProvider.hasCameraOrNull(selector: CameraSelector): Boolean? = runCatching { hasCamera(selector) }.getOrNull()

@Composable
private fun CameraUnavailable(
    message: String,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onCancel) { Text("Enter the node id instead") }
    }
}

/**
 * Decodes QR frames until one reads, then stops reporting.
 *
 * The frame geometry comes from the plane's row stride, not the image width — see [QrDecoder].
 */
private class QrFrameAnalyzer(
    private val decoded: AtomicBoolean,
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val decoder = QrDecoder()

    override fun analyze(image: ImageProxy) {
        if (decoded.get()) {
            image.close()
            return
        }
        try {
            val text =
                image.planes.firstOrNull()?.let { plane ->
                    decoder.decode(
                        y = plane.buffer.toByteArrayCopy(),
                        rowStride = plane.rowStride,
                        width = image.width,
                        height = image.height,
                    )
                }
            if (!text.isNullOrBlank() && decoded.compareAndSet(false, true)) onDecoded(text)
        } finally {
            image.close()
        }
    }
}

/** The buffer's remaining bytes, copied, leaving the buffer's own position alone. */
private fun java.nio.ByteBuffer.toByteArrayCopy(): ByteArray {
    val copy = duplicate()
    val bytes = ByteArray(copy.remaining())
    copy.get(bytes)
    return bytes
}

@Composable
private fun CameraRefusal(
    refused: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (refused) {
                    "Camera access is off, so a pairing code cannot be scanned. Allow it, or " +
                        "enter the node id by hand."
                } else {
                    "Livtet needs the camera to scan a pairing code."
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRetry) { Text("Allow camera") }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
