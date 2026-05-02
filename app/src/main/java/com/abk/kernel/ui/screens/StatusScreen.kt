package com.abk.kernel.ui.screens

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
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User card
            state.user?.let { user ->
                StatusCard(title = stringResource(R.string.status_login)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(user.login, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (!user.name.isNullOrBlank()) {
                                Text(user.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } ?: StatusCard(title = stringResource(R.string.status_login)) {
                StatusRow(Icons.Default.AccountCircle, stringResource(R.string.status_not_login), isError = true)
            }

            // Root status
            StatusCard(title = stringResource(R.string.status_root)) {
                StatusRow(
                    if (state.rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                    if (state.rootGranted) stringResource(R.string.root_granted) else stringResource(R.string.root_denied),
                    isError = !state.rootGranted
                )
            }

            // Fork status
            StatusCard(title = stringResource(R.string.status_fork)) {
                if (state.forkRepo != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusRow(Icons.Default.ForkRight, state.forkRepo!!.fullName, isError = false)
                        if (state.behindBy > 0) {
                            StatusRow(
                                Icons.Default.Warning,
                                "落后上游 ${state.behindBy} 个提交",
                                isError = true
                            )
                        } else {
                            StatusRow(Icons.Default.CheckCircle, "已与上游同步", isError = false)
                        }
                    }
                } else {
                    StatusRow(Icons.Default.ForkRight, stringResource(R.string.status_no_fork), isError = true)
                }
            }

            // Kernel version
            StatusCard(title = "设备内核信息") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow(Icons.Default.Memory, "内核: ${com.abk.kernel.utils.RootUtils.getKernelVersion()}", false)
                    val ksuVer = com.abk.kernel.utils.RootUtils.getKsuVersion()
                    StatusRow(Icons.Default.Shield, "KSU: $ksuVer", ksuVer == "N/A")
                }
            }

            // Current build
            StatusCard(title = stringResource(R.string.status_build)) {
                when (state.buildStatus) {
                    BuildStatus.IDLE -> StatusRow(Icons.Default.HourglassEmpty, "暂无进行中的构建", false)
                    BuildStatus.QUEUED -> StatusRow(Icons.Default.Queue, "构建已排队…", false)
                    BuildStatus.IN_PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("构建进行中…")
                    }
                    BuildStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, "最近构建成功 ✓", false)
                    BuildStatus.FAILURE -> StatusRow(Icons.Default.Error, "最近构建失败", true)
                    BuildStatus.CANCELLED -> StatusRow(Icons.Default.Cancel, "构建已取消", true)
                }
                state.currentRun?.let { run ->
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {},
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("查看详情 #${run.runNumber}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Recent runs
            if (state.recentRuns.isNotEmpty()) {
                StatusCard(title = "最近构建记录") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentRuns.take(5).forEach { run ->
                            RunListItem(run)
                            if (run != state.recentRuns.last()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon, null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunListItem(run: WorkflowRun) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "#${run.runNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                run.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val (color, label) = when {
            run.status == "completed" && run.conclusion == "success" ->
                MaterialTheme.colorScheme.primary to "成功"
            run.status == "completed" ->
                MaterialTheme.colorScheme.error to "失败"
            run.status == "in_progress" ->
                MaterialTheme.colorScheme.tertiary to "进行中"
            else -> MaterialTheme.colorScheme.outline to run.status
        }
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}
