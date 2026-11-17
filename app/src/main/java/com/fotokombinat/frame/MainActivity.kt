package com.fotokombinat.frame

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class BorderStyle(val title: String, val ratio: Float? = null) {
    EVEN("Even"),
    SQUARE("Square", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    LANDSCAPE_5_4("5:4", 5f / 4f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    STORY_9_16("9:16", 9f / 16f),
    WIDE_16_9("16:9", 16f / 9f),
    ASYMMETRIC("Asymmetric"),
    DOUBLE("Double")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(245, 244, 240)
        window.navigationBarColor = AndroidColor.rgb(245, 244, 240)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { FrameApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var palette by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selected by remember { mutableIntStateOf(AndroidColor.rgb(235, 231, 222)) }
    var hex by remember { mutableStateOf("#EBE7DE") }
    var style by remember { mutableStateOf(BorderStyle.EVEN) }
    var widthPct by remember { mutableFloatStateOf(10f) }
    var topPct by remember { mutableFloatStateOf(10f) }
    var bottomPct by remember { mutableFloatStateOf(10f) }
    var leftPct by remember { mutableFloatStateOf(10f) }
    var rightPct by remember { mutableFloatStateOf(10f) }
    var linked by remember { mutableStateOf(true) }
    var sampling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val loaded = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 2048L * 2048L) }
                imageUri = uri
                bitmap = loaded
                palette = loaded?.let { withContext(Dispatchers.Default) { dominantColors(it, 10) } } ?: emptyList()
                message = if (loaded == null) "Could not open this image." else ""
            } catch (e: Exception) {
                message = "Could not open this image."
            } finally {
                busy = false
                sampling = false
            }
        }
    }

    fun applyColor(c: Int) {
        selected = c
        hex = String.format(Locale.US, "#%06X", 0xFFFFFF and c)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF252525),
            background = Color(0xFFF5F4F0),
            surface = Color(0xFFF5F4F0)
        )
    ) {
        Scaffold(
            containerColor = Color(0xFFF5F4F0),
            topBar = {
                TopAppBar(
                    title = { Text("FRAME", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                    actions = {
                        IconButton(onClick = {
                            bitmap = null
                            imageUri = null
                            palette = emptyList()
                            message = ""
                        }) { Icon(Icons.Default.Refresh, "Reset") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F4F0))
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("PHOTO BORDER STUDIO", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.3.sp)
                        Text("Keep the image. Add space.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                    Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                Box(
                    Modifier.fillMaxWidth().height(320.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Color(0xFFD9D7D1)),
                    contentAlignment = Alignment.Center
                ) {
                    val image = bitmap
                    if (image == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray, modifier = Modifier.size(42.dp))
                            Text("Choose a photograph", fontWeight = FontWeight.Medium)
                            Text("Your original image will never be cropped", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { picker.launch("image/*") }) { Text("Import photo") }
                        }
                    } else {
                        var previewSize by remember { mutableStateOf(IntSize.Zero) }
                        ComposeCanvas(
                            Modifier.fillMaxSize()
                                .onSizeChanged { previewSize = it }
                                .pointerInput(image, sampling, previewSize, topPct, bottomPct, leftPct, rightPct, style) {
                                    detectTapGestures { p ->
                                        if (sampling && previewSize.width > 0) {
                                            val margins = frameMargins(image.width, image.height, style, widthPct, topPct, bottomPct, leftPct, rightPct)
                                            val ml = margins[2]
                                            val mr = margins[3]
                                            val mt = margins[0]
                                            val mb = margins[1]
                                            val totalW = image.width + ml + mr
                                            val totalH = image.height + mt + mb
                                            val fit = minOf(previewSize.width / totalW, previewSize.height / totalH)
                                            val outW = totalW * fit
                                            val outH = totalH * fit
                                            val ox = (previewSize.width - outW) / 2f + ml * fit
                                            val oy = (previewSize.height - outH) / 2f + mt * fit
                                            val photoW = image.width * fit
                                            val photoH = image.height * fit
                                            if (p.x >= ox && p.x <= ox + photoW && p.y >= oy && p.y <= oy + photoH) {
                                                val ix = ((p.x - ox) / fit).toInt().coerceIn(0, image.width - 1)
                                                val iy = ((p.y - oy) / fit).toInt().coerceIn(0, image.height - 1)
                                                applyColor(image.getPixel(ix, iy))
                                                sampling = false
                                            }
                                        }
                                    }
                                }
                        ) {
                            val margins = frameMargins(image.width, image.height, style, widthPct, topPct, bottomPct, leftPct, rightPct)
                            val ml = margins[2]
                            val mr = margins[3]
                            val mt = margins[0]
                            val mb = margins[1]
                            val totalW = image.width + ml + mr
                            val totalH = image.height + mt + mb
                            val fit = minOf(size.width / totalW, size.height / totalH)
                            val outW = totalW * fit
                            val outH = totalH * fit
                            val x = (size.width - outW) / 2f
                            val y = (size.height - outH) / 2f
                            val photoX = x + ml * fit
                            val photoY = y + mt * fit
                            val photoW = image.width * fit
                            val photoH = image.height * fit
                            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = AndroidColor.WHITE
                                setShadowLayer(12f, 0f, 4f, AndroidColor.argb(75, 0, 0, 0))
                            }
                            drawContext.canvas.nativeCanvas.drawRoundRect(
                                RectF(x, y, x + outW, y + outH), 3.dp.toPx(), 3.dp.toPx(), shadowPaint
                            )
                            drawRect(Color(selected), Offset(x, y), androidx.compose.ui.geometry.Size(outW, outH))
                            // Draw the Android bitmap directly through the native canvas.
                            // This avoids ImageBitmap conversion/rendering issues on some devices.
                            drawContext.canvas.nativeCanvas.drawBitmap(
                                image,
                                null,
                                RectF(photoX, photoY, photoX + photoW, photoY + photoH),
                                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            )
                            if (style == BorderStyle.DOUBLE) {
                                drawRect(
                                    Color.Black.copy(alpha = .45f),
                                    Offset(photoX - 6, photoY - 6),
                                    androidx.compose.ui.geometry.Size(photoW + 12, photoH + 12),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                                )
                            }
                        }
                        if (sampling) {
                            Text(
                                "Tap a pixel in the photo to sample its color",
                                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                                    .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(8.dp)).padding(10.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Change photo")
                    }
                    Button(
                        onClick = {
                            val source = imageUri
                            if (source != null) scope.launch {
                                busy = true
                                var exportSource: Bitmap? = null
                                var out: Bitmap? = null
                                try {
                                    exportSource = withContext(Dispatchers.IO) {
                                        decodeBitmap(context, source, 16_000_000L)
                                    } ?: throw IllegalStateException("Image could not be decoded")
                                    out = withContext(Dispatchers.Default) {
                                        render(exportSource!!, selected, style, topPct, bottomPct, leftPct, rightPct, widthPct)
                                    }
                                    val saved = withContext(Dispatchers.IO) { saveBitmap(context, out!!) }
                                    message = if (saved) "Saved to Pictures/FRAME" else "Could not save image."
                                } catch (e: Exception) {
                                    message = "Export failed. The image may exceed this device's memory limit."
                                } finally {
                                    out?.recycle()
                                    exportSource?.recycle()
                                    busy = false
                                }
                            }
                        },
                        enabled = imageUri != null && !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                }
                if (message.isNotBlank()) Text(message, color = Color(0xFF39734A), style = MaterialTheme.typography.bodySmall)

                SectionTitle("FRAME TYPE")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BorderStyle.entries.forEach { s ->
                        FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s.title) })
                    }
                }

                SectionTitle("BORDER WIDTH")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${widthPct.toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("Minimum frame width", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                Slider(
                    value = widthPct,
                    onValueChange = { v ->
                        widthPct = v
                        if (linked) {
                            topPct = v
                            bottomPct = v
                            leftPct = v
                            rightPct = v
                        }
                    },
                    valueRange = 0f..40f
                )
                if (style == BorderStyle.ASYMMETRIC) {
                    SideSlider("Top", topPct) { topPct = it }
                    SideSlider("Bottom", bottomPct) { bottomPct = it }
                    SideSlider("Left", leftPct) { leftPct = it }
                    SideSlider("Right", rightPct) { rightPct = it }
                }
                if (style == BorderStyle.WEIGHTED) {
                    Text("Weighted style uses a deeper lower margin. Adjust Bottom independently.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                if (style == BorderStyle.SQUARE) {
                    Text("Square canvas adds space to make a square output. The photo itself remains uncropped.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }

                SectionTitle("BORDER COLOR")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(selected)).border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { v -> hex = v; parseHex(v)?.let { selected = it } },
                        label = { Text("HEX") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { sampling = true }, enabled = bitmap != null) {
                        Icon(Icons.Default.Colorize, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pick")
                    }
                }
                Text("PHOTO-BASED PALETTE · 10 DOMINANT COLORS", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.sp)
                if (palette.isEmpty()) {
                    Text("Import a photo to generate its color palette.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        palette.chunked(5).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                group.forEach { c ->
                                    Box(
                                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(9.dp))
                                            .background(Color(c))
                                            .border(if (c == selected) 2.dp else 1.dp, if (c == selected) Color.DarkGray else Color.LightGray, RoundedCornerShape(9.dp))
                                            .clickable { applyColor(c) }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
private fun SideSlider(name: String, v: Float, on: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = v, onValueChange = on, valueRange = 0f..40f, modifier = Modifier.weight(1f))
        Text("${v.toInt()}%", modifier = Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun decodeBitmap(
    context: android.content.Context,
    uri: android.net.Uri,
    maxPixels: Long
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > maxPixels) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}


private fun frameMargins(
    imageW: Int, imageH: Int, style: BorderStyle, widthPct: Float,
    topPct: Float, bottomPct: Float, leftPct: Float, rightPct: Float
): FloatArray {
    val minDimension = minOf(imageW, imageH).toFloat()
    val base = minDimension * widthPct / 100f
    if (style == BorderStyle.ASYMMETRIC) {
        return floatArrayOf(
            imageH * topPct / 100f, imageH * bottomPct / 100f,
            imageW * leftPct / 100f, imageW * rightPct / 100f
        )
    }
    if (style == BorderStyle.EVEN || style == BorderStyle.DOUBLE) {
        return floatArrayOf(base, base, base, base)
    }
    val targetRatio = style.ratio ?: 1f
    val minW = imageW + base * 2f
    val minH = imageH + base * 2f
    val canvasW: Float
    val canvasH: Float
    if (minW / minH > targetRatio) {
        canvasW = minW
        canvasH = minW / targetRatio
    } else {
        canvasH = minH
        canvasW = minH * targetRatio
    }
    val horizontal = maxOf(base, (canvasW - imageW) / 2f)
    val vertical = maxOf(base, (canvasH - imageH) / 2f)
    return floatArrayOf(vertical, vertical, horizontal, horizontal)
}

private fun parseHex(s: String): Int? = try {
    AndroidColor.parseColor(if (s.startsWith("#")) s else "#$s")
} catch (_: Exception) { null }

private fun dominantColors(src: Bitmap, count: Int): List<Int> {
    val sample = Bitmap.createScaledBitmap(
        src,
        minOf(160, src.width),
        (src.height.toFloat() * minOf(160, src.width) / src.width).toInt().coerceAtLeast(1),
        true
    )
    val hist = HashMap<Int, Int>()
    for (y in 0 until sample.height step 2) for (x in 0 until sample.width step 2) {
        val c = sample.getPixel(x, y)
        if (AndroidColor.alpha(c) > 100) {
            val r = (AndroidColor.red(c) / 32) * 32 + 16
            val g = (AndroidColor.green(c) / 32) * 32 + 16
            val b = (AndroidColor.blue(c) / 32) * 32 + 16
            val key = AndroidColor.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
            hist[key] = (hist[key] ?: 0) + 1
        }
    }
    val chosen = mutableListOf<Int>()
    hist.entries.sortedByDescending { it.value }.forEach { e ->
        if (chosen.none { colorDistance(it, e.key) < 42 }) chosen.add(e.key)
    }
    if (chosen.size < count) hist.keys.forEach { if (chosen.size < count && it !in chosen) chosen.add(it) }
    return chosen.take(count)
}

private fun colorDistance(a: Int, b: Int): Double {
    val dr = AndroidColor.red(a) - AndroidColor.red(b)
    val dg = AndroidColor.green(a) - AndroidColor.green(b)
    val db = AndroidColor.blue(a) - AndroidColor.blue(b)
    return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
}

private fun render(
    src: Bitmap, color: Int, style: BorderStyle,
    top: Float, bottom: Float, left: Float, right: Float, borderWidthPct: Float = 10f
): Bitmap {
    val margins = frameMargins(src.width, src.height, style, borderWidthPct, top, bottom, left, right)
    var t = margins[0].toInt()
    var b = margins[1].toInt()
    var l = margins[2].toInt()
    var r = margins[3].toInt()
    var outW = src.width + l + r
    var outH = src.height + t + b
    val out = Bitmap.createBitmap(outW.coerceAtLeast(1), outH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(color)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, l.toFloat(), t.toFloat(), paint)
    if (style == BorderStyle.DOUBLE) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            this.color = AndroidColor.BLACK
            alpha = 110
            strokeWidth = maxOf(1f, minOf(src.width, src.height) * 0.0015f)
        }
        val inset = maxOf(4f, minOf(src.width, src.height) * 0.012f)
        canvas.drawRect(RectF(l - inset, t - inset, l + src.width + inset, t + src.height + inset), line)
    }
    return out
}

private fun saveBitmap(context: android.content.Context, bitmap: Bitmap): Boolean {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "FRAME_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FRAME")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        val ok = context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 98, it) } ?: false
        if (!ok) context.contentResolver.delete(uri, null, null)
        ok
    } catch (e: Exception) {
        context.contentResolver.delete(uri, null, null)
        false
    }
}
