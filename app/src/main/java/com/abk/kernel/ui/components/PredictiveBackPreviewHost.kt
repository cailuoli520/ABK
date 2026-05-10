@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackPreviewHost(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.(requestBack: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val maxPreviewOffsetPx = with(density) { 34.dp.toPx() }
    val previewProgress = remember { Animatable(0f) }
    val pageOffset = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    var activeDirection by remember { mutableStateOf(PredictiveBackSide.Right) }
    var closing by remember { mutableStateOf(false) }

    fun completeBack(direction: PredictiveBackSide = activeDirection) {
        if (closing) return
        if (!enabled) {
            onBack()
            return
        }
        closing = true
        activeDirection = direction
        scope.launch {
            launch { previewProgress.animateTo(1f, animationSpec = motionScheme.fastEffectsSpec()) }
            pageOffset.animateTo(widthPx * 1.08f, animationSpec = motionScheme.fastSpatialSpec())
            onBack()
        }
    }

    fun cancelBack() {
        scope.launch {
            launch { previewProgress.animateTo(0f, animationSpec = motionScheme.defaultEffectsSpec()) }
            pageOffset.animateTo(0f, animationSpec = motionScheme.defaultSpatialSpec())
        }
    }

    BackHandler {
        completeBack(PredictiveBackSide.Right)
    }

    val progress = previewProgress.value.coerceIn(0f, 1f)
    val signedOffset = activeDirection.sign * pageOffset.value
    val cornerRadius = (30f * progress).dp
    val scrimAlpha = 0.46f * progress

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    ) {
        backgroundContent()
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            )
        }
        PredictiveBackAffordance(activeDirection, progress)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = signedOffset
                    scaleX = 1f - 0.10f * progress
                    scaleY = 1f - 0.10f * progress
                    shape = RoundedCornerShape(cornerRadius)
                    clip = progress > 0f
                }
                .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
        ) {
            content { completeBack(PredictiveBackSide.Right) }
        }
        PredictiveBackEdge(
            side = PredictiveBackSide.Left,
            enabled = enabled,
            closing = closing,
            widthPx = widthPx,
            onDirectionChange = { activeDirection = it },
            onProgressDelta = { signedDelta ->
                scope.launch {
                    val nextProgress = (previewProgress.value + signedDelta / (widthPx * 0.34f))
                        .coerceIn(0f, 1f)
                    previewProgress.snapTo(nextProgress)
                    pageOffset.snapTo(maxPreviewOffsetPx * nextProgress)
                }
            },
            onRelease = {
                if (previewProgress.value >= 0.46f) completeBack(activeDirection) else cancelBack()
            },
            onCancel = ::cancelBack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        PredictiveBackEdge(
            side = PredictiveBackSide.Right,
            enabled = enabled,
            closing = closing,
            widthPx = widthPx,
            onDirectionChange = { activeDirection = it },
            onProgressDelta = { signedDelta ->
                scope.launch {
                    val nextProgress = (previewProgress.value + signedDelta / (widthPx * 0.34f))
                        .coerceIn(0f, 1f)
                    previewProgress.snapTo(nextProgress)
                    pageOffset.snapTo(maxPreviewOffsetPx * nextProgress)
                }
            },
            onRelease = {
                if (previewProgress.value >= 0.46f) completeBack(activeDirection) else cancelBack()
            },
            onCancel = ::cancelBack,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PredictiveBackEdge(
    side: PredictiveBackSide,
    enabled: Boolean,
    closing: Boolean,
    widthPx: Float,
    onDirectionChange: (PredictiveBackSide) -> Unit,
    onProgressDelta: (Float) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(34.dp)
            .pointerInput(enabled, closing, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        onDirectionChange(if (enabled && !closing) side else PredictiveBackSide.None)
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (enabled && !closing) {
                            val signedDelta = side.sign * dragAmount
                            onProgressDelta(signedDelta)
                        }
                    },
                    onDragEnd = {
                        if (enabled && !closing) {
                            onRelease()
                        }
                    },
                    onDragCancel = {
                        if (!closing) onCancel()
                    }
                )
            }
    )
}

@Composable
private fun BoxScope.PredictiveBackAffordance(
    side: PredictiveBackSide,
    progress: Float
) {
    if (side == PredictiveBackSide.None || progress <= 0f) return
    val alignment = if (side == PredictiveBackSide.Right) Alignment.CenterEnd else Alignment.CenterStart
    val icon = if (side == PredictiveBackSide.Right) Icons.Default.ArrowBack else Icons.Default.ArrowForward
    Box(
        modifier = Modifier
            .align(alignment)
            .navigationBarsPadding()
            .padding(horizontal = 0.dp)
            .width(48.dp)
            .height(110.dp)
            .graphicsLayer {
                alpha = progress
                scaleX = 0.88f + 0.12f * progress
                scaleY = 0.88f + 0.12f * progress
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "返回",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

private enum class PredictiveBackSide(val sign: Float) {
    Left(1f),
    Right(-1f),
    None(0f)
}
