package com.abk.kernel.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.abk.kernel.data.model.LspInstalledModule
import com.abk.kernel.data.model.LspScopeApp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties
import java.util.zip.ZipFile

object LspModuleUtils {
    fun listInstalledModules(context: Context): List<LspInstalledModule> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            toLspInstalledModule(pm, pkg)
        }.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    fun listScopeApps(context: Context): List<LspScopeApp> {
        val pm = context.packageManager
        val installed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }
        return installed.map { info ->
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            LspScopeApp(
                packageName = info.packageName,
                label = label,
                isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedWith(
            compareBy<LspScopeApp> { it.isSystemApp }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )
    }

    private fun toLspInstalledModule(pm: PackageManager, pkg: PackageInfo): LspInstalledModule? {
        val app = pkg.applicationInfo ?: return null
        val modern = getModernModuleApk(app)
        val legacy = isLegacyModule(app)
        if (modern == null && !legacy) return null

        val label = runCatching {
            pm.getApplicationLabel(app).toString()
        }.getOrDefault(pkg.packageName)

        return if (modern != null) {
            modern.use { zip ->
                val props = Properties()
                zip.getEntry("META-INF/xposed/module.prop")?.let { entry ->
                    zip.getInputStream(entry).use(props::load)
                }
                val scopeHints = zip.getEntry("META-INF/xposed/scope.list")?.let { entry ->
                    BufferedReader(InputStreamReader(zip.getInputStream(entry))).use { reader ->
                        reader.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                    }
                }.orEmpty()
                val entryPoints = zip.getEntry("META-INF/xposed/java_init.list")?.let { entry ->
                    BufferedReader(InputStreamReader(zip.getInputStream(entry))).use { reader ->
                        reader.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                    }
                }.orEmpty()
                LspInstalledModule(
                    packageName = pkg.packageName,
                    label = label,
                    versionName = pkg.versionName.orEmpty(),
                    versionCode = pkg.longVersionCode,
                    description = props.getProperty("description", ""),
                    modern = true,
                    legacy = legacy,
                    minVersion = props.getProperty("minApiVersion", "0").toIntOrNull() ?: 0,
                    targetVersion = props.getProperty("targetApiVersion", "0").toIntOrNull() ?: 0,
                    scopeHints = scopeHints,
                    entryPoints = entryPoints,
                    enabled = false
                )
            }
        } else {
            val metaData = app.metaData
            val scopeString = metaData?.getString("xposedscope").orEmpty()
            LspInstalledModule(
                packageName = pkg.packageName,
                label = label,
                versionName = pkg.versionName.orEmpty(),
                versionCode = pkg.longVersionCode,
                description = metaData?.getString("xposeddescription").orEmpty(),
                modern = false,
                legacy = true,
                minVersion = extractIntPart(metaData?.get("xposedminversion")?.toString().orEmpty()),
                targetVersion = 0,
                scopeHints = scopeString.split(';').map { it.trim() }.filter { it.isNotBlank() },
                entryPoints = emptyList(),
                enabled = false
            )
        }
    }

    private fun getModernModuleApk(info: ApplicationInfo): ZipFile? {
        val splitSourceDirs = info.splitSourceDirs
        val apks = if (splitSourceDirs != null) {
            splitSourceDirs + info.sourceDir
        } else {
            arrayOf(info.sourceDir)
        }
        for (apk in apks) {
            try {
                val zip = ZipFile(apk)
                if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                    return zip
                }
                zip.close()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isLegacyModule(info: ApplicationInfo): Boolean =
        info.metaData?.containsKey("xposedminversion") == true

    private fun extractIntPart(str: String): Int {
        var result = 0
        for (char in str) {
            if (char !in '0'..'9') break
            result = result * 10 + (char - '0')
        }
        return result
    }
}
