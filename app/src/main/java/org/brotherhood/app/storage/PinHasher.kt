package org.brotherhood.app.storage

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    data class Record(val salt: String, val hash: String)

    fun create(pin: CharArray): Record {
        validate(pin)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        return Record(encoder.encodeToString(salt), encoder.encodeToString(hash))
    }

    fun verify(pin: CharArray, salt: String, expectedHash: String): Boolean {
        if (pin.size !in 6..64) return false
        return runCatching {
            val actual = derive(pin, decoder.decode(salt))
            MessageDigest.isEqual(actual, decoder.decode(expectedHash)).also { actual.fill(0) }
        }.getOrDefault(false)
    }

    private fun validate(pin: CharArray) {
        require(pin.size in 6..64) { "Il PIN deve contenere almeno 6 caratteri" }
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
