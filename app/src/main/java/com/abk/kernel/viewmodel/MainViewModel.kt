package com.abk.kernel.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abk.kernel.BuildConfig
import com.abk.kernel.data.model.*
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import com.abk.kernel.utils.BuildMonitorService
import com.abk.kernel.utils.NotificationUtils
import com.abk.kernel.utils.RootUtils
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

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(BuildMonitorService.EXTRA_STATUS) ?: return
            val runJson = intent.getStringExtra(BuildMonitorService.EXTRA_RUN) ?: return
            try {
                val run = com.google.gson.Gson().fromJson(runJson, WorkflowRun::class.java)
                val bs = when (status) {
                    "queued", "waiting", "requested" -> BuildStatus.QUEUED
                    "in_progress" -> BuildStatus.IN_PROGRESS
                    "completed" -> if (run.conclusion == "success") BuildStatus.SUCCESS else BuildStatus.FAILURE
                    else -> BuildStatus.IDLE
                }
                _uiState.update { it.copy(buildStatus = bs, currentRun = run) }
                if (bs == BuildStatus.SUCCESS && _uiState.value.autoDownload) {
                    loadArtifacts(run.id)
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
            _uiState.update { it.copy(rootGranted = granted) }
            if (granted) advanceStep()
        }
    }

    fun requestRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val granted = RootUtils.requestRoot()
            _uiState.update { it.copy(rootGranted = granted, isLoading = false) }
            if (granted) advanceStep()
        }
    }

    private fun advanceStep() {
        val state = _uiState.value
        when {
            !state.rootGranted -> _uiState.update { it.copy(authStep = AuthStep.CHECK_ROOT) }
            !state.isLoggedIn -> _uiState.update { it.copy(authStep = AuthStep.LOGIN) }
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
                        prefs.saveForkRepoName(fork.name)
                        val compareResult = github.checkBehind(
                            username, fork.name,
                            "${BuildConfig.SOURCE_REPO_OWNER}:${fork.defaultBranch}",
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val wfResult = github.getWorkflowId(username, repoName, "kernel-custom.yml")
            if (wfResult is Result.Error) {
                _uiState.update { it.copy(isLoading = false, error = wfResult.message) }
                return@launch
            }
            val wfId = (wfResult as Result.Success).data
            val inputs = config.toInputMap()
            when (val r = github.dispatchWorkflow(username, repoName, wfId, inputs)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, buildStatus = BuildStatus.QUEUED)
                    }
                    delay(5000) // wait for GH to create the run
                    findAndMonitorLatestRun(username, repoName)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                else -> {}
            }
        }
    }

    private suspend fun findAndMonitorLatestRun(owner: String, repo: String) {
        when (val r = github.listRecentRuns(owner, repo, 5)) {
            is Result.Success -> {
                val run = r.data.firstOrNull()
                if (run != null) {
                    prefs.saveLastRunId(run.id)
                    _uiState.update { it.copy(currentRun = run, buildStatus = BuildStatus.QUEUED) }
                    BuildMonitorService.startMonitoring(getApplication(), owner, repo, run.id)
                }
            }
            else -> {}
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

    fun loadArtifacts(runId: Long) {
        val state = _uiState.value
        val username = state.user?.login ?: return
        val repoName = state.forkRepo?.name ?: return
        viewModelScope.launch {
            when (val r = github.listArtifacts(username, repoName, runId)) {
                is Result.Success -> _uiState.update { it.copy(artifacts = r.data) }
                else -> {}
            }
        }
    }

    fun downloadArtifact(artifact: com.abk.kernel.data.model.Artifact) {
        viewModelScope.launch {
            val token = prefs.accessToken.first() ?: return@launch
            _uiState.update { it.copy(isDownloading = true) }
            NotificationUtils.notifyDownloadProgress(getApplication(), 0, artifact.name)
            val result = DownloadUtils.downloadArtifact(
                getApplication(), token, artifact
            ) { pct ->
                NotificationUtils.notifyDownloadProgress(getApplication(), pct, artifact.name)
                _uiState.update { s ->
                    s.copy(downloadProgress = s.downloadProgress + (artifact.id to pct))
                }
            }
            if (result != null) {
                NotificationUtils.notifyDownloadDone(getApplication(), artifact.name)
                _uiState.update { s ->
                    s.copy(
                        isDownloading = false,
                        downloadedArtifacts = s.downloadedArtifacts + result,
                        downloadProgress = s.downloadProgress - artifact.id
                    )
                }
            } else {
                _uiState.update { it.copy(isDownloading = false, error = "下载失败: ${artifact.name}") }
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setAutoDownload(v: Boolean) = viewModelScope.launch { prefs.setAutoDownload(v) }
    fun setNotifyBuild(v: Boolean) = viewModelScope.launch { prefs.setNotifyBuild(v) }
    fun setThemeMode(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
        super.onCleared()
    }
}

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
