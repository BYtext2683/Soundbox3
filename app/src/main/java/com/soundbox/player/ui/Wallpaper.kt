package com.soundbox.player.ui

import android.content.Context
import android.content.Intent
import android.graphics.Movie
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundbox.player.App

/** 把图像以「覆盖」方式绘制到画布（scale=1 时填满画布，多余部分裁掉），再叠加用户缩放与偏移。 */
private fun DrawScope.drawWallpaper(
    image: ImageBitmap,
    scale: Float,
    offset: Offset,
) {
    val canvasW = size.width
    val canvasH = size.height
    val imgW = image.width.toFloat()
    val imgH = image.height.toFloat()
    val base = maxOf(canvasW / imgW, canvasH / imgH)
    val s = base * scale
    val drawW = imgW * s
    val drawH = imgH * s
    val left = (canvasW - drawW) / 2f + offset.x
    val top = (canvasH - drawH) / 2f + offset.y
    drawImage(
        image = image,
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(drawW.toInt(), drawH.toInt()),
    )
}

/** GIF（Movie）按同样规则绘制，借助 nativeCanvas 的矩阵实现缩放/偏移。 */
private fun DrawScope.drawWallpaperMovie(
    movie: Movie,
    scale: Float,
    offset: Offset,
) {
    val canvasW = size.width
    val canvasH = size.height
    val imgW = movie.width().toFloat()
    val imgH = movie.height().toFloat()
    val base = maxOf(canvasW / imgW, canvasH / imgH)
    val s = base * scale
    val drawW = imgW * s
    val drawH = imgH * s
    val left = (canvasW - drawW) / 2f + offset.x
    val top = (canvasH - drawH) / 2f + offset.y
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.translate(left, top)
    canvas.scale(s, s)
    movie.draw(canvas, 0f, 0f)
    canvas.restore()
}

private fun loadWallpaperBitmap(context: Context, uri: Uri): ImageBitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
}.getOrNull()

private fun loadWallpaperMovie(context: Context, uri: Uri): Movie? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        Movie.decodeStream(stream)
    }
}.getOrNull()?.takeIf { it.width() > 0 && it.height() > 0 }

/** 常驻在最底层：渲染用户设置的背景壁纸（静态图或动态 GIF），并应用缩放/偏移。 */
@Composable
fun WallpaperBackground(app: App) {
    val config by app.wallpaperConfig.collectAsStateWithLifecycle()
    if (config.uri.isBlank()) return
    val context = LocalContext.current
    val uriObj = remember(config.uri) { Uri.parse(config.uri) }
    val bitmap = remember(config.uri) { loadWallpaperBitmap(context, uriObj) }
    val movie = remember(config.uri) { if (bitmap == null) loadWallpaperMovie(context, uriObj) else null }
    val transform = remember(config) { Offset(config.offsetX, config.offsetY) to config.scale }

    Box(Modifier.fillMaxSize()) {
        when {
            bitmap != null -> Canvas(Modifier.fillMaxSize()) {
                drawWallpaper(bitmap, transform.second, transform.first)
            }
            movie != null -> AnimatedWallpaper(movie, transform.second, transform.first)
        }
    }
}

@Composable
private fun AnimatedWallpaper(movie: Movie, scale: Float, offset: Offset) {
    val duration = movie.duration().coerceAtLeast(1)
    val transition = rememberInfiniteTransition(label = "wp")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = duration.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wp-time",
    )
    Canvas(Modifier.fillMaxSize()) {
        movie.setTime(t.toInt())
        drawWallpaperMovie(movie, scale, offset)
    }
}

/** 设置页入口：从相册选择图片/GIF，缩放裁剪后应用为壁纸。 */
@Composable
fun WallpaperScreen(app: App, onBack: () -> Unit) {
    val context = LocalContext.current
    var cropActive by remember { mutableStateOf(app.prefs.wallpaperUri.isNotBlank()) }
    val uriString = app.prefs.wallpaperUri

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            app.prefs.wallpaperUri = uri.toString()
            cropActive = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("背景壁纸") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )

        if (!cropActive || uriString.isBlank()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "从相册选择一张图片作为 App 背景，支持静态图片与动态 GIF。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text("选择图片") }
            }
        } else {
            WallpaperCropper(
                uri = Uri.parse(uriString),
                initialScale = app.prefs.wallpaperScale,
                initialOffset = Offset(app.prefs.wallpaperOffsetX, app.prefs.wallpaperOffsetY),
                onApply = { scale, offset ->
                    app.applyWallpaper(uriString, scale, offset.x, offset.y)
                    onBack()
                },
                onCancel = onBack,
                onClear = {
                    app.clearWallpaper()
                    cropActive = false
                },
            )
        }
    }
}

@Composable
private fun WallpaperCropper(
    uri: Uri,
    initialScale: Float,
    initialOffset: Offset,
    onApply: (Float, Offset) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { loadWallpaperBitmap(context, uri) }
    val movie = remember(uri) { if (bitmap == null) loadWallpaperMovie(context, uri) else null }
    var scale by remember(uri) { mutableStateOf(initialScale) }
    var offset by remember(uri) { mutableStateOf(initialOffset) }

    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset += pan
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            Canvas(Modifier.fillMaxSize().transformable(state)) {
                drawWallpaper(bitmap, scale, offset)
            }
        } else if (movie != null) {
            Canvas(Modifier.fillMaxSize().transformable(state)) {
                drawWallpaperMovie(movie, scale, offset)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无法读取该图片", color = Color.White)
            }
        }

        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "双指缩放、拖动来选择显示区域，调整好后点「应用」。",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(onClick = onCancel) { Text("取消") }
                Row {
                    TextButton(onClick = onClear) { Text("恢复默认", color = Color.White) }
                    Button(onClick = { onApply(scale, offset) }) { Text("应用") }
                }
            }
        }
    }
}
