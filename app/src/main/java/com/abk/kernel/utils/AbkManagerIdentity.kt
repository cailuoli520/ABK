package com.abk.kernel.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.abk.kernel.BuildConfig
import java.security.MessageDigest

object AbkManagerIdentity {

    data class SelfCheckResult(
        val expectedPackageName: String,
        val actualPackageName: String,
        val expectedCertSha256: String,
        val actualCertSha256: Set<String>,
        val expectedCertSize: Int,
        val actualCertSizes: Set<Int>
    ) {
        val packageMatches: Boolean
            get() = actualPackageName == expectedPackageName

        val certSha256Matches: Boolean
            get() = expectedCertSha256.isNotBlank() && actualCertSha256.contains(expectedCertSha256)

        val certSizeMatches: Boolean
            get() = expectedCertSize > 0 && actualCertSizes.contains(expectedCertSize)

        val matchesOfficialManagerIdentity: Boolean
            get() = packageMatches && certSha256Matches && certSizeMatches

        fun mismatchSummary(): String? {
            if (matchesOfficialManagerIdentity) return null

            val reasons = buildList {
                if (!packageMatches) {
                    add("包名不匹配: expected=$expectedPackageName actual=$actualPackageName")
                }
                if (!certSha256Matches) {
                    add(
                        "证书 SHA-256 不匹配: expected=$expectedCertSha256 actual=${
                            actualCertSha256.sorted().joinToString(",").ifBlank { "unknown" }
                        }"
                    )
                }
                if (!certSizeMatches) {
                    add(
                        "证书大小不匹配: expected=$expectedCertSize actual=${
                            actualCertSizes.sorted().joinToString(",").ifBlank { "unknown" }
                        }"
                    )
                }
            }

            return reasons.joinToString("；")
        }
    }

    fun verifySelf(context: Context): SelfCheckResult {
        val certBlobs = runCatching {
            signingCertificateBlobs(context.packageManager, context.packageName)
        }.getOrDefault(emptyList())
        return SelfCheckResult(
            expectedPackageName = BuildConfig.ABK_MANAGER_PACKAGE,
            actualPackageName = context.packageName,
            expectedCertSha256 = BuildConfig.ABK_MANAGER_CERT_SHA256.trim().lowercase(),
            actualCertSha256 = certBlobs.mapTo(linkedSetOf(), ::sha256Hex),
            expectedCertSize = BuildConfig.ABK_MANAGER_CERT_SIZE,
            actualCertSizes = certBlobs.mapTo(linkedSetOf()) { it.size }
        )
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateBlobs(
        packageManager: PackageManager,
        packageName: String
    ): List<ByteArray> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        return packageSignatures(packageInfo).map { it.toByteArray() }
    }

    @Suppress("DEPRECATION")
    private fun packageSignatures(packageInfo: PackageInfo): Array<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptyArray<Signature>()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures ?: emptyArray<Signature>()
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
