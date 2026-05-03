package com.abk.kernel.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.BuildMonitorService
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── UI State ─────────────────────────────────────────────────────────────────

enum class AuthStep { CHECK_ROOT, LOGIN, FORK_CHECK, READY }

data class MainUiState(
    val authStep: AuthStep = AuthStep.CHECK_ROOT,
    val rootGranted: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val forkRepo: GitHubRepo? = null,
    val behindBy: Int = 0,
    val showSyncDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Device-flow OAuth
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val isPollingToken: Boolean = false,
    // Build
    val buildStatus: BuildStatus = BuildStatus.IDLE,
    val currentRun: WorkflowRun? = null,
    val recentRuns: List<WorkflowRun> = emptyList(),
    val buildProgress: BuildProgress = BuildProgress(),
    val buildConfig: KernelBuildConfig = KernelBuildConfig(),
    val recommendedBuildConfig: KernelBuildConfig? = null,
    // Download
    val downloadedArtifacts: List<DownloadedArtifact> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val isDownloading: Boolean = false,
    val downloadProgress: Map<Long, Int> = emptyMap(),
    // Settings
    val autoDownload: Boolean = true,
    val notifyBuild: Boolean = true,
    val themeMode: String = "system"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    val github = GitHubRepository()
    private val gson = Gson()
    private var hasSavedBuildConfig = false

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(BuildMonitorService.EXTRA_STATUS) ?: return
            val runJson = intent.getStringExtra(BuildMonitorService.EXTRA_RUN) ?: return
            try {
                val run = com.google.gson.Gson().fromJson(runJson, WorkflowRun::class.java)
                val progress = intent.getStringExtra(BuildMonitorService.EXTRA_PROGRESS)?.let {
                    runCatching { gson.fromJson(it, BuildProgress::class.java) }.getOrNull()
                } ?: _uiState.value.buildProgress
                val bs = when (status) {
                    "queued", "waiting", "requested" -> BuildStatus.QUEUED
                    "in_progress" -> BuildStatus.IN_PROGRESS
                    "completed" -> if (run.conclusion == "success") BuildStatus.SUCCESS else BuildStatus.FAILURE
                    else -> BuildStatus.IDLE
                }
                _uiState.update { it.copy(buildStatus = bs, currentRun = run, buildProgress = progress) }
                if (bs == BuildStatus.SUCCESS && _uiState.value.autoDownload) {
                    loadArtifacts(run.id, autoDownload = true)
                }
            } catch (_: Exception) {}
        }
    }

    init {
        observePreferences()
        registerStatusReceiver()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                prefs.accessToken,
                prefs.username,
                prefs.avatarUrl,
                prefs.autoDownload,
                prefs.notifyBuild
            ) { token, name, avatar, autoDl, notify ->
                Quintuple(token, name, avatar, autoDl, notify)
            }.collect { (token, name, avatar, autoDl, notify) ->
                if (!token.isNullOrBlank()) {
                    github.updateToken(token)
                    val shouldResumeSetup = _uiState.value.rootGranted &&
                        !_uiState.value.isPollingToken &&
                        _uiState.value.authStep in setOf(AuthStep.CHECK_ROOT, AuthStep.LOGIN)
                    _uiState.update {
                        it.copy(
                            isLoggedIn = true,
                            autoDownload = autoDl,
                            notifyBuild = notify
                        )
                    }
                    if (!name.isNullOrBlank()) {
                        _uiState.update { state ->
                            state.copy(
                                user = state.user?.copy(login = name, avatarUrl = avatar ?: "")
                                    ?: GitHubUser(name, 0, name, avatar ?: "", "")
                            )
                        }
                    }
                    if (shouldResumeSetup) {
                        if (name.isNullOrBlank()) {
                            fetchUserAndContinue()
                        } else {
                            advanceStep()
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoggedIn = false,
                            user = null,
                            autoDownload = autoDl,
                            notifyBuild = notify
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            prefs.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            prefs.buildConfigJson.collect { json ->
                if (!json.isNullOrBlank()) {
                    runCatching { gson.fromJson(json, KernelBuildConfig::class.java) }
                        .getOrNull()
                        ?.let { config ->
                            hasSavedBuildConfig = true
                            _uiState.update { it.copy(buildConfig = config) }
                        }
                }
            }
        }
    }

    private fun registerStatusReceiver() {
        val filter = IntentFilter(BuildMonitorService.BROADCAST_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(statusReceiver, filter)
        }
    }

    // ── Root ──────────────────────────────────────────────────────────────

    fun checkRoot() {
        viewModelScope.launch {
            val granted = RootUtils.isRootAvailable()
            val recommended = if (granted) detectRecommendedBuildConfig() else null
            _uiState.update {
                it.copy(
                    rootGranted = granted,
                    recommendedBuildConfig = recommended,
                    buildConfig = if (granted && !hasSavedBuildConfig && recommended != null) {
                        recommended
                    } else {
                        it.buildConfig
                    }
                )
            }
            if (granted) advanceStep()
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val granted = RootUtils.requestRoot()
            val recommended = if (granted) detectRecommendedBuildConfig() else null
            _uiState.update {
                it.copy(
                    rootGranted = granted,
                    isLoading = false,
                    recommendedBuildConfig = recommended,
                    buildConfig = if (granted && !hasSavedBuildConfig && recommended != null) {
                        recommended
                    } else {
                        it.buildConfig
                    }
                )
            }
            if (granted) advanceStep()
        }
    }

    private fun advanceStep() {
        val state = _uiState.value
        when {
            !state.rootGranted -> _uiState.update { it.copy(authStep = AuthStep.CHECK_ROOT) }
            !state.isLoggedIn -> _uiState.update { it.copy(authStep = AuthStep.LOGIN) }
            state.user == null -> {
                _uiState.update { it.copy(authStep = AuthStep.LOGIN) }
                viewModelScope.launch { fetchUserAndContinue() }
            }
            else -> {
                _uiState.update { it.copy(authStep = AuthStep.FORK_CHECK) }
                checkFork()
            }
        }
    }

    // ── GitHub Auth (Device Flow) ─────────────────────────────────────────

    fun startDeviceFlow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = github.requestDeviceCode()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            deviceCode = r.data.deviceCode,
                            userCode = r.data.userCode,
                            verificationUri = r.data.verificationUri,
                            isPollingToken = true
                        )
                    }
                    pollToken(r.data.deviceCode, r.data.interval.toLong())
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    private fun pollToken(deviceCode: String, intervalSeconds: Long) {
        viewModelScope.launch {
            while (_uiState.value.isPollingToken) {
                delay(intervalSeconds * 1000)
                when (val r = github.pollToken(deviceCode)) {
                    is Result.Success -> {
                        val tokenResp = r.data
                        when (tokenResp.error) {
                            null -> {
                                val token = tokenResp.accessToken ?: continue
                                prefs.saveToken(token)
                                github.updateToken(token)
                                _uiState.update { it.copy(isPollingToken = false) }
                                fetchUserAndContinue()
                            }
                            "authorization_pending", "slow_down" -> {
                                if (tokenResp.error == "slow_down") delay(5000)
                            }
                            "expired_token", "access_denied" -> {
                                _uiState.update {
                                    it.copy(
                                        isPollingToken = false,
                                        error = "授权失败: ${tokenResp.error}"
                                    )
                                }
                            }
                            else -> _uiState.update { it.copy(isPollingToken = false, error = tokenResp.error) }
                        }
                    }
                    is Result.Error -> delay(intervalSeconds * 1000)
                    else -> {}
                }
            }
        }
    }

    private suspend fun fetchUserAndContinue() {
        when (val r = github.getAuthenticatedUser()) {
            is Result.Success -> {
                val user = r.data
                prefs.saveUsername(user.login)
                prefs.saveAvatarUrl(user.avatarUrl)
                _uiState.update { it.copy(user = user, isLoggedIn = true) }
                advanceStep()
            }
            is Result.Error -> _uiState.update { it.copy(error = r.message) }
            else -> {}
        }
    }

    fun logout() {
        viewModelScope.launch {
            prefs.clearAuth()
            _uiState.update {
                MainUiState(
                    rootGranted = it.rootGranted,
                    authStep = if (it.rootGranted) AuthStep.LOGIN else AuthStep.CHECK_ROOT
                )
            }
        }
    }

    // ── Fork Management ───────────────────────────────────────────────────

    fun checkFork() {
        val username = _uiState.value.user?.login ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val forkResult = github.getUserFork(
                BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME, username
            )
            when (forkResult) {
                is Result.Success -> {
                    if (forkResult.data == null) {
                        _uiState.update { it.copy(isLoading = false, forkRepo = null) }
                    } else {
                        val fork = forkResult.data
                        val upstreamBranch = fork.parent?.defaultBranch ?: fork.defaultBranch
                        prefs.saveForkRepoName(fork.name)
                        val compareResult = github.checkBehind(
                            BuildConfig.SOURCE_REPO_OWNER,
                            BuildConfig.SOURCE_REPO_NAME,
                            upstreamBranch,
                            username,
                            fork.defaultBranch
                        )
                        val behind = if (compareResult is Result.Success) compareResult.data.behindBy else 0
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                forkRepo = fork,
                                behindBy = behind,
                                showSyncDialog = behind > 0
                            )
                        }
                        if (behind == 0) finishSetup()
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = forkResult.message) }
                else -> {}
            }
        }
    }

    fun forkRepo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val r = github.forkRepo(BuildConfig.SOURCE_REPO_OWNER, BuildConfig.SOURCE_REPO_NAME)) {
                is Result.Success -> {
                    prefs.saveForkRepoName(r.data.name)
                    _uiState.update { it.copy(isLoading = false, forkRepo = r.data) }
                    openActionsPage(r.data)
                    finishSetup()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun syncFork() {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val fork = state.forkRepo ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showSyncDialog = false) }
            when (val r = github.syncFork(username, fork.name, fork.defaultBranch)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, behindBy = 0) }
                    finishSetup()
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    fun dismissSyncDialog() {
        _uiState.update { it.copy(showSyncDialog = false) }
        finishSetup()
    }

    private fun finishSetup() {
        _uiState.update { it.copy(authStep = AuthStep.READY) }
        loadRecentRuns()
    }

    // ── Build ─────────────────────────────────────────────────────────────

    fun dispatchBuild(config: KernelBuildConfig) {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: BuildConfig.SOURCE_REPO_NAME
        val ref = state.forkRepo?.defaultBranch ?: "main"
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val wfResult = github.getWorkflowId(username, repoName, "kernel-custom.yml")
            if (wfResult is Result.Error) {
                _uiState.update { it.copy(isLoading = false, error = wfResult.message) }
                return@launch
            }
            val wfId = (wfResult as Result.Success).data
            github.enableWorkflow(username, repoName, wfId)
            val previousRunId = when (val prior = github.listRecentRuns(username, repoName, 1, wfId)) {
                is Result.Success -> prior.data.firstOrNull()?.id
                else -> null
            }
            val inputs = config.toInputMap()
            when (val r = github.dispatchWorkflow(username, repoName, wfId, inputs, ref)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            buildStatus = BuildStatus.QUEUED,
                            buildProgress = BuildProgress(percent = 0, currentStep = "构建已排队")
                        )
                    }
                    delay(5000) // wait for GH to create the run
                    findAndMonitorLatestRun(username, repoName, wfId, previousRunId)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    private suspend fun findAndMonitorLatestRun(
        owner: String,
        repo: String,
        workflowId: Long,
        previousRunId: Long?
    ) {
        repeat(6) { attempt ->
            when (val r = github.listRecentRuns(owner, repo, 5, workflowId)) {
                is Result.Success -> {
                    val run = r.data.firstOrNull {
                        previousRunId == null || it.id > previousRunId
                    }
                    if (run != null) {
                        prefs.saveLastRunId(run.id)
                        _uiState.update { it.copy(currentRun = run, buildStatus = BuildStatus.QUEUED) }
                        BuildMonitorService.startMonitoring(getApplication(), owner, repo, run.id)
                        return
                    }
                }
                else -> {}
            }
            if (attempt < 5) delay(5_000)
        }
        _uiState.update {
            it.copy(error = "已提交构建，但暂未找到工作流运行，请稍后刷新最近构建。")
        }
    }

    fun loadRecentRuns() {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = github.listRecentRuns(username, repoName)) {
                is Result.Success -> _uiState.update { it.copy(recentRuns = r.data) }
                else -> {}
            }
        }
    }

    fun loadArtifacts(runId: Long, autoDownload: Boolean = false) {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = github.listArtifacts(username, repoName, runId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(artifacts = r.data) }
                    if (autoDownload) {
                        r.data.filterNot { it.expired }.forEach { downloadArtifact(it) }
                    }
                }
                else -> {}
            }
        }
    }

    fun downloadArtifact(artifact: com.abk.kernel.data.model.Artifact) {
        viewModelScope.launch {
            val token = prefs.accessToken.first()
            if (token.isNullOrBlank()) {
                _uiState.update { it.copy(isDownloading = false, error = "未登录，无法下载构建产物") }
                return@launch
            }
            _uiState.update { it.copy(isDownloading = true) }
            NotificationUtils.notifyDownloadProgress(getApplication(), 0, artifact.name)
            val results = DownloadUtils.downloadArtifact(
                getApplication(), token, artifact
            ) { pct ->
                NotificationUtils.notifyDownloadProgress(getApplication(), pct, artifact.name)
                _uiState.update { s ->
                    s.copy(downloadProgress = s.downloadProgress + (artifact.id to pct))
                }
            }
            if (results.isNotEmpty()) {
                NotificationUtils.notifyDownloadDone(getApplication(), artifact.name)
                _uiState.update { s ->
                    s.copy(
                        isDownloading = false,
                        downloadedArtifacts = (s.downloadedArtifacts + results)
                            .distinctBy { it.filePath },
                        downloadProgress = s.downloadProgress - artifact.id
                    )
                }
            } else {
                _uiState.update { it.copy(isDownloading = false, error = "下载失败: ${artifact.name}") }
            }
        }
    }

    private fun openActionsPage(repo: GitHubRepo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${repo.htmlUrl}/actions")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setAutoDownload(v: Boolean) = viewModelScope.launch { prefs.setAutoDownload(v) }
    fun setNotifyBuild(v: Boolean) = viewModelScope.launch { prefs.setNotifyBuild(v) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun updateBuildConfig(config: KernelBuildConfig) {
        hasSavedBuildConfig = true
        _uiState.update { it.copy(buildConfig = config) }
        viewModelScope.launch { prefs.saveBuildConfigJson(gson.toJson(config)) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

private fun detectRecommendedBuildConfig(): KernelBuildConfig {
    val kernel = RootUtils.getKernelVersion().lowercase()
    val base = when {
        kernel.contains("6.12") || kernel.contains("android16") -> KernelBuildConfig(
            androidVersion = "android16",
            kernelVersion = "6.12",
            subLevel = "58",
            osPatchLevel = "2025-12",
            revision = ""
        )
        kernel.contains("6.6") || kernel.contains("android15") -> KernelBuildConfig(
            androidVersion = "android15",
            kernelVersion = "6.6",
            subLevel = "118",
            osPatchLevel = "2026-01",
            revision = ""
        )
        kernel.contains("6.1") || kernel.contains("android14") -> KernelBuildConfig(
            androidVersion = "android14",
            kernelVersion = "6.1",
            subLevel = "157",
            osPatchLevel = "2025-12",
            revision = ""
        )
        kernel.contains("5.15") || kernel.contains("android13") -> KernelBuildConfig(
            androidVersion = "android13",
            kernelVersion = "5.15",
            subLevel = "194",
            osPatchLevel = "2025-12",
            revision = ""
        )
        else -> KernelBuildConfig(
            androidVersion = "android12",
            kernelVersion = "5.10",
            subLevel = "246",
            osPatchLevel = "2025-12",
            revision = "r1"
        )
    }
    val detectedSubLevel = Regex("""\b${Regex.escape(base.kernelVersion)}\.(\d+)""")
        .find(kernel)
        ?.groupValues
        ?.getOrNull(1)
        ?: return base
    val patch = kernelPatchMap[base.kernelVersion]?.get(detectedSubLevel)
    return base.copy(
        subLevel = detectedSubLevel,
        osPatchLevel = patch?.first ?: base.osPatchLevel,
        revision = patch?.second ?: base.revision
    )
}

private val kernelPatchMap = mapOf(
    "5.10" to mapOf(
        "66" to ("2022-01" to "r11"),
        "81" to ("2022-03" to "r11"),
        "101" to ("2022-04" to "r28"),
        "110" to ("2022-07" to "r1"),
        "117" to ("2022-09" to "r1"),
        "136" to ("2022-11" to "r15"),
        "149" to ("2023-01" to "r1"),
        "160" to ("2023-03" to "r1"),
        "168" to ("2023-04" to "r9"),
        "177" to ("2023-07" to "r3"),
        "185" to ("2023-09" to "r1"),
        "198" to ("2024-01" to "r17"),
        "205" to ("2024-03" to "r1"),
        "209" to ("2024-05" to "r13"),
        "218" to ("2024-08" to "r14"),
        "226" to ("2024-11" to "r8"),
        "233" to ("2025-02" to "r1"),
        "236" to ("2025-05" to "r1"),
        "237" to ("2025-06" to "r1"),
        "240" to ("2025-09" to "r1"),
        "246" to ("2025-12" to "r1")
    ),
    "5.15" to mapOf(
        "194" to ("2025-12" to ""),
        "189" to ("2025-09" to ""),
        "185" to ("2025-07" to ""),
        "180" to ("2025-05" to ""),
        "178" to ("2025-03" to ""),
        "170" to ("2025-01" to "")
    ),
    "6.1" to mapOf(
        "157" to ("2025-12" to ""),
        "145" to ("2025-09" to ""),
        "141" to ("2025-07" to ""),
        "138" to ("2025-06" to ""),
        "134" to ("2025-05" to "")
    ),
    "6.6" to mapOf(
        "118" to ("2026-01" to ""),
        "102" to ("2025-10" to ""),
        "98" to ("2025-09" to ""),
        "92" to ("2025-07" to ""),
        "89" to ("2025-06" to "")
    ),
    "6.12" to mapOf(
        "58" to ("2025-12" to ""),
        "38" to ("2025-09" to ""),
        "30" to ("2025-07" to ""),
        "23" to ("2025-06" to "")
    )
)

// Helper to convert KernelBuildConfig to workflow dispatch inputs map
private fun KernelBuildConfig.toInputMap(): Map<String, String> = mapOf(
    "android_version" to androidVersion,
    "kernel_version" to kernelVersion,
    "sub_level" to subLevel,
    "os_patch_level" to osPatchLevel,
    "revision" to revision,
    "kernelsu_variant" to kernelsuVariant,
    "kernelsu_branch" to kernelsuBranch,
    "version" to version,
    "build_time" to buildTime,
    "use_zram" to useZram.toString(),
    "use_bbg" to useBbg.toString(),
    "use_kpm" to useKpm.toString(),
    "use_rekernel" to useRekernel.toString(),
    "cancel_susfs" to cancelSusfs.toString(),
    "supp_op" to suppOp.toString(),
    "zram_full_algo" to zramFullAlgo.toString(),
    "zram_extra_algos" to zramExtraAlgos,
    "kpm_password" to kpmPassword
)

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = e
