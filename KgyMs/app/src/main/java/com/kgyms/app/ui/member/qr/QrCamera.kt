package com.kgyms.app.ui.member.qr

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

/**
 * Khung camera sau + ML Kit đọc mã QR. Chỉ là View: mỗi mã đọc được đẩy lên [onCode],
 * ViewModel quyết định có xử lý hay bỏ qua. Màn hình gọi composable này khi đã có quyền CAMERA.
 */
@Composable
internal fun QrCameraPreview(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, QrAnalyzer(scanner) { code -> latestOnCode(code) }) }

        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                provider = future.get().also {
                    it.unbind(preview, analysis)
                    it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Gửi mỗi khung hình cho ML Kit; kết quả trả về trên main thread. */
private class QrAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { proxy.close() }
    }
}

/** Vẽ chuỗi [content] thành mã QR đen trên nền trắng (ZXing, offline). */
@Composable
internal fun QrCodeImage(content: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { encodeQr(content) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        filterQuality = FilterQuality.None
    )
}

private fun encodeQr(content: String, size: Int = 512): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}.getOrNull()
