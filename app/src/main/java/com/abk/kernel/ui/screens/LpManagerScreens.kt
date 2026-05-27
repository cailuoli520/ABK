@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

@Composable
fun LpManagerHomeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onOpenManagerSurfacePicker: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshAbkRuntimeStatus()
        vm.refreshLpInstalledModules()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LP 管理器",
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refreshAbkRuntimeStatus) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                    IconButton(onClick = onOpenManagerSurfacePicker) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换管理侧")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val runtime = state.abkRuntimeStatus
            ExpressiveHeroCard(
                title = runtime?.manager?.displayName?.ifBlank { "ABK LSP Bridge" } ?: "ABK LSP Bridge",
                subtitle = runtime?.runtimeBackend?.version?.ifBlank { "未提供版本" } ?: "未连接到 LP bridge",
                icon = Icons.Default.Memory,
                badge = {
                    ExpressiveStatusChip(
                        label = runtime?.runtimeBackend?.backend?.ifBlank { "lsp_bridge" } ?: "inactive",
                        icon = Icons.Default.Tune,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveStatusChip(
                        label = "模块 ${state.lpInstalledModules.size}",
                        icon = Icons.Default.Extension,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {}

            if (state.abkRuntimeLoading || state.lpInstalledModulesLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ExpressiveSectionCard(
                title = "Bridge 状态",
                subtitle = "当前 LP 管理器内核/桥接状态",
                icon = Icons.Default.Tune
            ) {
                Text(
                    text = runtime?.manager?.diagnostics?.joinToString("\n").orEmpty().ifBlank {
                        state.abkRuntimeError ?: "暂无可用 bridge 诊断"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpressiveSectionCard(
                title = "模块发现",
                subtitle = "当前按 LSPosed/Xposed 模块格式识别到的 APK 数量",
                icon = Icons.Default.Extension
            ) {
                Text(
                    text = state.lpInstalledModulesError ?: "已发现 ${state.lpInstalledModules.size} 个模块 APK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LpModulesScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshLpInstalledModules()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LP 模块",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refreshLpInstalledModules) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.lpInstalledModulesLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.lpInstalledModulesError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.lpInstalledModules.isEmpty() && !state.lpInstalledModulesLoading) {
                Text(
                    text = "未发现兼容的 LSPosed/Xposed 模块 APK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.lpInstalledModules.forEach { module ->
                ExpressiveSectionCard(
                    title = module.label.ifBlank { module.packageName },
                    subtitle = module.packageName,
                    icon = Icons.Default.Extension
                ) {
                    Text(
                        text = buildString {
                            append("Version: ")
                            append(module.versionName.ifBlank { module.versionCode.toString() })
                            append("\nMode: ")
                            append(
                                when {
                                    module.modern && module.legacy -> "Modern + Legacy"
                                    module.modern -> "Modern"
                                    module.legacy -> "Legacy"
                                    else -> "Unknown"
                                }
                            )
                            if (module.scopeHints.isNotEmpty()) {
                                append("\nScope: ")
                                append(module.scopeHints.joinToString(", "))
                            }
                            if (module.description.isNotBlank()) {
                                append("\n")
                                append(module.description)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}
