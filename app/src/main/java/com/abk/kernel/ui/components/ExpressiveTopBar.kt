@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.abk.kernel.ui.theme.uiSurfaceColor

val AbkScreenHorizontalPadding: Dp = 24.dp

@Composable
fun ExpressiveFlexibleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    compactTitle: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    LargeFlexibleTopAppBar(
        modifier = modifier,
        title = {
            val titleStyle = if (compactTitle) {
                val baseStyle = LocalTextStyle.current
                if (baseStyle.fontSize.isSpecified && baseStyle.fontSize.value > 32f) {
                    baseStyle.copy(
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp
                    )
                } else {
                    baseStyle.copy(letterSpacing = 0.sp)
                }
            } else {
                LocalTextStyle.current.copy(letterSpacing = 0.sp)
            }
            Text(
                text = title,
                style = titleStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            navigationIcon?.invoke()
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
            scrolledContainerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface)
        ),
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun ExpressiveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    largeTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val hasNavigation = navigationIcon != null
    val useLargeTitle = largeTitle || !hasNavigation
    val titleStyle = if (useLargeTitle) {
        MaterialTheme.typography.headlineLarge.copy(
            fontSize = 44.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = if (hasNavigation) 4.dp else AbkScreenHorizontalPadding,
                    top = if (useLargeTitle) 18.dp else 10.dp,
                    end = AbkScreenHorizontalPadding,
                    bottom = if (useLargeTitle) 18.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = titleStyle,
                maxLines = if (useLargeTitle) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}
