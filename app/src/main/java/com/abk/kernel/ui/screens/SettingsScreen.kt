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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Default.Logout, null) },
            title = { Text("退出登录") },
            text = { Text("确认退出 GitHub 账户吗？退出后需重新授权。") },
            confirmButton = {
                Button(
                    onClick = { showLogoutDialog = false; vm.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // ── 账户 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_account)) {
                state.user?.let { user ->
                    ListItem(
                        headlineContent = { Text(user.login, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(user.name ?: user.htmlUrl) },
                        leadingContent = {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Fork 仓库") },
                        supportingContent = { Text(state.forkRepo?.fullName ?: "未 Fork") },
                        leadingContent = { Icon(Icons.Default.ForkRight, null) }
                    )
                } ?: ListItem(
                    headlineContent = { Text("未登录") },
                    leadingContent = { Icon(Icons.Default.AccountCircle, null) }
                )
            }

            // ── 构建 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_build)) {
                SwitchSettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_auto_download),
                    subtitle = "构建成功后自动下载所有产物",
                    checked = state.autoDownload,
                    onCheckedChange = { vm.setAutoDownload(it) }
                )
            }

            // ── 通知 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_notification)) {
                SwitchSettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notify_build),
                    subtitle = "在通知栏显示构建状态",
                    checked = state.notifyBuild,
                    onCheckedChange = { vm.setNotifyBuild(it) }
                )
            }

            // ── 主题 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_theme)) {
                val themes = listOf(
                    "system" to stringResource(R.string.settings_theme_system),
                    "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)
                )
                themes.forEach { (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            Icon(
                                when (key) {
                                    "light" -> Icons.Default.LightMode
                                    "dark" -> Icons.Default.DarkMode
                                    else -> Icons.Default.BrightnessMedium
                                }, null
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = state.themeMode == key,
                                onClick = { vm.setThemeMode(key) }
                            )
                        },
                        modifier = Modifier.then(
                            Modifier // clickable
                        )
                    )
                    if (key != themes.last().first) HorizontalDivider()
                }
            }

            // ── 关于 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_full_name)) },
                    supportingContent = { Text("ABK v${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_source_repo)) },
                    supportingContent = {
                        Text("${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}")
                    },
                    leadingContent = { Icon(Icons.Default.GitHub, null) }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SwitchSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}
