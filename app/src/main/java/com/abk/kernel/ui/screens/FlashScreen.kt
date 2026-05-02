package com.abk.kernel.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.ui.components.ExpressiveEmptyState
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var flashLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLogDialog by remember { mutableStateOf(false) }

    if (showFlashConfirm && selectedItem != null) {
        val item = selectedItem!!
        AlertDialog(
            onDismissRequest = { showFlashConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.flash_confirm)) },
            text = { Text(stringResource(R.string.flash_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showFlashConfirm = false
                        val result = when (item.type) {
                            ArtifactType.KERNEL_IMG -> RootUtils.flashImage(item.filePath, "boot")
                            ArtifactType.SUSFS_MODULE -> RootUtils.installModule(item.filePath)
                            else -> RootUtils.ShellResult(false, listOf("不支持此文件类型的自动刷写"))
                        }
                        flashLog = result.output
                        showLogDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFlashConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("执行日志") },
            text = {
                Column {
                    flashLog.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.flash_title),
                icon = Icons.Default.FlashOn
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                FlashHero(
                    buildStatus = state.buildStatus,
                    availableCount = state.artifacts.size,
                    downloadedCount = state.downloadedArtifacts.size
                )
            }

            // ── Available artifacts to download ──
            if (state.buildStatus == BuildStatus.SUCCESS && state.artifacts.isNotEmpty()) {
                item {
                    ExpressiveSectionCard(
                        title = "可下载的构建产物",
                        subtitle = "先下载需要的 img、AnyKernel3、管理器或模块，再在下方执行刷写/安装。",
                        icon = Icons.Default.CloudDownload
                    ) {}
                }
                items(state.artifacts, key = { it.id }) { artifact ->
                    ArtifactDownloadCard(
                        artifact = artifact,
                        progress = state.downloadProgress[artifact.id],
                        alreadyDownloaded = state.downloadedArtifacts.any {
                            it.filePath.contains("/${artifact.name}/")
                        },
                        onDownload = { vm.downloadArtifact(artifact) }
                    )
                }
            } else if (state.currentRun != null && state.buildStatus == BuildStatus.SUCCESS && state.artifacts.isEmpty()) {
                item {
                    state.currentRun?.let { run ->
                        OutlinedButton(
                            onClick = { vm.loadArtifacts(run.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.download_artifacts))
                        }
                    }
                }
            }

            // ── Downloaded artifacts ──
            if (state.downloadedArtifacts.isNotEmpty()) {
                item {
                    ExpressiveSectionCard(
                        title = "已下载，选择操作",
                        subtitle = "危险操作会再次确认。AK3 包默认交给恢复环境或文件管理器处理。",
                        icon = Icons.Default.FolderSpecial,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {}
                }
                items(state.downloadedArtifacts, key = { it.id }) { downloaded ->
                    DownloadedArtifactCard(
                        artifact = downloaded,
                        context = context,
                        onFlash = {
                            selectedItem = downloaded
                            showFlashConfirm = true
                        }
                    )
                }
            }

            // Empty state
            if (state.artifacts.isEmpty() && state.downloadedArtifacts.isEmpty()) {
                item {
                    ExpressiveEmptyState(
                        title = "暂无可刷写产物",
                        subtitle = "构建成功并下载后，ABK 会按类型整理这里的操作。",
                        icon = Icons.Default.Inbox
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun FlashHero(
    buildStatus: BuildStatus,
    availableCount: Int,
    downloadedCount: Int
) {
    ExpressiveHeroCard(
        title = "产物中心",
        subtitle = "下载构建输出，并按文件类型选择刷写、安装或交给恢复环境处理。",
        icon = Icons.Default.FlashOn,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = "${availableCount} 个可下载",
                icon = Icons.Default.CloudDownload,
                color = MaterialTheme.colorScheme.tertiary
            )
            ExpressiveStatusChip(
                label = "${downloadedCount} 个已下载",
                icon = Icons.Default.Inventory2,
                color = MaterialTheme.colorScheme.secondary
            )
            ExpressiveStatusChip(
                label = when (buildStatus) {
                    BuildStatus.SUCCESS -> "构建成功"
                    BuildStatus.IN_PROGRESS -> "构建中"
                    BuildStatus.QUEUED -> "排队中"
                    BuildStatus.FAILURE -> "构建失败"
                    BuildStatus.CANCELLED -> "已取消"
                    BuildStatus.IDLE -> "等待构建"
                },
                icon = Icons.Default.RunCircle,
                color = when (buildStatus) {
                    BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        }
    )
}

@Composable
private fun ArtifactDownloadCard(
    artifact: Artifact,
    progress: Int?,
    alreadyDownloaded: Boolean,
    onDownload: () -> Unit
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    val animatedProgress by animateFloatAsState(
        targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
        label = "artifact-download"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    artifactIcon(type),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(artifact.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(DownloadUtils.formatSize(artifact.sizeInBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExpressiveStatusChip(
                    label = artifactTypeLabel(type),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                Text("下载中 $progress%", style = MaterialTheme.typography.labelSmall)
            } else if (!alreadyDownloaded) {
                Button(onClick = onDownload, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("下载")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("已下载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DownloadedArtifactCard(
    artifact: DownloadedArtifact,
    context: Context,
    onFlash: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    artifactIcon(artifact.type),
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(artifact.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        artifactTypeLabel(artifact.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        DownloadUtils.formatSize(artifact.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveStatusChip(
                    label = artifactTypeLabel(artifact.type),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { DownloadUtils.openFile(context, artifact.filePath) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看文件")
                }
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG -> Button(
                        onClick = onFlash,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.flash_kernel))
                    }
                    ArtifactType.ANYKERNEL3 -> Button(
                        onClick = { DownloadUtils.openFile(context, artifact.filePath) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("交给恢复刷入")
                    }
                    ArtifactType.KSU_MANAGER -> Button(
                        onClick = { DownloadUtils.installApk(context, artifact.filePath) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装 APK")
                    }
                    ArtifactType.SUSFS_MODULE -> Button(
                        onClick = onFlash,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Extension, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.install_module))
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun artifactIcon(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.Default.InsertDriveFile
}

private fun artifactTypeLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "内核镜像"
    ArtifactType.ANYKERNEL3 -> "AnyKernel3 刷写包"
    ArtifactType.KSU_MANAGER -> "KernelSU 管理器"
    ArtifactType.SUSFS_MODULE -> "SUSFS 模块"
    ArtifactType.OTHER -> "其他文件"
}
