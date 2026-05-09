@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showThemeSettings) {
        showThemeSettings = false
    }

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

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenUrl = { openUrl(context, it) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ExpressiveTopBar(
                title = if (showThemeSettings) {
                    stringResource(R.string.settings_theme)
                } else {
                    stringResource(R.string.settings_title)
                },
                navigationIcon = if (showThemeSettings) {
                    {
                        IconButton(onClick = { showThemeSettings = false }) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    }
                } else {
                    null
                }
            )
        }
    ) { padding ->
        if (showThemeSettings) {
            ThemeSettingsScreen(
                padding = padding,
                themeMode = state.themeMode,
                dynamicColorEnabled = state.dynamicColorEnabled,
                onThemeModeChange = vm::setThemeMode,
                onDynamicColorEnabledChange = vm::setDynamicColorEnabled
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    subtitle = "仅对下一次新提交的构建生效，关闭后不会自动下载",
                    checked = state.autoDownload,
                    onCheckedChange = { vm.setAutoDownload(it) }
                )
                HorizontalDivider()
                SwitchSettingsItem(
                    icon = Icons.Default.CloudDownload,
                    title = "预编译 GKI 获取与下载",
                    subtitle = "从本仓库 Release 获取预编译 GKI，下载需手动触发",
                    checked = state.prebuiltGkiEnabled,
                    onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
                )
                Spacer(Modifier.height(10.dp))
                MirrorSettingsItem(
                    value = state.downloadMirrorBaseUrl,
                    onValueChange = { vm.setDownloadMirrorBaseUrl(it) }
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
                ListItem(
                    headlineContent = { Text("颜色与外观") },
                    supportingContent = {
                        Text("${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}")
                    },
                    leadingContent = { Icon(Icons.Default.Palette, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { showThemeSettings = true }
                )
            }

            // ── 关于 ──────────────────────────────────────────────────────
            SettingsGroup(title = stringResource(R.string.settings_about)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_full_name)) },
                    supportingContent = { Text("AnyBase Kernel v${BuildConfig.VERSION_NAME}") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("关于") },
                    supportingContent = { Text("项目入口、源码仓库、上游项目与致谢") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ThemeSettingsScreen(
    padding: PaddingValues,
    themeMode: String,
    dynamicColorEnabled: Boolean,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorEnabledChange: (Boolean) -> Unit
) {
    val dynamicColorAvailable = isDynamicColorAvailable()
    val themes = listOf(
        Triple("system", stringResource(R.string.settings_theme_system), Icons.Default.BrightnessMedium),
        Triple("light", stringResource(R.string.settings_theme_light), Icons.Default.LightMode),
        Triple("dark", stringResource(R.string.settings_theme_dark), Icons.Default.DarkMode)
    )

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(title = "外观模式") {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                themes.forEach { (key, label, icon) ->
                    toggleableItem(
                        checked = themeMode == key,
                        label = label,
                        onCheckedChange = { selected ->
                            if (selected) onThemeModeChange(key)
                        },
                        icon = {
                            Icon(icon, contentDescription = null)
                        },
                        weight = 1f
                    )
                }
            }
        }

        SettingsGroup(title = "颜色来源") {
            SwitchSettingsItem(
                icon = Icons.Default.AutoAwesome,
                title = "莫奈取色",
                subtitle = if (dynamicColorAvailable) {
                    "使用系统壁纸生成的 Material You 动态颜色"
                } else {
                    "Android 12 及以上可用，当前使用固定色板"
                },
                checked = dynamicColorAvailable && dynamicColorEnabled,
                enabled = dynamicColorAvailable,
                onCheckedChange = onDynamicColorEnabledChange
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun themeModeLabel(themeMode: String): String = when (themeMode) {
    "light" -> stringResource(R.string.settings_theme_light)
    "dark" -> stringResource(R.string.settings_theme_dark)
    else -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun dynamicColorLabel(enabled: Boolean): String = when {
    !isDynamicColorAvailable() -> "莫奈取色不可用"
    enabled -> "莫奈取色"
    else -> "固定色板"
}

private fun isDynamicColorAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val links = remember { aboutLinks() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null) },
        title = { Text("关于 AnyBase Kernel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "AnyBase Kernel 用于构建、分发和管理 GKI KernelSU / SUSFS 内核。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AboutLinkRow(AboutLink("源仓库", sourceRepoUrl()), onOpenUrl)
                AboutSectionTitle("致谢")
                Text(
                    "ABK 基于以下项目、仓库和社区工作继续开发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                links.forEach {
                    AboutLinkRow(it, onOpenUrl)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AboutLinkRow(
    link: AboutLink,
    onOpenUrl: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(link.title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(link.url) },
        leadingContent = { Icon(Icons.Default.Code, null) },
        trailingContent = { Icon(Icons.Default.OpenInBrowser, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable { onOpenUrl(link.url) }
    )
}

private data class AboutLink(
    val title: String,
    val url: String
)

private fun aboutLinks(): List<AboutLink> {
    return listOf(
        AboutLink("上游仓库", BuildConfig.UPSTREAM_REPO_URL),
        AboutLink("顶层仓库", BuildConfig.TOP_LEVEL_REPO_URL),
        AboutLink("KernelSU", "https://github.com/tiann/KernelSU"),
        AboutLink("KernelSU Next", "https://github.com/KernelSU-Next/KernelSU-Next"),
        AboutLink("SukiSU Ultra", "https://github.com/SukiSU-Ultra/SukiSU-Ultra"),
        AboutLink("ReSukiSU", "https://github.com/ReSukiSU/ReSukiSU"),
        AboutLink("SUSFS", "https://gitlab.com/simonpunk/susfs4ksu"),
        AboutLink("SUSFS GitHub 镜像/补丁来源", "https://github.com/ShirkNeko/susfs4ksu"),
        AboutLink("SukiSU patch", "https://github.com/ShirkNeko/SukiSU_patch"),
        AboutLink("AnyKernel3", "https://github.com/WildKernels/AnyKernel3"),
        AboutLink("Kernel patches", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR 来源", "https://github.com/WildKernels/kernel_patches"),
        AboutLink("NTsync / IPSet / BBR PR by huime180", "https://github.com/huime180"),
        AboutLink("Action-Build", "https://github.com/Numbersf/Action-Build"),
        AboutLink("SUSFS 模块构建来源", "https://github.com/sidex15/susfs4ksu-module"),
        AboutLink(
            "GCC prebuilts",
            "https://github.com/LineageOS/android_prebuilts_gcc_linux-x86_aarch64_aarch64-linux-gnu-6.4.1"
        ),
        AboutLink("Baseband Guard", "https://github.com/vc-teahouse/Baseband-guard"),
        AboutLink("Re-Kernel", "https://github.com/Sakion-Team/Re-Kernel"),
        AboutLink("Droidspaces / 虚拟化支持补丁来源", "https://github.com/ravindu644/Droidspaces-OSS"),
        AboutLink("KernelSU 官方站点", "https://kernelsu.org/")
    )
}

private fun sourceRepoUrl(): String =
    "https://github.com/${BuildConfig.SOURCE_REPO_OWNER}/${BuildConfig.SOURCE_REPO_NAME}"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SettingsHero(
    login: String?,
    forkName: String?,
    themeMode: String
) {
    ExpressiveHeroCard(
        title = login?.let { "已连接 GitHub：$it" } ?: "AnyBase Kernel 设置中心",
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
            "外观模式" -> "控制应用明暗显示方式。"
            "颜色来源" -> "选择系统动态颜色或固定色板。"
            else -> "应用版本与源码信息。"
        },
        icon = when (title) {
            stringResource(R.string.settings_account) -> Icons.Default.AccountCircle
            stringResource(R.string.settings_build) -> Icons.Default.Build
            stringResource(R.string.settings_notification) -> Icons.Default.Notifications
            stringResource(R.string.settings_theme) -> Icons.Default.Palette
            "外观模式" -> Icons.Default.BrightnessMedium
            "颜色来源" -> Icons.Default.AutoAwesome
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                icon,
                null,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    checked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MirrorSettingsItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ListItem(
            leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
            headlineContent = {
                Text("下载镜像站", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            },
            supportingContent = {
                Text(
                    "留空直连 GitHub；填写后会先镜像到 Release 再下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("https://hk.gh-proxy.org/") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
