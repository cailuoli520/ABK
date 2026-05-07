package com.abk.kernel

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abk.kernel.ui.screens.AuthGateScreen
import com.abk.kernel.ui.screens.BuildScreen
import com.abk.kernel.ui.screens.FlashScreen
import com.abk.kernel.ui.screens.SettingsScreen
import com.abk.kernel.ui.screens.StatusScreen
import com.abk.kernel.ui.theme.AbkTheme
import com.abk.kernel.viewmodel.AuthStep
import com.abk.kernel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            LaunchedEffect(state.termsAccepted) {
                if (state.termsAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            AbkTheme(themeMode = state.themeMode) {
                when {
                    !state.termsLoaded -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {}
                    !state.termsAccepted -> TermsAgreementDialog(onAccept = vm::acceptTerms)
                    state.authStep != AuthStep.READY -> AuthGateScreen(vm)
                    else -> AbkMainScaffold(vm)
                }
            }
        }
    }
}

@Composable
private fun TermsAgreementDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "用户协议与风险免责声明",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TermsText("ABK 用于触发 GitHub Actions 构建、下载、刷写或安装 GKI KernelSU / SUSFS 相关产物。继续使用前，请确认你理解内核修改和刷写的风险。")
                TermsText("刷写 boot、init_boot、内核镜像或安装底层模块可能导致无法开机、数据损坏、系统异常、保修或售后受限，并可能需要恢复官方镜像、清除数据或重新刷机。所有操作由你自行决定并自行承担后果。")
                TermsText("你只能在本人拥有或已获得明确授权的设备、账号和仓库中使用 ABK。禁止将本软件、工作流、补丁、构建产物或自定义外部模块用于灰黑产、未授权访问、绕过风控、破坏服务、窃取数据、作弊、恶意隐藏行为或其他违法违规用途。")
                TermsText("ABK 聚合多个第三方项目、补丁和下载来源。第三方代码、模块、许可证、稳定性和适配性由对应上游负责；ABK 不保证任何构建一定成功、可启动、可刷写或适配你的设备。")
                TermsText("使用 Root、GitHub 授权、Fork、Actions、自定义外部模块和下载镜像站时，你应自行确认权限范围、脚本内容、仓库可信度和产物来源。执行外部 setup.sh 前尤其应审查代码。")
                TermsText("开发者和贡献者在法律允许范围内不对因使用、修改、分发或依赖 ABK 造成的设备损坏、数据丢失、账号风险、服务中断、合规问题或任何直接/间接损失承担责任。")
                TermsText("点击“同意并继续”表示你已阅读、理解并接受以上协议和免责声明。")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("同意并继续")
            }
        }
    )
}

@Composable
private fun TermsText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private enum class AbkTab(val label: String) {
    Status("当前状态"),
    Build("构建内核"),
    Flash("刷写"),
    Settings("设置")
}

@Composable
private fun AbkMainScaffold(vm: MainViewModel) {
    val state by vm.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AbkTab.Status) }
    val visibleTabs = if (state.rootGranted) AbkTab.entries else AbkTab.entries.filterNot { it == AbkTab.Flash }
    val activeTab = if (!state.rootGranted && selectedTab == AbkTab.Flash) AbkTab.Status else selectedTab

    LaunchedEffect(state.rootGranted, selectedTab) {
        if (!state.rootGranted && selectedTab == AbkTab.Flash) {
            selectedTab = AbkTab.Status
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(82.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 6.dp,
                shadowElevation = 0.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    visibleTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { selectedTab = tab },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AbkTab.Status -> Icons.Default.Home
                                        AbkTab.Build -> Icons.Default.RocketLaunch
                                        AbkTab.Flash -> Icons.Default.FlashOn
                                        AbkTab.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { width -> direction * width / 4 }
                        ) togetherWith (
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) { width -> -direction * width / 6 }
                        )
                },
                label = "abk-tab"
            ) { tab ->
                when (tab) {
                    AbkTab.Status -> StatusScreen(vm)
                    AbkTab.Build -> BuildScreen(vm)
                    AbkTab.Flash -> FlashScreen(vm)
                    AbkTab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }
}
