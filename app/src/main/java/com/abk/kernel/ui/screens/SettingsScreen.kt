package com.abk.kernel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

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
            ExpressiveTopBar(
                title = stringResource(R.string.settings_title),
                icon = Icons.Default.Settings
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
            SettingsHero(
                login = state.user?.login,
                forkName = state.forkRepo?.fullName,
                themeMode = state.themeMode
            )

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
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.clickable { vm.setThemeMode(key) }
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
                    leadingContent = { Icon(Icons.Default.Code, null) }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsHero(
    login: String?,
    forkName: String?,
    themeMode: String
) {
    ExpressiveHeroCard(
        title = login?.let { "已连接 GitHub：$it" } ?: "ABK 设置中心",
        subtitle = forkName ?: "管理构建自动化、通知、主题和仓库来源。",
        icon = Icons.Default.Tune,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            ExpressiveStatusChip(
                label = when (themeMode) {
                    "dark" -> "深色主题"
                    "light" -> "浅色主题"
                    else -> "跟随系统"
                },
                icon = Icons.Default.Palette,
                color = MaterialTheme.colorScheme.primary
            )
            ExpressiveStatusChip(
                label = if (forkName != null) "Fork 已连接" else "等待 Fork",
                icon = Icons.Default.ForkRight,
                color = if (forkName != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    ExpressiveSectionCard(
        title = title,
        subtitle = when (title) {
            stringResource(R.string.settings_account) -> "GitHub 账户、fork 仓库和退出登录。"
            stringResource(R.string.settings_build) -> "控制构建成功后的自动化行为。"
            stringResource(R.string.settings_notification) -> "同步工作流状态到系统通知。"
            stringResource(R.string.settings_theme) -> "Material 3 Expressive 主题显示模式。"
            else -> "应用版本与源码信息。"
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            else -> Icons.Default.Info
        }
    ) {
        Column { content() }
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
