@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackMotionHost(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.(requestBack: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val dragDistance = remember { Animatable(0f) }
    var widthPx by remember { mutableStateOf(1f) }
    var activeDirection by remember { mutableStateOf(PredictiveBackDirection.FromLeft) }
    var closing by remember { mutableStateOf(false) }

    fun runBack(direction: PredictiveBackDirection = activeDirection) {
        if (closing) return
        if (!enabled) {
            onBack()
            return
        }
        closing = true
        activeDirection = direction
        scope.launch {
            dragDistance.animateTo(
                targetValue = widthPx * 1.08f,
                animationSpec = motionScheme.fastSpatialSpec()
            )
            onBack()
        }
    }

    fun resetDrag() {
        scope.launch {
            dragDistance.animateTo(0f, animationSpec = motionScheme.defaultSpatialSpec())
        }
    }

    BackHandler {
        runBack(PredictiveBackDirection.FromLeft)
    }

    val dragProgress = (dragDistance.value / (widthPx * 0.36f)).coerceIn(0f, 1f)
    val signedOffset = activeDirection.sign * dragDistance.value
    val cornerRadius = (30f * dragProgress).dp
    val scrimAlpha = 0.46f * dragProgress

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, edgeWidthPx, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset: Offset ->
                        activeDirection = if (!enabled || closing) {
                            PredictiveBackDirection.None
                        } else {
                            when {
                                offset.x <= edgeWidthPx -> PredictiveBackDirection.FromLeft
                                offset.x >= widthPx - edgeWidthPx -> PredictiveBackDirection.FromRight
                                else -> PredictiveBackDirection.None
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (enabled && !closing && activeDirection != PredictiveBackDirection.None) {
                            val signedDelta = activeDirection.sign * dragAmount
                            val next = (dragDistance.value + signedDelta).coerceIn(0f, widthPx * 1.08f)
                            scope.launch { dragDistance.snapTo(next) }
                        }
                    },
                    onDragEnd = {
                        if (enabled && !closing && activeDirection != PredictiveBackDirection.None) {
                            if (dragDistance.value >= widthPx * 0.28f) {
                                runBack(activeDirection)
                            } else {
                                resetDrag()
                            }
                        }
                    },
                    onDragCancel = {
                        if (!closing) resetDrag()
                    }
                )
            }
    ) {
        backgroundContent()
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
            )
        }
        PredictiveBackAffordance(
            direction = activeDirection,
            progress = dragProgress
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = signedOffset
                    scaleX = 1f - 0.08f * dragProgress
                    scaleY = 1f - 0.08f * dragProgress
                    shape = RoundedCornerShape(cornerRadius)
                    clip = dragProgress > 0f
                }
                .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
        ) {
            content { runBack(PredictiveBackDirection.FromLeft) }
        }
    }
}

@Composable
private fun BoxScope.PredictiveBackAffordance(
    direction: PredictiveBackDirection,
    progress: Float
) {
    if (direction == PredictiveBackDirection.None || progress <= 0f) return
    val alignment = if (direction == PredictiveBackDirection.FromLeft) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    val icon = if (direction == PredictiveBackDirection.FromLeft) {
        Icons.Default.ArrowForward
    } else {
        Icons.Default.ArrowBack
    }
    Box(
        modifier = Modifier
            .align(alignment)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .width(46.dp)
            .height(96.dp)
            .graphicsLayer {
                alpha = progress
                scaleX = 0.86f + 0.14f * progress
                scaleY = 0.86f + 0.14f * progress
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

private enum class PredictiveBackDirection(val sign: Float) {
    FromLeft(1f),
    FromRight(-1f),
    None(0f)
}
