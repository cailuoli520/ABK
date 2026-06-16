package com.abk.kernel.utils

import com.abk.kernel.data.model.GitHubSecretPublicKey
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import java.security.KeyPairGenerator
import java.util.Base64

data class ForkSigningMaterial(
    val privateKeyPem: String,
    val privateKeyBase64: String,
    val publicKeyPem: String,
    val publicKeyBase64: String
)

object ForkSigningManager {
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    fun generateSigningMaterial(): ForkSigningMaterial {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        return ForkSigningMaterial(
            privateKeyPem = pem("PRIVATE KEY", pair.private.encoded),
            privateKeyBase64 = Base64.getEncoder().encodeToString(pair.private.encoded),
            publicKeyPem = pem("PUBLIC KEY", pair.public.encoded),
            publicKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        )
    }

    fun encryptSecretForGitHub(
        secretValue: String,
        publicKey: GitHubSecretPublicKey
    ): String {
        val hexCipher = sodium.cryptoBoxSealEasy(
            secretValue,
            Key.fromBase64String(publicKey.key, Base64.getEncoder())
        )
        val ok = hexCipher != null && hexCipher.isNotBlank()
        require(ok) { "Failed to encrypt secret with repository public key" }
        return Base64.getEncoder().encodeToString(hexToBytes(requireNotNull(hexCipher)))
    }

    fun publicKeyPemFromBase64(base64: String): String =
        pem("PUBLIC KEY", Base64.getDecoder().decode(base64))

    private fun pem(type: String, bytes: ByteArray): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return buildString {
            append("-----BEGIN $type-----\n")
            append(encoded)
            append("\n-----END $type-----\n")
        }
    }

    private fun hexToBytes(value: String): ByteArray {
        val clean = value.trim()
        require(clean.length % 2 == 0) { "Encrypted secret hex has odd length" }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
