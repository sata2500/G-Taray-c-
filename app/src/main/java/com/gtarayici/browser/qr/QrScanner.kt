package com.gtarayici.browser.qr

import com.gtarayici.browser.components
import com.gtarayici.browser.ui.feedback.UiText
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.gtarayici.browser.R
import java.util.concurrent.Executors
import mozilla.components.support.ktx.util.URLStringUtils

/** Görüntüdeki QR kodunu çözer (yalnızca parlaklık kanalı kullanılır). */
internal class QrAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
    @Volatile private var done = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (done) return
            val plane = it.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { bytes -> buffer.get(bytes) }
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            val text = decode(BinaryBitmap(HybridBinarizer(source)))
                ?: decode(BinaryBitmap(HybridBinarizer(source.invert()))) // Açık renk zemin üzerinde koyu olmayan kodlar
            if (text != null) {
                done = true
                onResult(text)
            }
        }
    }

    private fun decode(bitmap: BinaryBitmap): String? = try {
        reader.decodeWithState(bitmap).text
    } catch (_: NotFoundException) {
        null
    } finally {
        reader.reset()
    }
}

/**
 * Tam ekran QR kod tarayıcı. Adres içeren kodlar doğrudan açılır; diğer metinler için
 * arama veya kopyalama seçeneği sunulur. Görüntü cihazda işlenir.
 */
@Composable
fun QrScannerScreen(onOpen: (String) -> Unit, onSearch: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && result == null) {
            CameraPreview { text ->
                if (URLStringUtils.isURLLike(text.trim()) && !text.trim().contains(' ')) {
                    onOpen(URLStringUtils.toNormalizedURL(text.trim()))
                } else {
                    result = text
                }
            }
            // Hedef çerçevesi
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
            )
            Text(
                stringResource(R.string.qr_hint),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(32.dp),
            )
        } else if (!granted && asked) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.qr_permission), color = Color.White, textAlign = TextAlign.Center)
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.qr_allow))
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.systemBarsPadding().padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
        }
    }

    result?.let { text ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(stringResource(R.string.qr_result)) },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { onSearch(text) }) { Text(stringResource(R.string.qr_search)) } },
            dismissButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
                    context.components.messenger.success(UiText.res(R.string.qr_copied))
                    result = null
                }) { Text(stringResource(R.string.qr_copy)) }
            },
        )
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val cameraProvider = future.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, QrAnalyzer { text -> previewView.post { onResult(text) } }) }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}
