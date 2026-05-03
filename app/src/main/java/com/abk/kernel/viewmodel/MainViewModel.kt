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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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
    val artifacts: List<BuildArtifact> = emptyList(),
    val isDownloading: Boolean = false,
    val downloadProgress: Map<Long, Int> = emptyMap(),
    val pendingAutoDownloadRunId: Long = -1L,
    // Settings
    val autoDownload: Boolean = true,
    val notifyBuild: Boolean = true,
    val themeMode: String = "dark"
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
                if (bs == BuildStatus.SUCCESS) {
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
        viewModelScope.launch {
            prefs.downloadedArtifactsJson.collect { json ->
                val restored = parseDownloadedArtifacts(json)
                    .distinctBy { it.filePath }
                    .filter { File(it.filePath).exists() }
                _uiState.update { it.copy(downloadedArtifacts = restored) }
            }
        }
        viewModelScope.launch {
            prefs.remoteArtifactsJson.collect { json ->
                if (json.isNullOrBlank()) {
                    _uiState.update { it.copy(artifacts = emptyList()) }
                } else {
                    val restored = parseBuildArtifacts(json)
                        .distinctBy { it.id }
                        .sortedForDisplay()
                    _uiState.update { it.copy(artifacts = mergeRemoteArtifacts(it.artifacts, restored)) }
                }
            }
        }
        viewModelScope.launch {
            prefs.pendingAutoDownloadRunId.collect { runId ->
                _uiState.update { it.copy(pendingAutoDownloadRunId = runId) }
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
        val buildConfig = KernelSupport.normalize(config)
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
            val inputs = buildConfig.toInputMap()
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
                        if (_uiState.value.autoDownload) {
                            prefs.savePendingAutoDownloadRunId(run.id)
                        } else {
                            prefs.clearPendingAutoDownloadRunId()
                        }
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
            when (val r = github.listRecentRuns(username, repoName, perPage = 30)) {
                is Result.Success -> {
                    _uiState.update { it.copy(recentRuns = r.data) }
                    refreshArtifactsForRuns(username, repoName, r.data)
                }
                else -> {}
            }
        }
    }

    fun loadArtifacts(runId: Long, autoDownload: Boolean = false) {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = listArtifactsWithRetry(username, repoName, runId, retryWhenEmpty = autoDownload)) {
                is Result.Success -> {
                    val run = state.recentRuns.find { it.id == runId }
                        ?: state.currentRun?.takeIf { it.id == runId }
                        ?: when (val runResult = github.getWorkflowRun(username, repoName, runId)) {
                            is Result.Success -> runResult.data
                            else -> null
                        }
                    val buildArtifacts = r.data.map { artifact ->
                        if (run != null) artifact.withRun(run) else artifact.toBuildArtifact(runId)
                    }
                    val merged = mergeRemoteArtifacts(_uiState.value.artifacts, buildArtifacts)
                    _uiState.update { it.copy(artifacts = merged) }
                    prefs.saveRemoteArtifactsJson(gson.toJson(merged))
                    maybeAutoDownloadRun(runId, buildArtifacts, autoDownload)
                }
                else -> {}
            }
        }
    }

    private suspend fun listArtifactsWithRetry(
        owner: String,
        repoName: String,
        runId: Long,
        retryWhenEmpty: Boolean
    ): Result<List<Artifact>> {
        var result = github.listArtifacts(owner, repoName, runId)
        if (!retryWhenEmpty) return result
        repeat(3) {
            when (val current = result) {
                is Result.Success -> if (current.data.isNotEmpty()) return current
                else -> {}
            }
            delay(5_000)
            result = github.listArtifacts(owner, repoName, runId)
        }
        return result
    }

    fun downloadArtifact(
        artifact: BuildArtifact
    ) {
        viewModelScope.launch {
            val token = prefs.accessToken.first()
            if (token.isNullOrBlank()) {
                _uiState.update { it.copy(isDownloading = false, error = "未登录，无法下载构建产物") }
                return@launch
            }
            _uiState.update { it.copy(isDownloading = true) }
            NotificationUtils.notifyDownloadProgress(getApplication(), 0, artifact.name)
            val results = DownloadUtils.downloadArtifact(
                getApplication(), token, artifact.toArtifact(), artifact.toWorkflowRun()
            ) { pct ->
                NotificationUtils.notifyDownloadProgress(getApplication(), pct, artifact.name)
                _uiState.update { s ->
                    s.copy(downloadProgress = s.downloadProgress + (artifact.id to pct))
                }
            }
            if (results.isNotEmpty()) {
                NotificationUtils.notifyDownloadDone(getApplication(), artifact.name)
                val updated = (_uiState.value.downloadedArtifacts + results)
                    .distinctBy { it.filePath }
                    .sortedDownloadedForDisplay()
                _uiState.update { s ->
                    s.copy(
                        isDownloading = false,
                        downloadedArtifacts = updated,
                        downloadProgress = s.downloadProgress - artifact.id
                    )
                }
                prefs.saveDownloadedArtifactsJson(gson.toJson(updated))
            } else {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        error = "下载失败: ${artifact.name}",
                        downloadProgress = it.downloadProgress - artifact.id
                    )
                }
            }
        }
    }

    private suspend fun refreshArtifactsForRuns(
        owner: String,
        repoName: String,
        runs: List<WorkflowRun>
    ) {
        val completedRuns = runs
            .filter { it.status == "completed" }
            .take(MAX_REMOTE_ARTIFACT_RUNS)
        if (completedRuns.isEmpty()) return

        val collected = completedRuns.flatMap { run ->
            when (val artifacts = github.listArtifacts(owner, repoName, run.id)) {
                is Result.Success -> artifacts.data.map { it.withRun(run) }
                else -> emptyList()
            }
        }
        val merged = mergeRemoteArtifacts(_uiState.value.artifacts, collected)
        _uiState.update { it.copy(artifacts = merged) }
        prefs.saveRemoteArtifactsJson(gson.toJson(merged))

        val pendingRunId = prefs.pendingAutoDownloadRunId.first()
        if (pendingRunId > 0L && completedRuns.any { it.id == pendingRunId }) {
            maybeAutoDownloadRun(
                pendingRunId,
                merged.filter { it.runId == pendingRunId },
                requestedByMonitor = true
            )
        }
    }

    private suspend fun maybeAutoDownloadRun(
        runId: Long,
        artifacts: List<BuildArtifact>,
        requestedByMonitor: Boolean
    ) {
        if (!requestedByMonitor || !_uiState.value.autoDownload) return
        if (prefs.pendingAutoDownloadRunId.first() != runId) return
        if (artifacts.isEmpty()) return

        val targets = artifacts
            .filter { !it.expired && DownloadUtils.shouldAutoDownload(it) }
            .filterNot { candidate ->
                _uiState.value.downloadedArtifacts.any { DownloadUtils.matchesDownloadedArtifact(it, candidate) }
            }

        if (targets.isEmpty()) {
            prefs.clearPendingAutoDownloadRunId()
            return
        }
        prefs.clearPendingAutoDownloadRunId()
        targets.forEach { downloadArtifact(it) }
    }

    private fun openActionsPage(repo: GitHubRepo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${repo.htmlUrl}/actions")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setAutoDownload(v: Boolean) = viewModelScope.launch {
        prefs.setAutoDownload(v)
        if (!v) prefs.clearPendingAutoDownloadRunId()
    }
    fun setNotifyBuild(v: Boolean) = viewModelScope.launch { prefs.setNotifyBuild(v) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun updateBuildConfig(config: KernelBuildConfig) {
        val normalized = KernelSupport.normalize(config)
        hasSavedBuildConfig = true
        _uiState.update { it.copy(buildConfig = normalized) }
        viewModelScope.launch { prefs.saveBuildConfigJson(gson.toJson(normalized)) }
    }

    private fun parseDownloadedArtifacts(json: String?): List<DownloadedArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<DownloadedArtifact>> {
            val type = object : TypeToken<List<DownloadedArtifact>>() {}.type
            gson.fromJson<List<DownloadedArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun parseBuildArtifacts(json: String?): List<BuildArtifact> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching<List<BuildArtifact>> {
            val type = object : TypeToken<List<BuildArtifact>>() {}.type
            gson.fromJson<List<BuildArtifact>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

private fun detectRecommendedBuildConfig(): KernelBuildConfig {
    return KernelSupport.recommendedFromKernel(RootUtils.getKernelVersion())
}

// Helper to convert KernelBuildConfig to workflow dispatch inputs map
private fun KernelBuildConfig.toInputMap(): Map<String, String> = mapOf(
    "android_version" to androidVersion,
    "kernel_version" to kernelVersion,
    "sub_level" to subLevel,
    "os_patch_level" to osPatchLevel,
    "revision" to revision,
    "kernelsu_variant" to kernelsuVariant,
    "kernelsu_branch" to kernelsuBranch.takeIf { it in setOf("Stable(标准)", "Dev(开发)") }.orEmpty()
        .ifBlank { "Stable(标准)" },
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

private const val MAX_REMOTE_ARTIFACT_RUNS = 30
private const val MAX_PERSISTED_REMOTE_ARTIFACTS = 240

private fun Artifact.toBuildArtifact(runId: Long): BuildArtifact = BuildArtifact(
    id = id,
    name = name,
    sizeInBytes = sizeInBytes,
    archiveDownloadUrl = archiveDownloadUrl,
    expired = expired,
    createdAt = createdAt,
    runId = runId,
    runTitle = "工作流 #$runId",
    runNumber = 0,
    runCreatedAt = createdAt
)

private fun mergeRemoteArtifacts(
    existing: List<BuildArtifact>,
    incoming: List<BuildArtifact>
): List<BuildArtifact> {
    val incomingRunIds = incoming.map { it.runId }.toSet()
    return (incoming + existing.filterNot { it.runId in incomingRunIds })
        .distinctBy { it.id }
        .sortedForDisplay()
        .take(MAX_PERSISTED_REMOTE_ARTIFACTS)
}

private fun List<BuildArtifact>.sortedForDisplay(): List<BuildArtifact> =
    sortedWith(
        compareByDescending<BuildArtifact> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )

private fun List<DownloadedArtifact>.sortedDownloadedForDisplay(): List<DownloadedArtifact> =
    sortedWith(
        compareByDescending<DownloadedArtifact> { it.runNumber }
            .thenByDescending { it.runId }
            .thenBy { it.name }
    )

private data class Quintuple<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quintuple<A, B, C, D, E>.component5() = e
