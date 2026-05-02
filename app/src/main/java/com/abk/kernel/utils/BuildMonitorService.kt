package com.abk.kernel.utils

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.repository.GitHubRepository
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.data.repository.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BuildMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.abk.kernel.BUILD_MONITOR_START"
        const val ACTION_STOP = "com.abk.kernel.BUILD_MONITOR_STOP"
        const val EXTRA_RUN_ID = "run_id"
        const val EXTRA_OWNER = "owner"
        const val EXTRA_REPO = "repo"

        // Broadcast action for UI updates
        const val BROADCAST_STATUS = "com.abk.kernel.BUILD_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RUN = "run_json"

        fun startMonitoring(context: Context, owner: String, repo: String, runId: Long) {
            val intent = Intent(context, BuildMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
                putExtra(EXTRA_RUN_ID, runId)
            }
            context.startForegroundService(intent)
        }

        fun stopMonitoring(context: Context) {
            context.startService(Intent(context, BuildMonitorService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val runId = intent.getLongExtra(EXTRA_RUN_ID, -1L)
                val owner = intent.getStringExtra(EXTRA_OWNER) ?: return START_NOT_STICKY
                val repo = intent.getStringExtra(EXTRA_REPO) ?: return START_NOT_STICKY
                startForeground(NotificationUtils.NOTIF_ID_BUILD, buildForegroundNotification())
                startMonitoring(owner, repo, runId)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        NotificationUtils.createChannels(this)
        return android.app.NotificationCompat.Builder(this, NotificationUtils.CHANNEL_BUILD)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(getString(com.abk.kernel.R.string.notif_build_running))
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring(owner: String, repo: String, runId: Long) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            val prefs = PreferencesRepository(applicationContext)
            val token = prefs.accessToken.first() ?: return@launch
            val github = GitHubRepository()
            github.updateToken(token)

            NotificationUtils.notifyBuildRunning(applicationContext)

            while (isActive) {
                val result = github.getWorkflowRun(owner, repo, runId)
                if (result is Result.Success) {
                    val run = result.data
                    broadcastStatus(run)
                    when (run.status) {
                        "completed" -> {
                            val success = run.conclusion == "success"
                            NotificationUtils.notifyBuildDone(applicationContext, success)
                            stopSelf()
                            break
                        }
                        "queued", "waiting", "in_progress", "requested" -> {
                            delay(30_000)
                        }
                        else -> {
                            stopSelf()
                            break
                        }
                    }
                } else {
                    delay(30_000)
                }
            }
        }
    }

    private fun broadcastStatus(run: WorkflowRun) {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_STATUS, run.status)
            putExtra(EXTRA_RUN, com.google.gson.Gson().toJson(run))
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
