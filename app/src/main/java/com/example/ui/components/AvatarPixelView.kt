package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.example.model.AvatarNative
import com.example.orb.AvatarDnaWire
import kotlinx.coroutines.delay

/** Real shared C indexed framebuffer, converted from RGB565 and scaled nearest-neighbor. */
@Composable
fun AvatarPixelView(hex: String, size: Dp, modifier: Modifier = Modifier) {
    var elapsed by remember(hex) { mutableFloatStateOf(0f) }
    LaunchedEffect(hex) { while (true) { delay(100); elapsed += 0.1f } }
    val image = remember(hex, elapsed) {
        runCatching {
            val pixels = AvatarNative.frame(AvatarDnaWire.decode(hex), 1, elapsed) ?: return@runCatching null
            Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }
    Canvas(modifier.size(size)) {
        image?.let { drawImage(it, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.None) }
    }
}

/** Accepted production C frames for each installed built-in ID; never a stand-in shape. */
@Composable
fun AcceptedAvatarPixelView(id: String, size: Dp, modifier: Modifier = Modifier, mode: Int = 0) {
    val context = LocalContext.current
    var frame by remember(id, mode) { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(id, mode) { while (true) { delay(160); frame = (frame + 1) % 4 } }
    val image = remember(context, id, mode, frame) {
        AcceptedAvatarFrames.frameOrNull(context, id, mode, frame)?.let {
            Bitmap.createBitmap(it, 64, 64, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
    Canvas(modifier.size(size)) {
        image?.let { drawImage(it, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.None) }
    }
}
