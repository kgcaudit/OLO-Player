package org.olo.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.olo.player.art.AlbumArtApi
import org.olo.player.ui.theme.OloTheme

// 자르기 결과 규격 -- 커버 공통 500px(AlbumArtApi.COVER_PX)로 저장한다.
private const val MAX_ZOOM = 5f

/**
 * 기기 사진 한 장을 '정사각형으로 자르기'. 핀치로 확대/축소, 드래그로 이동하고, 고정된 정사각
 * 프레임에 보이는 영역을 [AlbumArtApi.COVER_PX]×[AlbumArtApi.COVER_PX] JPEG로 만들어 [onCropped]에
 * 넘긴다(온라인 커버와 같은 교체 경로). 메모리 보호를 위해 큰 사진은 작업용(≈1600px)으로 줄여 다룬다.
 *
 * 최소 확대 = 프레임을 꽉 채움(빈 여백 없음), 최대 = 5x. 이동은 프레임이 늘 덮이도록 가둔다.
 */
@Composable
fun ImageCropSheet(
    source: Uri,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) } // 자르기/인코딩 중

    LaunchedEffect(source) {
        val bmp = withContext(Dispatchers.IO) { runCatching { loadSampledBitmap(context, source, WORK_MAX_PX) }.getOrNull() }
        if (bmp != null) bitmap = bmp else failed = true
    }

    // 뷰 변환 상태(프레임 로컬 px 기준). 프레임 크기를 알게 되면 cover-fit으로 초기화한다.
    var scale by remember(source) { mutableStateOf(0f) }
    var offset by remember(source) { mutableStateOf(Offset.Zero) }
    var framePx by remember(source) { mutableStateOf(0) }
    val img = remember(bitmap) { bitmap?.asImageBitmap() }

    fun minScaleFor(f: Int, bmp: Bitmap): Float = f.toFloat() / min(bmp.width, bmp.height)

    fun clampOffset(o: Offset, s: Float, bmp: Bitmap, f: Int): Offset {
        val dispW = bmp.width * s
        val dispH = bmp.height * s
        val x = o.x.coerceIn(min(0f, f - dispW), 0f)
        val y = o.y.coerceIn(min(0f, f - dispH), 0f)
        return Offset(x, y)
    }

    // 프레임 크기·비트맵이 준비되면 1회 초기화(가운데, 최소 확대).
    LaunchedEffect(framePx, bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (framePx <= 0) return@LaunchedEffect
        if (scale == 0f) {
            val s0 = minScaleFor(framePx, bmp)
            scale = s0
            offset = Offset((framePx - bmp.width * s0) / 2f, (framePx - bmp.height * s0) / 2f)
        }
    }

    OloCardDialog(
        title = "사진 자르기",
        onDismiss = onCancel,
        titleAccent = true,
        actions = {
            OloDialogButton("취소", onClick = onCancel, primary = false)
            OloDialogButton(if (working) "저장 중…" else "적용", onClick = {
                val bmp = bitmap ?: return@OloDialogButton
                if (working || scale <= 0f || framePx <= 0) return@OloDialogButton
                working = true
                // 프레임(0..framePx)을 이미지 픽셀로 역변환해 정사각 영역을 자른다.
                val srcLeft = (-offset.x) / scale
                val srcTop = (-offset.y) / scale
                val srcSize = framePx / scale
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching { cropToJpeg(bmp, srcLeft, srcTop, srcSize, AlbumArtApi.COVER_PX) }.getOrNull()
                    }
                    working = false
                    if (bytes != null) onCropped(bytes) else onCancel()
                }
            })
        },
    ) {
        val c = OloTheme.colors
        Text("핀치로 확대, 드래그로 이동해 정사각형을 맞추세요. 적용 시 ${AlbumArtApi.COVER_PX}×${AlbumArtApi.COVER_PX}로 저장합니다.",
            color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black),
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            framePx = with(density) { maxWidth.toPx() }.roundToInt()
            val bmp = bitmap
            val image = img
            when {
                failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("사진을 불러오지 못했습니다.", color = Color.White, fontSize = 13.sp)
                }
                bmp == null || image == null || scale <= 0f -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
                else -> CropViewport(
                    image = image, imgW = bmp.width, imgH = bmp.height, scale = scale, offset = offset,
                    modifier = Modifier.fillMaxSize().clipToBounds().pointerInput(bmp, framePx) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val s0 = minScaleFor(framePx, bmp)
                            val newScale = (scale * zoom).coerceIn(s0, s0 * MAX_ZOOM)
                            // 센트로이드(두 손가락 중심) 아래 지점을 고정하며 확대/축소 + 이동.
                            val newOffset = centroid - (centroid - offset) * (newScale / scale) + pan
                            scale = newScale
                            offset = clampOffset(newOffset, newScale, bmp, framePx)
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("최대 ${MAX_ZOOM.toInt()}배까지 확대", color = c.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * 자르기 뷰포트: 이미지에 [scale]·[offset]을 적용해 그리고 3분할 가이드를 얹는다. 시트와 대조
 * 렌더가 같은 그리기를 쓰도록 분리(제스처는 [modifier]로 주입).
 */
@Composable
internal fun CropViewport(
    image: ImageBitmap,
    imgW: Int,
    imgH: Int,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawImage(
            image = image,
            dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
            dstSize = IntSize((imgW * scale).roundToInt(), (imgH * scale).roundToInt()),
        )
        val w = size.width
        val h = size.height
        val gl = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            drawLine(gl, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), 1f)
            drawLine(gl, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), 1f)
        }
    }
}

// 작업용 비트맵 최대 변. 메모리 보호(큰 원본은 이만큼으로 줄여 자르기).
private const val WORK_MAX_PX = 1600

// content URI를 [maxPx] 이하로 샘플링해 비트맵으로. EXIF 회전은 고려하지 않는다(포토 피커가 대개
// 정립 이미지를 주며, 사용자가 자르기 화면에서 구도를 직접 맞춘다).
private fun loadSampledBitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val (w, h) = bounds.outWidth to bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (max(w, h) / sample > maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

// 이미지 픽셀 좌표의 정사각 영역을 잘라 [outPx]×[outPx] JPEG 바이트로. 경계는 비트맵 안으로 가둔다.
internal fun cropToJpeg(src: Bitmap, left: Float, top: Float, sizeF: Float, outPx: Int): ByteArray {
    val maxSize = min(src.width, src.height)
    val size = sizeF.roundToInt().coerceIn(1, maxSize)
    val x = left.roundToInt().coerceIn(0, src.width - size)
    val y = top.roundToInt().coerceIn(0, src.height - size)
    val cropped = Bitmap.createBitmap(src, x, y, size, size)
    val scaled = if (size == outPx) cropped else Bitmap.createScaledBitmap(cropped, outPx, outPx, true)
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
    if (scaled !== cropped) scaled.recycle()
    if (cropped !== src) cropped.recycle()
    return out.toByteArray()
}
