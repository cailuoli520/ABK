package com.abk.kernel.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var config by remember { mutableStateOf(KernelBuildConfig()) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Build, null) },
            title = { Text("确认提交构建") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("构建配置概览：", fontWeight = FontWeight.SemiBold)
                    Text("Android ${config.androidVersion} · 内核 ${config.kernelVersion}.${config.subLevel}")
                    Text("KSU: ${config.kernelsuVariant} (${config.kernelsuBranch})")
                    Text("补丁级别: ${config.osPatchLevel}")
                    Text("SUSFS: ${if (!config.cancelSusfs) "启用" else "禁用"} · ZRAM: ${if (config.useZram) "启用" else "禁用"} · KPM: ${if (config.useKpm) "启用" else "禁用"}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    vm.dispatchBuild(config)
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.build_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Build status banner
            if (state.buildStatus != BuildStatus.IDLE) {
                BuildStatusBanner(state.buildStatus)
            }

            // ── 内核版本配置 ──────────────────────────────────────────────
            SectionCard(title = "内核版本配置") {
                DropdownField(
                    label = "Android 版本",
                    value = config.androidVersion,
                    options = listOf("android12", "android13", "android14", "android15", "android16"),
                    onSelect = { config = config.copy(androidVersion = it) }
                )
                DropdownField(
                    label = "内核版本",
                    value = config.kernelVersion,
                    options = listOf("5.10", "5.15", "6.1", "6.6", "6.12"),
                    onSelect = { config = config.copy(kernelVersion = it) }
                )
                OutlinedTextField(
                    value = config.subLevel,
                    onValueChange = { config = config.copy(subLevel = it) },
                    label = { Text("子版本号") },
                    placeholder = { Text("如: 66, 198") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.osPatchLevel,
                    onValueChange = { config = config.copy(osPatchLevel = it) },
                    label = { Text("安全补丁级别") },
                    placeholder = { Text("如: 2022-01, lts") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (config.kernelVersion == "5.10") {
                    OutlinedTextField(
                        value = config.revision,
                        onValueChange = { config = config.copy(revision = it) },
                        label = { Text("修订版本 (5.10 专用)") },
                        placeholder = { Text("如: r11") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── KernelSU 配置 ────────────────────────────────────────────
            SectionCard(title = "KernelSU 配置") {
                DropdownField(
                    label = "KernelSU 变体",
                    value = config.kernelsuVariant,
                    options = listOf("Official", "SukiSU", "ReSukiSU"),
                    onSelect = { config = config.copy(kernelsuVariant = it) }
                )
                DropdownField(
                    label = "KSU 分支",
                    value = config.kernelsuBranch,
                    options = listOf("Stable(标准)", "Dev(开发)", "Other(其他/指定)"),
                    onSelect = { config = config.copy(kernelsuBranch = it) }
                )
            }

            // ── 功能开关 ─────────────────────────────────────────────────
            SectionCard(title = "功能开关") {
                SwitchRow("启用 SUSFS", !config.cancelSusfs) {
                    config = config.copy(cancelSusfs = !it)
                }
                SwitchRow("启用 ZRAM 增强算法", config.useZram) {
                    config = config.copy(useZram = it)
                }
                SwitchRow("启用 BBG 防格机", config.useBbg) {
                    config = config.copy(useBbg = it)
                }
                SwitchRow("启用 KPM 功能", config.useKpm) {
                    config = config.copy(useKpm = it)
                }
                SwitchRow("启用 Re-Kernel 驱动 (测试)", config.useRekernel) {
                    config = config.copy(useRekernel = it)
                }
                SwitchRow("启用一加 8E 支持", config.suppOp) {
                    config = config.copy(suppOp = it)
                }
            }

            // ── ZRAM 扩展选项 ────────────────────────────────────────────
            AnimatedVisibility(config.useZram) {
                SectionCard(title = "ZRAM 扩展选项") {
                    SwitchRow("启用完整算法支持 (LZO/LZ4/ZSTD 等)", config.zramFullAlgo) {
                        config = config.copy(zramFullAlgo = it)
                    }
                    if (!config.zramFullAlgo) {
                        OutlinedTextField(
                            value = config.zramExtraAlgos,
                            onValueChange = { config = config.copy(zramExtraAlgos = it) },
                            label = { Text("自定义 ZRAM 算法") },
                            placeholder = { Text("如: lzo,lz4,deflate,zstd") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // ── KPM 扩展选项 ─────────────────────────────────────────────
            AnimatedVisibility(config.useKpm) {
                SectionCard(title = "KPM 扩展选项") {
                    OutlinedTextField(
                        value = config.kpmPassword,
                        onValueChange = { config = config.copy(kpmPassword = it) },
                        label = { Text("KPM 超级密码 (可选)") },
                        placeholder = { Text("留空使用默认密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── 可选配置 ─────────────────────────────────────────────────
            SectionCard(title = "可选配置") {
                OutlinedTextField(
                    value = config.version,
                    onValueChange = { config = config.copy(version = it) },
                    label = { Text("自定义版本名 (可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.buildTime,
                    onValueChange = { config = config.copy(buildTime = it) },
                    label = { Text("自定义构建时间 (可选)") },
                    placeholder = { Text("留空=当前 UTC 时间") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Submit button
            Button(
                onClick = { showConfirmDialog = true },
                enabled = !state.isLoading && state.buildStatus !in listOf(
                    BuildStatus.QUEUED, BuildStatus.IN_PROGRESS
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.RocketLaunch, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.build_submit))
                }
            }

            // Error
            state.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearError() }) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun BuildStatusBanner(status: BuildStatus) {
    val (icon, text, color) = when (status) {
        BuildStatus.QUEUED -> Triple(Icons.Default.Queue, "构建已排队，等待运行…", MaterialTheme.colorScheme.tertiary)
        BuildStatus.IN_PROGRESS -> Triple(Icons.Default.RunCircle, "构建进行中…", MaterialTheme.colorScheme.secondary)
        BuildStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, "构建成功！", MaterialTheme.colorScheme.primary)
        BuildStatus.FAILURE -> Triple(Icons.Default.Error, "构建失败", MaterialTheme.colorScheme.error)
        BuildStatus.CANCELLED -> Triple(Icons.Default.Cancel, "构建已取消", MaterialTheme.colorScheme.outline)
        else -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (status == BuildStatus.IN_PROGRESS) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
