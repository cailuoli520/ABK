package com.abk.kernel.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
            lower.contains("boot") && lower.endsWith(".zip").not() -> ArtifactType.KERNEL_IMG
            lower.contains("anykernel") || lower.endsWith("ak3.zip") -> ArtifactType.ANYKERNEL3
            lower.contains("manager") || lower.contains("ksu") || lower.contains("ksud") -> ArtifactType.KSU_MANAGER
            lower.contains("susfs") || lower.contains("module") -> ArtifactType.SUSFS_MODULE
            else -> ArtifactType.OTHER
        }
    }

    suspend fun downloadArtifact(
        context: Context,
        token: String,
        artifact: Artifact,
        onProgress: (Int) -> Unit = {}
    ): DownloadedArtifact? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(artifact.archiveDownloadUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
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
                        val pct = (downloaded * 100 / totalBytes).toInt()
                        onProgress(pct)
                    }
                }
            }

            // Unzip into named folder
            val outDir = File(downloadsDir, artifact.name)
            outDir.mkdirs()
            unzip(zipFile, outDir)
            zipFile.delete()

            // Find primary file
            val primaryFile = outDir.walkTopDown()
                .firstOrNull { it.isFile && !it.name.startsWith(".") }
                ?: outDir

            DownloadedArtifact(
                id = artifact.id,
                name = artifact.name,
                filePath = primaryFile.absolutePath,
                type = classifyArtifact(artifact.name),
                sizeBytes = primaryFile.length()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun unzip(zipFile: File, outDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(outDir, entry.name)
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

    fun openFile(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
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
