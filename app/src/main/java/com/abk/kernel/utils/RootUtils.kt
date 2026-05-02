package com.abk.kernel.utils

import com.topjohnwu.superuser.Shell

object RootUtils {

    fun init() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    fun isRootAvailable(): Boolean {
        return Shell.isAppGrantedRoot() == true
    }

    fun requestRoot(): Boolean {
        return try {
            Shell.getShell() // triggers root request
            Shell.isAppGrantedRoot() == true
        } catch (e: Exception) {
            false
        }
    }

    fun flashImage(imagePath: String, partition: String): ShellResult {
        val result = Shell.cmd("dd if=\"$imagePath\" of=/dev/block/by-name/$partition").exec()
        return ShellResult(result.isSuccess, result.out + result.err)
    }

    fun installModule(zipPath: String): ShellResult {
        // ksud is the KernelSU manager daemon
        val result = Shell.cmd("ksud module install \"$zipPath\"").exec()
        return ShellResult(result.isSuccess, result.out + result.err)
    }

    fun getKernelVersion(): String {
        val result = Shell.cmd("uname -r").exec()
        return if (result.isSuccess) result.out.firstOrNull() ?: "Unknown" else "Unknown"
    }

    fun getKsuVersion(): String {
        val result = Shell.cmd("ksud --version 2>/dev/null || echo N/A").exec()
        return result.out.firstOrNull() ?: "N/A"
    }

    fun getSusfsVersion(): String {
        val result = Shell.cmd("cat /proc/self/attr/prev 2>/dev/null | head -1 || echo N/A").exec()
        return result.out.firstOrNull() ?: "N/A"
    }

    data class ShellResult(val success: Boolean, val output: List<String>)
}
