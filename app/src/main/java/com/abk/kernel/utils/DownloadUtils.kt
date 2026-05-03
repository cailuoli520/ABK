package com.abk.kernel.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.abk.kernel.data.model.Artifact
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object DownloadUtils {

    private val client = OkHttpClient.Builder().build()

    fun classifyArtifact(name: String): ArtifactType {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".img") && lower.contains("boot") -> ArtifactType.KERNEL_IMG
            lower.endsWith(".zip") && (lower.contains("anykernel") || lower.contains("ak3")) -> ArtifactType.ANYKERNEL3
            lower.endsWith(".apk") && (
                lower.contains("manager") ||
                    lower.contains("kernelsu") ||
                    lower.contains("ksu") ||
                    lower.contains("suki")
                ) -> ArtifactType.KSU_MANAGER
            lower.endsWith(".zip") && (lower.contains("susfs") || lower.contains("module")) -> ArtifactType.SUSFS_MODULE
            else -> ArtifactType.OTHER
        }
    }

    suspend fun downloadArtifact(
        context: Context,
        token: String,
        artifact: Artifact,
        onProgress: (Int) -> Unit = {}
    ): List<DownloadedArtifact> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(artifact.archiveDownloadUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body ?: return@withContext emptyList()
            val totalBytes = artifact.sizeInBytes.coerceAtLeast(1L)

            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val zipFile = File(downloadsDir, "${artifact.name}.zip")

            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloaded = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val pct = (downloaded * 100 / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(pct)
                    }
                }
            }

            // Unzip into named folder
            val outDir = File(downloadsDir, artifact.name)
            if (outDir.exists()) outDir.deleteRecursively()
            outDir.mkdirs()
            unzip(zipFile, outDir)
            zipFile.delete()

            collectCandidateFiles(outDir).mapIndexed { index, file ->
                DownloadedArtifact(
                    id = artifact.id * 1000 + index + 1,
                    name = file.name,
                    filePath = file.absolutePath,
                    type = classifyArtifact(file.name),
                    sizeBytes = file.length()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun unzip(zipFile: File, outDir: File) {
        val outCanonical = outDir.canonicalFile
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(outDir, entry.name).canonicalFile
                if (!file.path.startsWith(outCanonical.path + File.separator)) {
                    throw SecurityException("Unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun collectCandidateFiles(outDir: File): List<File> {
        val files = outDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .toList()

        val candidates = files.filter { file ->
            when (classifyArtifact(file.name)) {
                ArtifactType.KERNEL_IMG,
                ArtifactType.ANYKERNEL3,
                ArtifactType.KSU_MANAGER,
                ArtifactType.SUSFS_MODULE -> true
                ArtifactType.OTHER -> false
            }
        }

        val source = candidates.ifEmpty { files }
        return source.sortedWith(
            compareBy<File> {
                when (classifyArtifact(it.name)) {
                    ArtifactType.KERNEL_IMG -> 0
                    ArtifactType.ANYKERNEL3 -> 1
                    ArtifactType.KSU_MANAGER -> 2
                    ArtifactType.SUSFS_MODULE -> 3
                    ArtifactType.OTHER -> 4
                }
            }.thenBy { it.name }
        )
    }

    fun openFile(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "打开文件"))
        }
    }

    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
