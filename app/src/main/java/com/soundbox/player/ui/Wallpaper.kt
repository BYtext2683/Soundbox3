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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.soundbox.player.App
import com.soundbox.player.data.Prefs

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

/** 壁纸类型：静态图 / 动态 GIF / 视频（mp4 等）。 */
private enum class WallpaperType { IMAGE, GIF, VIDEO }

/** 根据内容 MIME（必要时嗅探解码）判断壁纸属于哪种类型。 */
private fun detectWallpaperType(context: Context, uri: Uri): WallpaperType {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (mime != null) return when {
        mime.startsWith("video/") -> WallpaperType.VIDEO
        mime == "image/gif" -> WallpaperType.GIF
        mime.startsWith("image/") -> WallpaperType.IMAGE
        else -> WallpaperType.IMAGE
    }
    // MIME 未知时兜底：先尝试静态图，再 GIF，最后当作视频。
    if (loadWallpaperBitmap(context, uri) != null) return WallpaperType.IMAGE
    if (loadWallpaperMovie(context, uri) != null) return WallpaperType.GIF
    return WallpaperType.VIDEO
}

/** 常驻在最底层：渲染用户设置的背景壁纸（静态图 / 动态 GIF / 视频），并应用缩放/偏移。 */
@Composable
fun WallpaperBackground(app: App) {
    val config by app.wallpaperConfig.collectAsStateWithLifecycle()
    if (config.uri.isBlank()) return
    val context = LocalContext.current
    val uriObj = remember(config.uri) { Uri.parse(config.uri) }
    val type = remember(config.uri) { detectWallpaperType(context, uriObj) }
    val offset = Offset(config.offsetX, config.offsetY)

    Box(Modifier.fillMaxSize()) {
        when (type) {
            WallpaperType.IMAGE -> {
                val bitmap = remember(config.uri) { loadWallpaperBitmap(context, uriObj) }
                if (bitmap != null) Canvas(Modifier.fillMaxSize()) {
                    drawWallpaper(bitmap, config.scale, offset)
                }
            }
            WallpaperType.GIF -> {
                val movie = remember(config.uri) { loadWallpaperMovie(context, uriObj) }
                if (movie != null) AnimatedWallpaper(movie, config.scale, offset)
            }
            WallpaperType.VIDEO -> {
                VideoWallpaper(uriObj, Modifier.fillMaxSize(), config.scale, offset)
            }
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

/**
 * 视频壁纸：用 ExoPlayer 把 mp4 等视频渲染进 TextureView，静音循环播放。
 * 缩放模式采用「cover」铺满，用户缩放/偏移通过 graphicsLayer 叠加，
 * 与静态图、GIF 壁纸保持一致的视觉表现。
 */
@Composable
private fun VideoWallpaper(
    uri: Uri,
    modifier: Modifier = Modifier.fillMaxSize(),
    scale: Float,
    offset: Offset,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    key(uri) {
        AndroidView(
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                transformOrigin = TransformOrigin.Center
            },
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
        )
    }
}

/** 设置页入口：从相册选择图片/GIF/视频，缩放裁剪并调节不透明度后应用为壁纸。 */
@Composable
fun WallpaperScreen(app: App, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriString = app.prefs.wallpaperUri
    var editing by remember { mutableStateOf(false) }
    var opacity by remember { mutableStateOf(app.prefs.wallpaperOpacity) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            app.prefs.wallpaperUri = uri.toString()
            editing = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("背景壁纸") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
        )

        if (uriString.isBlank()) {
            // 尚未选择图片：引导选择
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "从相册选择一张图片或视频作为 App 背景，支持静态图片、动态 GIF 与 .mp4 视频。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { picker.launch(arrayOf("image/*", "video/mp4")) }) { Text("选择图片 / 视频") }
            }
        } else if (editing) {
            // 裁剪 / 缩放编辑模式
            WallpaperCropper(
                uri = Uri.parse(uriString),
                initialScale = app.prefs.wallpaperScale,
                initialOffset = Offset(app.prefs.wallpaperOffsetX, app.prefs.wallpaperOffsetY),
                onApply = { scale, offset ->
                    app.applyWallpaper(uriString, scale, offset.x, offset.y, opacity)
                    editing = false
                },
                onCancel = { editing = false },
                onClear = {
                    app.resetWallpaper()
                    opacity = Prefs.DEFAULT_WALLPAPER_OPACITY
                    editing = false
                },
            )
        } else {
            // 预览 + 不透明度调节（拖动滑块即时作用于全局背景）
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "拖动滑块调节背景的明显程度。下方按钮可更换图片或重新调整裁剪区域。",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "背景不透明度：${(opacity * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        app.setWallpaperOpacity(it)
                    },
                    valueRange = 0f..1f,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { picker.launch(arrayOf("image/*", "video/mp4")) }) { Text("更换图片 / 视频") }
                    Button(onClick = { editing = true }) { Text("调整裁剪") }
                    TextButton(onClick = {
                        app.resetWallpaper()
                        opacity = Prefs.DEFAULT_WALLPAPER_OPACITY
                    }) { Text("恢复默认") }
                }
            }
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
    val type = remember(uri) { detectWallpaperType(context, uri) }
    val bitmap = remember(uri) { if (type == WallpaperType.IMAGE) loadWallpaperBitmap(context, uri) else null }
    val movie = remember(uri) { if (type == WallpaperType.GIF) loadWallpaperMovie(context, uri) else null }
    var scale by remember(uri) { mutableStateOf(initialScale) }
    var offset by remember(uri) { mutableStateOf(initialOffset) }

    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset += pan
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (type) {
            WallpaperType.IMAGE -> if (bitmap != null) {
                Canvas(Modifier.fillMaxSize().transformable(state)) {
                    drawWallpaper(bitmap, scale, offset)
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无法读取该图片", color = Color.White)
                }
            }
            WallpaperType.GIF -> if (movie != null) {
                Canvas(Modifier.fillMaxSize().transformable(state)) {
                    drawWallpaperMovie(movie, scale, offset)
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("无法读取该图片", color = Color.White)
                }
            }
            WallpaperType.VIDEO -> {
                VideoWallpaper(uri, Modifier.fillMaxSize().transformable(state), scale, offset)
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
