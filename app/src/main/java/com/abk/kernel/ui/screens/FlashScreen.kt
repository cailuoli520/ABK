@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildArtifact
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelBuildConfig
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.ui.components.ExpressiveEmptyState
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlashScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeContentTab by rememberSaveable { mutableStateOf(FlashContentTab.Workflows) }
    var selectedRunId by remember { mutableStateOf<Long?>(null) }
    var selectedPrebuiltReleaseId by remember { mutableStateOf<Long?>(null) }
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var deleteWorkflowTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var deleteRemoteWorkflowRun by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalTitle by remember { mutableStateOf("终端") }
    var terminalCanReboot by remember { mutableStateOf(false) }
    var terminalRunning by remember { mutableStateOf(false) }
    var terminalLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var terminalSuccess by remember { mutableStateOf<Boolean?>(null) }
    val rootGranted = state.rootGranted
    val currentContentTab = if (state.prebuiltGkiEnabled) activeContentTab else FlashContentTab.Workflows

    val remoteArtifacts = remember(state.artifacts) {
        state.artifacts.filter {
            !it.expired && DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) != null
        }
    }
    val workflowDownloadedArtifacts = remember(state.downloadedArtifacts, state.prebuiltGkiEnabled) {
        if (state.prebuiltGkiEnabled) {
            state.downloadedArtifacts.filterNot { it.runId == PREBUILT_GKI_RUN_ID }
        } else {
            state.downloadedArtifacts
        }
    }
    val workflowGroups = remember(remoteArtifacts, workflowDownloadedArtifacts) {
        buildWorkflowGroups(remoteArtifacts, workflowDownloadedArtifacts)
    }
    val selectedGroup = selectedRunId?.let { id -> workflowGroups.firstOrNull { it.runId == id } }
    val selectedPrebuiltRelease = selectedPrebuiltReleaseId?.let { id ->
        state.prebuiltGkiReleases.firstOrNull { it.id == id }
    }

    fun returnToWorkflowList() {
        selectedRunId = null
    }

    fun returnToPrebuiltReleaseList() {
        selectedPrebuiltReleaseId = null
    }

    fun returnToTopList() {
        selectedRunId = null
        selectedPrebuiltReleaseId = null
    }

    BackHandler(enabled = selectedRunId != null || selectedPrebuiltReleaseId != null) {
        if (selectedRunId != null) returnToWorkflowList() else returnToPrebuiltReleaseList()
    }
    PredictiveBackHandler(enabled = selectedRunId != null || selectedPrebuiltReleaseId != null) { progress ->
        try {
            progress.collect { }
            if (selectedRunId != null) returnToWorkflowList() else returnToPrebuiltReleaseList()
        } catch (_: CancellationException) {
        }
    }

    LaunchedEffect(state.forkRepo?.fullName) {
        if (state.forkRepo != null) vm.loadRecentRuns()
    }

    LaunchedEffect(state.prebuiltGkiEnabled) {
        if (!state.prebuiltGkiEnabled) {
            activeContentTab = FlashContentTab.Workflows
            selectedPrebuiltReleaseId = null
        }
    }

    LaunchedEffect(currentContentTab, state.prebuiltGkiEnabled, state.isLoggedIn) {
        if (currentContentTab == FlashContentTab.PrebuiltGki && state.prebuiltGkiEnabled && state.isLoggedIn) {
            vm.loadPrebuiltGkiReleases()
        }
    }

    LaunchedEffect(selectedPrebuiltRelease?.id, state.prebuiltGkiEnabled, state.isLoggedIn) {
        val release = selectedPrebuiltRelease
        if (release != null && state.prebuiltGkiEnabled && state.isLoggedIn) {
            vm.loadPrebuiltGkiAssets(release)
        }
    }

    LaunchedEffect(workflowGroups, selectedRunId) {
        if (selectedRunId != null && selectedGroup == null) selectedRunId = null
    }

    LaunchedEffect(state.prebuiltGkiReleases, selectedPrebuiltReleaseId) {
        if (selectedPrebuiltReleaseId != null && selectedPrebuiltRelease == null) selectedPrebuiltReleaseId = null
    }

    fun showFailure(title: String, lines: List<String>) {
        terminalTitle = title
        terminalCanReboot = false
        terminalRunning = false
        terminalSuccess = false
        terminalLog = lines
        showTerminal = true
    }

    fun copyDownloadedFilePath(item: DownloadedArtifact) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
        Toast.makeText(context, "已复制文件路径", Toast.LENGTH_SHORT).show()
    }

    fun installManager(item: DownloadedArtifact) {
        val ok = DownloadUtils.installApk(context, item.filePath)
        if (!ok) {
            showFailure(
                "无法安装 APK",
                listOf(
                    "${'$'} install ${item.filePath}",
                    "系统 APK 安装器不可用，或当前 ROM 阻止了外部 APK 安装请求。",
                    "文件: ${item.name}"
                )
            )
        }
    }

    fun startFlash(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                "Root 未授权",
                listOf(
                    "${'$'} ${flashCommandPreview(item)}",
                    "当前处于部分激活状态，文件页只允许查看已下载文件。",
                    "如需刷写或安装模块，请先授予 Root 权限。"
                )
            )
            return
        }
        terminalTitle = flashOperationLabel(item.type)
        terminalCanReboot = true
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "${'$'} ${flashCommandPreview(item)}",
            "file: ${item.filePath}",
            "",
            "等待 root shell 返回，请不要退出应用..."
        )
        showTerminal = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (item.type) {
                        ArtifactType.KERNEL_IMG -> RootUtils.flashImage(item.filePath)
                        ArtifactType.ANYKERNEL3 -> RootUtils.flashAnyKernel3(context, item.filePath)
                        ArtifactType.SUSFS_MODULE -> RootUtils.installModule(item.filePath)
                        ArtifactType.KSU_MANAGER -> RootUtils.ShellResult(false, listOf("APK 请通过系统安装器安装"))
                        else -> RootUtils.ShellResult(false, listOf("不支持此文件类型的自动刷写"))
                    }
                }.getOrElse { error ->
                    RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
                }
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "${'$'} ${flashCommandPreview(item)}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(if (result.success) "命令执行完成，无输出。" else "命令执行失败，但未返回日志。")
            }
        }
    }

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
                        startFlash(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.flash_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFlashConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("操作失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { vm.clearError() }) {
                    Text("知道了")
                }
            }
        )
    }

    deleteFileTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFileTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除文件") },
            text = { Text("将删除本地文件记录和已下载文件：\n${item.name}") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteDownloadedArtifact(item.filePath)
                        deleteFileTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFileTarget = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteWorkflowTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteWorkflowTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (group.runId == PREBUILT_GKI_RUN_ID) "删除预编译 GKI 文件" else "删除工作流记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (group.runId == PREBUILT_GKI_RUN_ID) {
                            "将删除本地已下载的预编译 GKI 文件。"
                        } else {
                            "将删除此工作流在 ABK 中缓存的产物记录，并删除本地已下载文件。\n\n工作流 ${
                                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                            }"
                        }
                    )
                    if (group.runId > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteRemoteWorkflowRun = !deleteRemoteWorkflowRun },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = deleteRemoteWorkflowRun,
                                onCheckedChange = { deleteRemoteWorkflowRun = it }
                            )
                            Text("同时删除远程 GitHub Actions 工作流记录")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetRunId = group.runId
                        val shouldDeleteRemoteRun = deleteRemoteWorkflowRun
                        vm.deleteWorkflowArtifacts(targetRunId, shouldDeleteRemoteRun)
                        if (selectedRunId == targetRunId) selectedRunId = null
                        deleteWorkflowTarget = null
                        deleteRemoteWorkflowRun = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteWorkflowTarget = null
                    deleteRemoteWorkflowRun = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showTerminal) {
        FlashTerminalDialog(
            title = terminalTitle,
            running = terminalRunning,
            success = terminalSuccess,
            logLines = terminalLog,
            canReboot = terminalCanReboot,
            onClose = { if (!terminalRunning) showTerminal = false },
            onReboot = {
                if (!terminalRunning) {
                    scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                }
            }
        )
    }

    val selectedPrebuiltAssets = selectedPrebuiltRelease?.let {
        state.prebuiltGkiAssetsByReleaseId[it.id].orEmpty()
    }.orEmpty()
    val selectedPrebuiltAssetsLoading = selectedPrebuiltRelease?.id
        ?.let { it in state.loadingPrebuiltGkiAssetReleaseIds } == true
    var prebuiltFilter by remember(selectedPrebuiltRelease?.id) {
        mutableStateOf(defaultPrebuiltFilter())
    }
    val filteredPrebuiltAssets = remember(selectedPrebuiltAssets, prebuiltFilter) {
        val candidates = selectedPrebuiltAssets.filter(::isPrebuiltGkiCandidateUi)
        if (prebuiltFilter.onlyMatches) {
            candidates.filter { prebuiltAssetMatchesFilter(it, prebuiltFilter) }
        } else {
            candidates
        }
    }
    val recommendedPrebuiltIds = remember(filteredPrebuiltAssets, state.recommendedBuildConfig) {
        recommendedPrebuiltAssetIdsForUi(filteredPrebuiltAssets, state.recommendedBuildConfig)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { ExpressiveTopBar(title = if (rootGranted) stringResource(R.string.flash_title) else "文件") }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            when {
                selectedGroup != null -> {
                    item {
                        WorkflowDetailHeader(
                            group = selectedGroup,
                            onBack = ::returnToWorkflowList,
                            onDelete = {
                                deleteWorkflowTarget = selectedGroup
                                deleteRemoteWorkflowRun = false
                            }
                        )
                    }

                    artifactCategoryOrder.forEach { category ->
                        val remoteInCategory = selectedGroup.remote.filter {
                            DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category
                        }
                        val matchedLocalPaths = remoteInCategory
                            .flatMap { source -> selectedGroup.local.filter { DownloadUtils.matchesDownloadedArtifact(it, source) } }
                            .map { it.filePath }
                            .toSet()
                        val localOnly = selectedGroup.local
                            .filter { it.category == category && it.filePath !in matchedLocalPaths }

                        if (remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()) {
                            item("category-${selectedGroup.runId}-${category.name}") {
                                CategoryHeader(category)
                            }
                        }

                        items(remoteInCategory, key = { "source-${it.id}" }) { artifact ->
                            ArtifactSourceCard(
                                artifact = artifact,
                                downloadedFiles = selectedGroup.local.filter {
                                    DownloadUtils.matchesDownloadedArtifact(it, artifact)
                                },
                                progress = state.downloadProgress[artifact.id],
                                autoDownloadEligible = state.autoDownload &&
                                    state.pendingAutoDownloadRunId == artifact.runId &&
                                    DownloadUtils.shouldAutoDownload(artifact),
                                onDownload = { vm.downloadArtifact(artifact) },
                                onCopyPath = ::copyDownloadedFilePath,
                                onInstall = ::installManager,
                                onFlash = {
                                    selectedItem = it
                                    showFlashConfirm = true
                                },
                                onDelete = { deleteFileTarget = it },
                                allowRootActions = rootGranted
                            )
                        }

                        items(localOnly, key = { "local-${it.filePath}" }) { artifact ->
                            LocalOnlyArtifactCard(
                                artifact = artifact,
                                onCopyPath = ::copyDownloadedFilePath,
                                onInstall = ::installManager,
                                onFlash = {
                                    selectedItem = it
                                    showFlashConfirm = true
                                },
                                onDelete = { deleteFileTarget = it },
                                allowRootActions = rootGranted
                            )
                        }
                    }
                }

                selectedPrebuiltRelease != null -> {
                    item {
                        PrebuiltReleaseDetailHeader(
                            release = selectedPrebuiltRelease,
                            sourceCount = selectedPrebuiltAssets.size,
                            visibleCount = filteredPrebuiltAssets.size,
                            onBack = ::returnToPrebuiltReleaseList,
                            onRefresh = { vm.loadPrebuiltGkiAssets(selectedPrebuiltRelease, force = true) }
                        )
                    }

                    item {
                        PrebuiltGkiFilterCard(
                            filter = prebuiltFilter,
                            onFilterChange = { prebuiltFilter = it }
                        )
                    }

                    when {
                        selectedPrebuiltAssetsLoading -> {
                            item {
                                LoadingRow("正在获取 ${selectedPrebuiltRelease.name} 的预编译 GKI")
                            }
                        }
                        filteredPrebuiltAssets.isEmpty() -> {
                            item {
                                ExpressiveEmptyState(
                                    title = "未找到匹配资产",
                                    subtitle = if (prebuiltFilter.onlyMatches) {
                                        "当前 release 没有匹配筛选条件的 GKI、boot、img 或 AK3 资产。"
                                    } else {
                                        "当前 release 没有可识别的预编译 GKI 资产。"
                                    },
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
                        else -> {
                            items(filteredPrebuiltAssets, key = { "prebuilt-${it.id}" }) { asset ->
                                PrebuiltGkiAssetCard(
                                    asset = asset,
                                    recommended = asset.id in recommendedPrebuiltIds,
                                    downloadedFiles = state.downloadedArtifacts.filter {
                                        DownloadUtils.matchesDownloadedPrebuilt(it, asset)
                                    },
                                    progress = state.downloadProgress[DownloadUtils.prebuiltProgressKey(asset.id)],
                                    onDownload = { vm.downloadPrebuiltGki(asset) },
                                    onCopyPath = ::copyDownloadedFilePath,
                                    onInstall = ::installManager,
                                    onFlash = {
                                        selectedItem = it
                                        showFlashConfirm = true
                                    },
                                    onDelete = { deleteFileTarget = it },
                                    allowRootActions = rootGranted
                                )
                            }
                        }
                    }
                }

                else -> {
                item {
                    FlashHero(
                        buildStatus = state.buildStatus,
                        availableCount = remoteArtifacts.size,
                        downloadedCount = workflowDownloadedArtifacts.size,
                        rootGranted = rootGranted
                    )
                }

                if (state.prebuiltGkiEnabled) {
                    item {
                        FlashContentTabs(
                            active = activeContentTab,
                            onSelect = {
                                activeContentTab = it
                                returnToTopList()
                            }
                        )
                    }
                }

                when (currentContentTab) {
                    FlashContentTab.Workflows -> {
                        item {
                            OutlinedButton(
                                onClick = { vm.loadRecentRuns() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("联网刷新构建产物")
                            }
                        }

                        if (workflowGroups.isNotEmpty()) {
                            items(workflowGroups, key = { "workflow-${it.runId}" }) { group ->
                                WorkflowRunCard(
                                    group = group,
                                    onClick = { selectedRunId = group.runId },
                                    onDelete = {
                                        deleteWorkflowTarget = group
                                        deleteRemoteWorkflowRun = false
                                    }
                                )
                            }
                        } else {
                            item {
                                ExpressiveEmptyState(
                                    title = if (rootGranted) "暂无可刷写产物" else "暂无可查看文件",
                                    subtitle = if (rootGranted) {
                                        "构建成功后，ABK 会联网同步并按工作流整理产物。"
                                    } else {
                                        "构建成功后，可在这里下载并查看产物文件。"
                                    },
                                    icon = Icons.Default.Inbox
                                )
                            }
                        }
                    }

                    FlashContentTab.PrebuiltGki -> {
                        if (state.prebuiltGkiEnabled) {
                            item {
                                PrebuiltReleaseListHeader(
                                    releaseCount = state.prebuiltGkiReleases.size,
                                    isLoading = state.isLoadingPrebuiltGkiReleases,
                                    onRefresh = { vm.loadPrebuiltGkiReleases(force = true) }
                                )
                            }

                            when {
                                state.isLoadingPrebuiltGkiReleases -> {
                                    item {
                                        LoadingRow("正在获取 Release")
                                    }
                                }
                                state.prebuiltGkiReleases.isEmpty() -> {
                                    item {
                                        ExpressiveEmptyState(
                                            title = "暂无预编译 GKI Release",
                                            subtitle = "本仓库 Release 中暂未发现可浏览的版本。",
                                            icon = Icons.Default.CloudDownload
                                        )
                                    }
                                }
                                else -> {
                                    items(state.prebuiltGkiReleases, key = { "release-${it.id}" }) { release ->
                                        PrebuiltReleaseCard(
                                            release = release,
                                            onClick = { selectedPrebuiltReleaseId = release.id }
                                        )
                                    }
                                }
                            }

                            val localPrebuiltFiles = state.downloadedArtifacts.filter {
                                it.runId == PREBUILT_GKI_RUN_ID
                            }
                            if (localPrebuiltFiles.isNotEmpty()) {
                                item {
                                    CategoryHeader(ArtifactCategory.KERNEL)
                                }
                                items(localPrebuiltFiles, key = { "prebuilt-local-${it.filePath}" }) { artifact ->
                                    LocalOnlyArtifactCard(
                                        artifact = artifact,
                                        onCopyPath = ::copyDownloadedFilePath,
                                        onInstall = ::installManager,
                                        onFlash = {
                                            selectedItem = it
                                            showFlashConfirm = true
                                        },
                                        onDelete = { deleteFileTarget = it },
                                        allowRootActions = rootGranted
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun FlashHero(
    buildStatus: BuildStatus,
    availableCount: Int,
    downloadedCount: Int,
    rootGranted: Boolean
) {
    ExpressiveHeroCard(
        title = if (rootGranted) "产物中心" else "文件中心",
        subtitle = if (rootGranted) {
            "先选工作流，再处理内核、管理器和模块。"
        } else {
            "部分激活状态下只提供产物下载和文件查看。"
        },
        icon = if (rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = "$availableCount 个源产物",
                icon = Icons.Default.CloudDownload,
                color = MaterialTheme.colorScheme.tertiary
            )
            ExpressiveStatusChip(
                label = "$downloadedCount 个已下载",
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
private fun FlashContentTabs(
    active: FlashContentTab,
    onSelect: (FlashContentTab) -> Unit
) {
    TabRow(selectedTabIndex = FlashContentTab.entries.indexOf(active)) {
        FlashContentTab.entries.forEach { tab ->
            Tab(
                selected = active == tab,
                onClick = { onSelect(tab) },
                text = { Text(tab.label) },
                icon = {
                    Icon(
                        if (tab == FlashContentTab.Workflows) Icons.Default.FolderSpecial else Icons.Default.CloudDownload,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PrebuiltReleaseListHeader(
    releaseCount: Int,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("预编译 GKI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$releaseCount 个 Release · 进入子页面后筛选资产",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("刷新")
        }
    }
}

@Composable
private fun PrebuiltReleaseCard(
    release: PrebuiltGkiRelease,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        release.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${release.tagName} · ${releaseDateLabel(release.publishedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpressiveStatusChip(
                    label = if (release.assetCount > 0) "${release.assetCount} 个资产" else "点进后加载资产",
                    color = MaterialTheme.colorScheme.primary
                )
                ExpressiveStatusChip(label = "手动下载", color = MaterialTheme.colorScheme.secondary)
                ExpressiveStatusChip(label = "分 Release 筛选", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun PrebuiltReleaseDetailHeader(
    release: PrebuiltGkiRelease,
    sourceCount: Int,
    visibleCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    ExpressiveSectionCard(
        title = release.name,
        subtitle = "${release.tagName} · ${releaseDateLabel(release.publishedAt)}",
        icon = Icons.Default.CloudDownload
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "$visibleCount / $sourceCount 个可见资产",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新资产")
            }
        }
    }
}

@Composable
private fun PrebuiltGkiFilterCard(
    filter: PrebuiltGkiFilter,
    onFilterChange: (PrebuiltGkiFilter) -> Unit
) {
    val androidOptions = remember { listOf("") + KernelSupport.androidVersions() }
    val kernelOptions = remember { listOf("") + KernelSupport.kernelVersions() }
    val subLevelOptions = remember(filter.androidVersion, filter.kernelVersion) {
        listOf("") + prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    }
    val patchOptions = remember(filter.androidVersion, filter.kernelVersion, filter.subLevel) {
        listOf("") + prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, filter.subLevel)
    }

    fun updateFilter(next: PrebuiltGkiFilter) {
        onFilterChange(sanitizePrebuiltFilter(next))
    }

    ExpressiveSectionCard(
        title = "筛选器",
        subtitle = "未选择的条件视为不限",
        icon = Icons.Default.Tune
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrebuiltDropdownField(
                label = "Android 版本",
                value = filter.androidVersion,
                options = androidOptions,
                onSelect = { updateFilter(filter.copy(androidVersion = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrebuiltDropdownField(
                    label = "内核版本",
                    value = filter.kernelVersion,
                    options = kernelOptions,
                    onSelect = { updateFilter(filter.copy(kernelVersion = it)) },
                    modifier = Modifier.weight(1f)
                )
                PrebuiltDropdownField(
                    label = "小版本",
                    value = filter.subLevel,
                    options = subLevelOptions,
                    onSelect = { updateFilter(filter.copy(subLevel = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            PrebuiltDropdownField(
                label = "补丁级别",
                value = filter.osPatchLevel,
                options = patchOptions,
                onSelect = { updateFilter(filter.copy(osPatchLevel = it)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    updateFilter(filter.copy(onlyMatches = !filter.onlyMatches))
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = filter.onlyMatches,
                    onCheckedChange = { updateFilter(filter.copy(onlyMatches = it)) }
                )
                Text("只看匹配当前筛选条件的资产")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrebuiltDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = prebuiltOptionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.distinct().forEach { option ->
                DropdownMenuItem(
                    text = { Text(prebuiltOptionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun PrebuiltGkiAssetCard(
    asset: PrebuiltGkiAsset,
    recommended: Boolean,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    val type = prebuiltArtifactType(asset)
    val animatedProgress by animateFloatAsState(
        targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "prebuilt-gki-download"
    )
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(type),
                title = asset.name,
                subtitle = "${asset.releaseTag} · ${DownloadUtils.formatSize(asset.sizeBytes)}",
                chip = if (recommended) "设备推荐" else "Release"
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("下载中 $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下载预编译 GKI")
                    }
                }
                else -> {
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlash = { onFlash(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }
        }
    }
}

private fun prebuiltArtifactType(asset: PrebuiltGkiAsset): ArtifactType {
    val type = DownloadUtils.classifyArtifact(asset.name)
    return if (type == ArtifactType.OTHER) ArtifactType.KERNEL_PACKAGE else type
}

@Composable
private fun WorkflowRunCard(
    group: WorkflowArtifactGroup,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceCount = group.remote.size
    val downloadedCount = group.local.size
    val categories = artifactCategoryOrder.filter { category ->
        group.remote.any { DownloadUtils.classifyCategory(DownloadUtils.classifyArtifact(it.name)) == category } ||
            group.local.any { it.category == category }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (group.runId == PREBUILT_GKI_RUN_ID) {
                            "预编译 GKI"
                        } else {
                            "工作流 ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = group.runTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除工作流")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpressiveStatusChip(label = "$sourceCount 个源产物", color = MaterialTheme.colorScheme.primary)
                ExpressiveStatusChip(label = "$downloadedCount 个已下载", color = MaterialTheme.colorScheme.secondary)
                categories.forEach {
                    ExpressiveStatusChip(label = it.label(), color = MaterialTheme.colorScheme.surfaceTint)
                }
            }
        }
    }
}

@Composable
private fun WorkflowDetailHeader(
    group: WorkflowArtifactGroup,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    ExpressiveSectionCard(
        title = if (group.runId == PREBUILT_GKI_RUN_ID) {
            "预编译 GKI"
        } else {
            "工作流 ${if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"}"
        },
        subtitle = group.runTitle,
        icon = Icons.Default.FolderSpecial
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "${group.remote.size} 个源产物 / ${group.local.size} 个已下载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除工作流")
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ArtifactCategory) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            category.label(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtifactSourceCard(
    artifact: BuildArtifact,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    autoDownloadEligible: Boolean,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    val type = DownloadUtils.classifyArtifact(artifact.name)
    val animatedProgress by animateFloatAsState(
        targetValue = ((progress ?: 0) / 100f).coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artifact-download"
    )
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(type),
                title = artifact.name,
                subtitle = "${artifactTypeLabel(type)} · ${DownloadUtils.formatSize(artifact.sizeInBytes)}",
                chip = if (autoDownloadEligible) "下次自动" else artifactTypeLabel(type)
            )

            when {
                progress != null -> {
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                    Text("下载中 $progress%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("下载")
                    }
                }
                else -> {
                    downloadedFiles.forEachIndexed { index, file ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onInstall = { onInstall(file) },
                            onFlash = { onFlash(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = allowRootActions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalOnlyArtifactCard(
    artifact: DownloadedArtifact,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit,
    allowRootActions: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArtifactHeader(
                icon = artifactIcon(artifact.type),
                title = artifact.name,
                subtitle = "${artifactTypeLabel(artifact.type)} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                chip = "本地文件"
            )
            DownloadedOutputRow(
                artifact = artifact,
                onCopyPath = { onCopyPath(artifact) },
                onInstall = { onInstall(artifact) },
                onFlash = { onFlash(artifact) },
                onDelete = { onDelete(artifact) },
                allowRootActions = allowRootActions
            )
        }
    }
}

@Composable
private fun ArtifactHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    chip: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
            style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ExpressiveStatusChip(label = chip, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DownloadedOutputRow(
    artifact: DownloadedArtifact,
    onCopyPath: () -> Unit,
    onInstall: () -> Unit,
    onFlash: () -> Unit,
    onDelete: () -> Unit,
    allowRootActions: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    artifact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${artifactTypeLabel(artifact.type)} · ${DownloadUtils.formatSize(artifact.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除文件",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCopyPath,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制路径")
            }
            if (allowRootActions) {
                when (artifact.type) {
                    ArtifactType.KERNEL_IMG,
                    ArtifactType.ANYKERNEL3,
                    ArtifactType.SUSFS_MODULE -> Button(
                        onClick = onFlash,
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (artifact.type == ArtifactType.KERNEL_IMG) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            if (artifact.type == ArtifactType.SUSFS_MODULE) Icons.Default.Extension else Icons.Default.FlashOn,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(flashButtonLabel(artifact.type))
                    }
                    ArtifactType.KSU_MANAGER -> Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("安装")
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun FlashTerminalDialog(
    title: String,
    running: Boolean,
    success: Boolean?,
    logLines: List<String>,
    canReboot: Boolean,
    onClose: () -> Unit,
    onReboot: () -> Unit
) {
    val terminalScroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = { if (!running) onClose() },
        icon = {
            when {
                running -> LoadingIndicator(modifier = Modifier.size(24.dp))
                success == true -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                success == false -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Default.Terminal, null)
            }
        },
        title = { Text(if (running) "正在执行 · $title" else title) },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 360.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(terminalScroll)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    logLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("${'$'}")) {
                                MaterialTheme.colorScheme.inversePrimary
                            } else {
                                MaterialTheme.colorScheme.inverseOnSurface
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = {}, enabled = false) { Text("执行中") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    if (canReboot) {
                        Button(
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重启")
                        }
                    }
                }
            }
        }
    )
}

private fun buildWorkflowGroups(
    remoteArtifacts: List<BuildArtifact>,
    downloadedArtifacts: List<DownloadedArtifact>
): List<WorkflowArtifactGroup> {
    val runIds = (remoteArtifacts.map { it.runId } + downloadedArtifacts.map { it.runId }).distinct()
    return runIds.map { runId ->
        val remote = remoteArtifacts.filter { it.runId == runId }
        val local = downloadedArtifacts.filter { it.runId == runId }
        val firstRemote = remote.firstOrNull()
        val firstLocal = local.firstOrNull()
        WorkflowArtifactGroup(
            runId = runId,
            runTitle = firstRemote?.runTitle?.ifBlank { null }
                ?: firstLocal?.runTitle?.ifBlank { null }
                ?: "未关联工作流",
            runNumber = firstRemote?.runNumber ?: firstLocal?.runNumber ?: 0,
            remote = remote,
            local = local
        )
    }.sortedWith(
        compareByDescending<WorkflowArtifactGroup> { it.runNumber }
            .thenByDescending { it.runId }
    )
}

private data class WorkflowArtifactGroup(
    val runId: Long,
    val runTitle: String,
    val runNumber: Int,
    val remote: List<BuildArtifact>,
    val local: List<DownloadedArtifact>
)

private fun artifactIcon(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> Icons.Default.Inventory2
    ArtifactType.KERNEL_IMG -> Icons.Default.Memory
    ArtifactType.ANYKERNEL3 -> Icons.Default.Archive
    ArtifactType.KSU_MANAGER -> Icons.Default.Shield
    ArtifactType.SUSFS_MODULE -> Icons.Default.Extension
    ArtifactType.OTHER -> Icons.Default.InsertDriveFile
}

private fun artifactTypeLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_PACKAGE -> "内核构建包"
    ArtifactType.KERNEL_IMG -> "内核镜像"
    ArtifactType.ANYKERNEL3 -> "AnyKernel3 刷写包"
    ArtifactType.KSU_MANAGER -> "KernelSU 管理器"
    ArtifactType.SUSFS_MODULE -> "SUSFS 模块"
    ArtifactType.OTHER -> "其他文件"
}

private fun flashButtonLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "刷写"
    ArtifactType.ANYKERNEL3 -> "刷入 AK3"
    ArtifactType.SUSFS_MODULE -> "安装模块"
    else -> "执行"
}

private fun flashOperationLabel(type: ArtifactType) = when (type) {
    ArtifactType.KERNEL_IMG -> "刷写 boot 镜像"
    ArtifactType.ANYKERNEL3 -> "刷入 AnyKernel3"
    ArtifactType.SUSFS_MODULE -> "安装模块"
    else -> "执行"
}

private fun flashCommandPreview(item: DownloadedArtifact) = when (item.type) {
    ArtifactType.KERNEL_IMG -> "dd boot <- ${item.name}"
    ArtifactType.ANYKERNEL3 -> "flash-ak3 ${item.name}"
    ArtifactType.SUSFS_MODULE -> "install-module ${item.name}"
    else -> "run ${item.name}"
}

private enum class FlashContentTab(val label: String) {
    Workflows("构建产物"),
    PrebuiltGki("预编译 GKI")
}

private data class PrebuiltGkiFilter(
    val androidVersion: String,
    val kernelVersion: String,
    val subLevel: String,
    val osPatchLevel: String,
    val onlyMatches: Boolean = true
)

private fun defaultPrebuiltFilter(): PrebuiltGkiFilter = PrebuiltGkiFilter(
    androidVersion = "",
    kernelVersion = "",
    subLevel = "",
    osPatchLevel = ""
)

private fun sanitizePrebuiltFilter(filter: PrebuiltGkiFilter): PrebuiltGkiFilter {
    val subOptions = prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    val subLevel = filter.subLevel.takeIf { it.isBlank() || it in subOptions }.orEmpty()
    val patchOptions = prebuiltPatchOptions(filter.androidVersion, filter.kernelVersion, subLevel)
    val patch = filter.osPatchLevel.takeIf { it.isBlank() || it in patchOptions }.orEmpty()
    return filter.copy(subLevel = subLevel, osPatchLevel = patch)
}

private fun prebuiltSubLevelOptions(androidVersion: String, kernelVersion: String): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .map { it.subLevel }
        .distinct()
        .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

private fun prebuiltPatchOptions(androidVersion: String, kernelVersion: String, subLevel: String): List<String> =
    KernelSupport.entries
        .filter { androidVersion.isBlank() || it.androidVersion == androidVersion }
        .filter { kernelVersion.isBlank() || it.kernelVersion == kernelVersion }
        .filter { subLevel.isBlank() || it.subLevel == subLevel }
        .map { it.osPatchLevel }
        .distinct()
        .sortedBy(::patchMonthIndexForUi)

private fun prebuiltOptionLabel(value: String): String = value.ifBlank { "不限" }

private fun patchMonthIndexForUi(value: String): Int {
    val parts = value.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
    return year * 12 + month
}

private fun releaseDateLabel(value: String): String =
    value.takeIf { it.length >= 10 }?.take(10) ?: "未知日期"

private fun isPrebuiltGkiCandidateUi(asset: PrebuiltGkiAsset): Boolean {
    val lower = asset.name.lowercase()
    val type = DownloadUtils.classifyArtifact(asset.name)
    return type in setOf(ArtifactType.KERNEL_PACKAGE, ArtifactType.KERNEL_IMG, ArtifactType.ANYKERNEL3) ||
        ((lower.endsWith(".img") || lower.endsWith(".zip")) &&
            listOf("gki", "kernel", "boot", "anykernel", "ak3").any { lower.contains(it) })
}

private fun prebuiltAssetMatchesFilter(asset: PrebuiltGkiAsset, filter: PrebuiltGkiFilter): Boolean {
    val haystack = prebuiltHaystack(asset)
    return prebuiltAndroidMatches(haystack, filter.androidVersion) &&
        prebuiltKernelMatches(haystack, filter.kernelVersion, filter.subLevel) &&
        prebuiltTextMatches(haystack, filter.osPatchLevel)
}

private fun recommendedPrebuiltAssetIdsForUi(
    assets: List<PrebuiltGkiAsset>,
    recommended: KernelBuildConfig?
): Set<Long> {
    if (recommended == null) return emptySet()
    val scored = assets.map { it to prebuiltRecommendationScoreForUi(it, recommended) }
        .filter { it.second > 0 }
    val best = scored.maxOfOrNull { it.second } ?: return emptySet()
    return scored.filter { it.second == best }.map { it.first.id }.toSet()
}

private fun prebuiltRecommendationScoreForUi(asset: PrebuiltGkiAsset, recommended: KernelBuildConfig?): Int {
    recommended ?: return 0
    if (recommended.subLevel == "X") return 0
    val haystack = prebuiltHaystack(asset)
    val kernelSub = Regex(
        """(^|[^0-9])${Regex.escape(recommended.kernelVersion)}[.-]?${Regex.escape(recommended.subLevel)}([^0-9]|$)"""
    ).containsMatchIn(haystack)
    if (!kernelSub) return 0

    val androidNumber = recommended.androidVersion.removePrefix("android")
    val hasAndroid = haystack.contains(recommended.androidVersion.lowercase()) ||
        haystack.contains("android-$androidNumber") ||
        haystack.contains("a$androidNumber")
    val hasPatch = recommended.osPatchLevel.isNotBlank() && haystack.contains(recommended.osPatchLevel.lowercase())
    return 10 + (if (hasAndroid) 5 else 0) + (if (hasPatch) 8 else 0)
}

private fun prebuiltHaystack(asset: PrebuiltGkiAsset): String =
    listOf(asset.name, asset.releaseTag, asset.releaseName, asset.releaseBody)
        .joinToString(" ")
        .lowercase()
        .replace('_', '-')

private fun prebuiltAndroidMatches(haystack: String, value: String): Boolean {
    val android = value.trim().lowercase().replace('_', '-')
    if (android.isBlank()) return true
    val number = android.removePrefix("android").removePrefix("-")
    return haystack.contains(android) ||
        (number.isNotBlank() && (
            haystack.contains("android$number") ||
                haystack.contains("android-$number") ||
                haystack.contains("a$number")
            ))
}

private fun prebuiltKernelMatches(haystack: String, kernelVersion: String, subLevel: String): Boolean {
    val kernel = kernelVersion.trim()
    val sub = subLevel.trim()
    if (kernel.isBlank()) return true
    if (sub.isBlank()) return haystack.contains(kernel.lowercase())
    return Regex(
        """(^|[^0-9])${Regex.escape(kernel)}[.-]?${Regex.escape(sub)}([^0-9]|$)"""
    ).containsMatchIn(haystack)
}

private fun prebuiltTextMatches(haystack: String, value: String): Boolean {
    val text = value.trim().lowercase().replace('_', '-')
    return text.isBlank() || haystack.contains(text)
}

private val artifactCategoryOrder = listOf(
    ArtifactCategory.KERNEL,
    ArtifactCategory.MANAGER,
    ArtifactCategory.MODULE
)

private fun ArtifactCategory.label(): String = when (this) {
    ArtifactCategory.KERNEL -> "内核产物"
    ArtifactCategory.MANAGER -> "管理器"
    ArtifactCategory.MODULE -> "模块"
}

private fun ArtifactCategory.icon(): ImageVector = when (this) {
    ArtifactCategory.KERNEL -> Icons.Default.Memory
    ArtifactCategory.MANAGER -> Icons.Default.Shield
    ArtifactCategory.MODULE -> Icons.Default.Extension
}
