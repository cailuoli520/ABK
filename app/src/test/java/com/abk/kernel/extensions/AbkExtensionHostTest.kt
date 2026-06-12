package com.abk.kernel.extensions

import com.abk.kernel.data.model.AbkRuntimeModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkExtensionHostTest {
    @Test
    fun `does not expose modules that only declare an extension id`() {
        val module = AbkRuntimeModule(
            id = "module-only",
            extensionId = "demo.extension"
        )

        assertFalse(abkShouldExposeManagedExtension(module, hasDiscoveredApp = false))
    }

    @Test
    fun `exposes entries backed by a discovered app or explicit companion metadata`() {
        val discoveredOnly = AbkRuntimeModule(
            id = "installed-app",
            extensionId = "demo.extension"
        )
        val declaredCompanion = AbkRuntimeModule(
            id = "downloadable-app",
            extensionId = "demo.extension",
            companionDownloadUrl = "https://example.invalid/companion.apk"
        )

        assertTrue(abkShouldExposeManagedExtension(discoveredOnly, hasDiscoveredApp = true))
        assertTrue(abkShouldExposeManagedExtension(declaredCompanion, hasDiscoveredApp = false))
    }
}
