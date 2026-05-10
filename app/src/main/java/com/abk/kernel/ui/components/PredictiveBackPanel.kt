@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlinx.coroutines.launch

@Composable
fun BoxScope.PredictiveBackPanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    predictiveBackEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val motionScheme = MaterialTheme.motionScheme
    val openness = remember { Animatable(if (predictiveBackEnabled) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }

    fun closePanel() {
        if (!dismissEnabled || closing) return
        if (!predictiveBackEnabled) {
            onDismiss()
            return
        }
        closing = true
        scope.launch {
            openness.animateTo(0f, animationSpec = motionScheme.fastSpatialSpec())
            onDismiss()
        }
    }

    LaunchedEffect(predictiveBackEnabled) {
        if (predictiveBackEnabled) {
            openness.snapTo(0f)
            openness.animateTo(1f, animationSpec = motionScheme.defaultSpatialSpec())
        } else {
            openness.snapTo(1f)
        }
    }

    BackHandler(enabled = dismissEnabled) {
        closePanel()
    }

    val open = openness.value
    val closeProgress = 1f - open
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.scrim.copy(alpha = 0.54f * open))
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .graphicsLayer {
                    translationX = size.width * 0.42f * closeProgress
                    scaleX = 0.92f + 0.08f * open
                    scaleY = 0.92f + 0.08f * open
                    alpha = 0.72f + 0.28f * open
                }
                .pointerInput(dismissEnabled, predictiveBackEnabled) {
                    if (dismissEnabled && predictiveBackEnabled) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val next = (openness.value - dragAmount / width).coerceIn(0f, 1f)
                                scope.launch { openness.snapTo(next) }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (openness.value < 0.64f) {
                                        closing = true
                                        openness.animateTo(0f, animationSpec = motionScheme.fastSpatialSpec())
                                        onDismiss()
                                    } else {
                                        openness.animateTo(1f, animationSpec = motionScheme.defaultSpatialSpec())
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    openness.animateTo(1f, animationSpec = motionScheme.defaultSpatialSpec())
                                }
                            }
                        )
                    }
                },
            shape = RoundedCornerShape(30.dp),
            color = uiSurfaceColor(colorScheme.surface),
            contentColor = colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.42f))
        ) {
            Column(Modifier.fillMaxSize()) {
                PredictivePanelHeader(
                    title = title,
                    subtitle = subtitle,
                    dismissEnabled = dismissEnabled,
                    onDismiss = ::closePanel,
                    actions = actions
                )
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.38f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PredictivePanelHeader(
    title: String,
    subtitle: String?,
    dismissEnabled: Boolean,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 18.dp, end = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onDismiss,
            enabled = dismissEnabled,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}
