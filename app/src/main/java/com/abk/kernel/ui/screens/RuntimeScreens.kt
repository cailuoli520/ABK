@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.data.model.AbkRuntimeBuildInfo
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel

@Composable
fun RuntimeHomeScreen(
    vm: MainViewModel,
    onSwitchToClassic: () -> Unit
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "首页",
                actions = {
                    IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新运行态信息")
                    }
                    IconButton(onClick = onSwitchToClassic) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "切换到完整导航")
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
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuntimeStatusHeader(
                runtimeStatus = state.abkRuntimeStatus,
                loading = state.abkRuntimeLoading,
                onGrantRoot = vm::requestRoot,
                onRefresh = vm::refreshAbkRuntimeStatus
            )

            state.abkRuntimeStatus?.let { runtimeStatus ->
                RuntimeBuildParametersCard(runtimeStatus)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun InstalledModulesScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(compareBy<AbkRuntimeModule> { !it.controllable }.thenBy { it.displayName().lowercase() })
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    Scaffold(
        containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
        topBar = {
            ExpressiveTopBar(
                title = "已安装模块",
                actions = {
                    IconButton(onClick = { vm.refreshAbkRuntimeStatus() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新已安装模块")
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
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuntimeModuleSearchField(query, onValueChange = { query = it })

            if (state.abkRuntimeLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.abkRuntimeError?.let {
                RuntimeErrorCard(
                    message = if (state.abkRuntimeStatus == null) "管理器未激活" else "操作未完成，请刷新后重试",
                    onGrantRoot = vm::requestRoot,
                    onRefresh = vm::refreshAbkRuntimeStatus
                )
            }

            if (state.abkRuntimeStatus != null && modules.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "当前内核没有上报 ABK 外部模块" else "没有匹配的模块",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                modules.forEach { module ->
                    InstalledRuntimeModuleCard(
                        module = module,
                        actionInFlight = state.abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> vm.setAbkRuntimeModuleEnabled(module.id, enabled) }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun RuntimeStatusHeader(
    runtimeStatus: AbkRuntimeStatus?,
    loading: Boolean,
    onGrantRoot: () -> Unit,
    onRefresh: () -> Unit
) {
    ExpressiveHeroCard(
        title = if (runtimeStatus != null) "管理器已激活" else "管理器未激活",
        subtitle = runtimeStatus?.let {
            "ABK ${it.abkVersion.ifBlank { "unknown" }} · ${it.modules.size} 个模块"
        } ?: "安装并启用支持 ABK Control 的内核后可查看运行态信息",
        icon = if (runtimeStatus != null) Icons.Default.CheckCircle else Icons.Default.Memory,
        containerColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (runtimeStatus != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        badge = {
            runtimeStatus?.let {
                ExpressiveStatusChip(
                    label = "schema ${it.schema}",
                    icon = Icons.Default.Tune,
                    color = MaterialTheme.colorScheme.primary
                )
                if (it.abkCommit.isNotBlank()) {
                    ExpressiveStatusChip(
                        label = it.abkCommit,
                        icon = Icons.Default.Memory,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    ) {
        if (runtimeStatus == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onGrantRoot,
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("授权 Root")
                }
                Button(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeBuildParametersCard(runtimeStatus: AbkRuntimeStatus) {
    val build = runtimeStatus.build
    ExpressiveSectionCard(
        title = "当前内核编译参数",
        subtitle = "来自 /dev/abk_control 的编译时信息",
        icon = Icons.Default.Tune
    ) {
        if (build == null) {
            Text(
                text = "当前设备输出为旧版 schema，未包含编译参数。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ExpressiveSectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            RuntimeInfoRow("Android", build.androidVersion)
            RuntimeInfoRow("内核", listOf(build.kernelVersion, build.subLevel).filter { it.isNotBlank() }.joinToString("."))
            RuntimeInfoRow("补丁级别", build.osPatchLevel)
            RuntimeInfoRow("修订版本", build.revision)
            RuntimeInfoRow("KSU", listOf(build.kernelsuVariant, build.kernelsuBranch).filter { it.isNotBlank() }.joinToString(" / "))
            RuntimeInfoRow("内核版本名", build.version)
            RuntimeInfoRow("构建时间", build.buildTime)
            RuntimeInfoRow("虚拟化", build.virtualizationSupport)
            RuntimeInfoRow("ZRAM 额外算法", build.zramExtraAlgos)
            RuntimeInfoRow("ABK", listOf(runtimeStatus.abkVersion, runtimeStatus.abkCommit).filter { it.isNotBlank() }.joinToString(" · "))
            RuntimeFeatureChips(build)
        }
    }
}

@Composable
private fun RuntimeFeatureChips(build: AbkRuntimeBuildInfo) {
    val features = build.features
        .filterValues { it }
        .keys
        .map(::runtimeFeatureLabel)
        .ifEmpty { listOf("基础配置") }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        features.forEach { feature ->
            AssistChip(
                onClick = {},
                label = { Text(feature) },
                enabled = false
            )
        }
    }
}

@Composable
private fun RuntimeInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(82.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RuntimeErrorCard(
    message: String,
    onGrantRoot: () -> Unit,
    onRefresh: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.errorContainer)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGrantRoot, modifier = Modifier.weight(1f)) {
                    Text("授权 Root")
                }
                Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("刷新")
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleSearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text("搜索已安装模块") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun InstalledRuntimeModuleCard(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = module.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (module.version.isNotBlank()) {
                        Text(
                            text = "版本: ${module.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (module.controllable) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (module.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RuntimeModuleChip(module.id.ifBlank { module.repoName() })
                if (module.stage.isNotBlank()) RuntimeModuleChip(module.stage, secondary = true)
                RuntimeModuleChip(if (module.enabled) "已启用" else "已关闭", secondary = !module.enabled)
                RuntimeModuleChip(if (module.controllable) "可控制" else "仅元数据", secondary = !module.controllable)
            }

            if (module.repoUrl.isNotBlank()) {
                Text(
                    text = module.repoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (module.controllable) {
                Button(
                    onClick = { onSetEnabled(!module.enabled) },
                    enabled = !actionInFlight,
                    modifier = Modifier.align(Alignment.End).height(40.dp)
                ) {
                    if (actionInFlight) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (module.enabled) "关闭" else "启用")
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeModuleChip(label: String, secondary: Boolean = false) {
    val leadingIcon: @Composable (() -> Unit)? = if (!secondary) {
        { Icon(Icons.Default.Extension, null, modifier = Modifier.size(15.dp)) }
    } else {
        null
    }
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = leadingIcon,
        enabled = false
    )
}

private fun AbkRuntimeModule.matchesRuntimeModuleQuery(query: String): Boolean {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return true
    return listOf(id, name, version, description, repoUrl, stage)
        .any { it.contains(cleanQuery, ignoreCase = true) }
}

private fun AbkRuntimeModule.displayName(): String =
    name.ifBlank { id.ifBlank { repoName() } }

private fun AbkRuntimeModule.repoName(): String =
    repoUrl
        .trim()
        .trimEnd('/')
        .removeSuffix(".git")
        .substringAfterLast('/')
        .ifBlank { "unknown" }

private fun runtimeFeatureLabel(key: String): String = when (key) {
    "use_zram" -> "ZRAM"
    "use_bbg" -> "BBG"
    "use_ddk" -> "DDK"
    "use_ntsync" -> "NTsync"
    "use_networking" -> "网络增强"
    "use_kpm" -> "KPM"
    "use_rekernel" -> "Re-Kernel"
    "enable_susfs" -> "SUSFS"
    "supp_op" -> "SukiSU SUS_SU"
    "zram_full_algo" -> "ZRAM 完整算法"
    "cancel_susfs" -> "SUSFS 已取消"
    else -> key
}
