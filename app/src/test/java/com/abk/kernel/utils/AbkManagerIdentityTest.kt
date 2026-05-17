package com.abk.kernel.utils

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkManagerIdentityTest {

    @Test
    fun mismatchSummaryReturnsNullWhenIdentityMatches() {
        val result = AbkManagerIdentity.SelfCheckResult(
            expectedPackageName = "com.abk.kernel",
            actualPackageName = "com.abk.kernel",
            expectedCertSha256 = "abc123",
            actualCertSha256 = linkedSetOf("abc123"),
            expectedCertSize = 1407,
            actualCertSizes = linkedSetOf(1407)
        )

        assertNull(result.mismatchSummary())
        assertTrue(result.matchesOfficialManagerIdentity)
    }

    @Test
    fun mismatchSummaryIncludesAllFailedDimensions() {
        val result = AbkManagerIdentity.SelfCheckResult(
            expectedPackageName = "com.abk.kernel",
            actualPackageName = "com.example.other",
            expectedCertSha256 = "abc123",
            actualCertSha256 = linkedSetOf("deadbeef"),
            expectedCertSize = 1407,
            actualCertSizes = linkedSetOf(512)
        )

        val summary = result.mismatchSummary().orEmpty()

        assertTrue(summary.contains("包名不匹配"))
        assertTrue(summary.contains("证书 SHA-256 不匹配"))
        assertTrue(summary.contains("证书大小不匹配"))
    }
}
