@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.LspInstalledModule
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveListItem
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

@Composable
fun LspManagerHomeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onOpenManagerSurfacePicker: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshAbkRuntimeStatus()
        vm.refreshLspInstalledModules()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 管理器",
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
        val bridge = state.lspBridgeStatus
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveHeroCard(
                title = state.abkRuntimeStatus?.manager?.displayName?.ifBlank { "ABK LSP Bridge" } ?: "ABK LSP Bridge",
                subtitle = state.abkRuntimeStatus?.runtimeBackend?.version?.ifBlank { "未提供版本" } ?: "未连接到 LSP bridge",
                icon = Icons.Default.Memory,
                badge = {
                    ExpressiveStatusChip(
                        label = state.abkRuntimeStatus?.runtimeBackend?.backend?.ifBlank { "lsp_bridge" } ?: "inactive",
                        icon = Icons.Default.Tune,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ExpressiveStatusChip(
                        label = if (bridge?.safeMode == true) "安全模式" else "桥接活动",
                        icon = Icons.Default.Security,
                        color = if (bridge?.safeMode == true) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    ExpressiveStatusChip(
                        label = "模块 ${state.lspInstalledModules.count { it.enabled }} / ${state.lspInstalledModules.size}",
                        icon = Icons.Default.Extension,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {}

            if (state.abkRuntimeLoading || state.lspInstalledModulesLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ExpressiveSectionCard(
                title = "Bridge 状态",
                subtitle = "当前 LSP bridge / helper / daemon 运行状态",
                icon = Icons.Default.Tune
            ) {
                LspStatusLine("安全模式", if (bridge?.safeMode == true) "开启" else "关闭")
                LspStatusLine("Helper", if (bridge?.helperActive == true) "active" else "inactive")
                LspStatusLine("Daemon", if (bridge?.daemonActive == true) "active" else "inactive")
                LspStatusLine("Zygote", if (bridge?.zygoteAttached == true) "attached" else "detached")
                LspStatusLine("Payload", if (bridge?.payloadReady == true) "ready" else "not ready")
                LspStatusLine("Runtime", if (bridge?.runtimeReady == true) "ready" else "not ready")
                LspStatusLine("模块", "${bridge?.managedModuleCount ?: 0}")
                LspStatusLine("作用域", "${bridge?.scopeCount ?: 0}")
                LspStatusLine("目标进程", "${bridge?.targetStateCount ?: 0}")
                LspStatusLine("已加载", "${bridge?.loadedModuleCount ?: 0}")
                LspStatusLine("Hook", "${bridge?.activeHookCount ?: 0}")
                if (bridge != null && !bridge.runtimeReady) {
                    Text(
                        text = "配置启用不等于模块已激活；目标应用内显示已激活需要 payload 注入并完成 ART/Xposed hook。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!bridge?.lastError.isNullOrBlank()) {
                    Text(
                        text = "最近错误: ${bridge?.lastError}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (bridge?.diagnostics?.isNotEmpty() == true) {
                    Text(
                        text = bridge.diagnostics.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.abkRuntimeError != null) {
                    Text(
                        text = state.abkRuntimeError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = vm::syncLspBridgeConfiguration,
                    enabled = !state.abkRuntimeLoading
                ) {
                    Text("同步模块配置到 Bridge")
                }
            }

            ExpressiveSectionCard(
                title = "模块概览",
                subtitle = "已识别的 LSPosed/Xposed 模块与当前启用态",
                icon = Icons.Default.Extension
            ) {
                val enabledCount = state.lspInstalledModules.count { it.enabled }
                Text(
                    text = "已识别 ${state.lspInstalledModules.size} 个模块，当前启用 $enabledCount 个。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.lspInstalledModules.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text("作用域 ${bridge?.scopeCount ?: 0}") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("插件 ${bridge?.pluginCount ?: 0}") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspModulesScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onOpenScope: (String) -> Unit
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshLspInstalledModules()
        vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 模块",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        vm.refreshLspInstalledModules()
                        vm.refreshAbkRuntimeStatus()
                    }) {
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
            if (state.lspInstalledModulesLoading || state.abkRuntimeLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.lspInstalledModulesError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.lspInstalledModules.isEmpty() && !state.lspInstalledModulesLoading) {
                Text(
                    text = "未发现兼容的 LSPosed/Xposed 模块 APK",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.lspInstalledModules.forEach { module ->
                LspModuleCard(
                    module = module,
                    actionInFlight = state.lspModuleActionPackage == module.packageName,
                    onEnabledChange = { enabled -> vm.setLspModuleEnabled(module.packageName, enabled) },
                    onOpenScope = {
                        vm.setSelectedLspModulePackage(module.packageName)
                        onOpenScope(module.packageName)
                    }
                )
            }
            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspScopeScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshLspInstalledModules()
        vm.refreshLspScopeApps()
        vm.refreshAbkRuntimeStatus()
    }

    val modules = state.lspInstalledModules
    val selectedModule = modules.firstOrNull { it.packageName == state.selectedLspModulePackage } ?: modules.firstOrNull()
    var draftScope by remember(selectedModule?.packageName, selectedModule?.selectedScope) {
        mutableStateOf(selectedModule?.selectedScope?.toSet().orEmpty())
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 作用域",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        vm.refreshLspInstalledModules()
                        vm.refreshLspScopeApps()
                        vm.refreshAbkRuntimeStatus()
                    }) {
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
            ExpressiveSectionCard(
                title = "模块选择",
                subtitle = "先选一个模块，再编辑它的作用域",
                icon = Icons.Default.Extension
            ) {
                if (modules.isEmpty()) {
                    Text(
                        text = "当前没有可配置作用域的模块。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    modules.forEach { module ->
                        ExpressiveListItem(
                            title = module.label.ifBlank { module.packageName },
                            subtitle = buildString {
                                append(module.packageName)
                                append("\n当前作用域 ${module.selectedScope.size} 项")
                                if (module.scopeHints.isNotEmpty()) {
                                    append(" · 推荐 ${module.scopeHints.joinToString(", ")}")
                                }
                            },
                            leadingIcon = Icons.Default.Tune,
                            selected = selectedModule?.packageName == module.packageName,
                            onClick = { vm.setSelectedLspModulePackage(module.packageName) }
                        )
                    }
                }
            }

            if (state.lspScopeAppsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            selectedModule?.let { module ->
                ExpressiveSectionCard(
                    title = module.label.ifBlank { module.packageName },
                    subtitle = "编辑 ${module.packageName} 的实际作用域",
                    icon = Icons.Default.Tune
                ) {
                    Text(
                        text = if (module.scopeHints.isEmpty()) {
                            "该模块未声明推荐作用域。"
                        } else {
                            "推荐作用域: ${module.scopeHints.joinToString(", ")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (module.lastError.isNotBlank()) {
                        Text(
                            text = "模块错误: ${module.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val nextScope = (draftScope + module.scopeHints).filter { it.isNotBlank() }.toSet()
                                draftScope = nextScope
                                vm.setLspModuleScope(module.packageName, nextScope)
                            },
                            enabled = state.lspModuleActionPackage == null && module.scopeHints.isNotEmpty()
                        ) {
                            Text("勾选推荐")
                        }
                        OutlinedButton(
                            onClick = {
                                draftScope = emptySet()
                                vm.setLspModuleScope(module.packageName, emptySet())
                            },
                            enabled = state.lspModuleActionPackage == null && draftScope.isNotEmpty()
                        ) {
                            Text("清空并保存")
                        }
                    }
                }

                state.lspScopeApps.forEach { app ->
                    val checked = app.packageName in draftScope
                    ExpressiveListItem(
                        title = app.label.ifBlank { app.packageName },
                        subtitle = buildString {
                            append(app.packageName)
                            append(if (app.isSystemApp) "\n系统应用" else "\n用户应用")
                        },
                        leadingIcon = if (app.isSystemApp) Icons.Default.Security else Icons.Default.Extension,
                        trailingContent = {
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    val nextScope = if (enabled) {
                                        draftScope + app.packageName
                                    } else {
                                        draftScope - app.packageName
                                    }
                                    draftScope = nextScope
                                    vm.setLspModuleScope(module.packageName, nextScope)
                                },
                                enabled = state.lspModuleActionPackage == null
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
fun LspLogsScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "LSP 日志",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = vm::refreshAbkRuntimeStatus) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.runtime_refresh))
                    }
                }
            )
        }
    ) { padding ->
        val bridge = state.lspBridgeStatus
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AbkScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveSectionCard(
                title = "Runtime Diagnostics",
                subtitle = "bridge / helper / daemon 当前诊断信息",
                icon = Icons.Default.Article
            ) {
                val diagnostics = buildList {
                    addAll(bridge?.diagnostics.orEmpty())
                    addAll(state.abkRuntimeStatus?.manager?.diagnostics.orEmpty())
                    addAll(state.abkRuntimeStatus?.runtimeBackend?.diagnostics.orEmpty())
                }.distinct()
                Text(
                    text = diagnostics.joinToString("\n").ifBlank { state.abkRuntimeError ?: "暂无可用诊断" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ExpressiveSectionCard(
                title = "Bridge Logs",
                subtitle = "最近一次 bridge 操作与状态变更日志",
                icon = Icons.Default.Article
            ) {
                Text(
                    text = bridge?.logs?.joinToString("\n").orEmpty().ifBlank { "暂无 bridge 日志" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bridge?.targetStates?.isNotEmpty() == true) {
                ExpressiveSectionCard(
                    title = "Target Runtime",
                    subtitle = "目标进程内 payload / runtime / hook 状态",
                    icon = Icons.Default.Memory
                ) {
                    Text(
                        text = bridge.targetStates.joinToString("\n") { target ->
                            buildString {
                                append(target.packageName.ifBlank { "unknown" })
                                append(" / ")
                                append(target.processName.ifBlank { target.packageName })
                                append(" pid=")
                                append(target.pid)
                                append(" payload=")
                                append(if (target.payloadInjected) "yes" else "no")
                                append(" runtime=")
                                append(if (target.runtimeReady) "ready" else "no")
                                append(" hooks=")
                                append(target.activeHookCount)
                                if (target.lastError.isNotBlank()) {
                                    append(" error=")
                                    append(target.lastError)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.lspInstalledModules.filter { it.lastError.isNotBlank() }.forEach { module ->
                ExpressiveSectionCard(
                    title = module.label.ifBlank { module.packageName },
                    subtitle = module.packageName,
                    icon = Icons.Default.Extension
                ) {
                    Text(
                        text = module.lastError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun LspModuleCard(
    module: LspInstalledModule,
    actionInFlight: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onOpenScope: () -> Unit
) {
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
                append("\n当前状态: ")
                append(if (module.enabled) "配置已启用" else "未启用")
                append(" · ")
                append(if (module.loaded) "runtime 已加载" else "runtime 未加载")
                append(" · ")
                append(if (module.hookActive) "hook 活动" else "hook 未确认")
                append("\n作用域: ")
                append(if (module.selectedScope.isEmpty()) "未配置" else "${module.selectedScope.size} 项")
                if (module.entryPoints.isNotEmpty()) {
                    append("\n入口: ")
                    append(module.entryPoints.joinToString(", "))
                }
                if (module.compatEntryPoints.isNotEmpty()) {
                    append("\n兼容入口: ")
                    append(module.compatEntryPoints.joinToString(", "))
                }
                if (module.staticScope) {
                    append("\nStatic scope: true")
                }
                if (module.scopeHints.isNotEmpty()) {
                    append("\n推荐 Scope: ")
                    append(module.scopeHints.joinToString(", "))
                }
                if (module.description.isNotBlank()) {
                    append("\n")
                    append(module.description)
                }
                if (module.lastError.isNotBlank()) {
                    append("\n最近错误: ")
                    append(module.lastError)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEnabledChange(!module.enabled) },
                enabled = !actionInFlight
            ) {
                Text(if (module.enabled) "禁用" else "启用")
            }
            OutlinedButton(
                onClick = onOpenScope,
                enabled = !actionInFlight
            ) {
                Text("管理作用域")
            }
        }
    }
}

@Composable
private fun LspStatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
