package org.brotherhood.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.brotherhood.app.model.AppState

class SecureStateStore(context: Context) {
    private val stateFile = File(context.noBackupFilesDir, "brotherhood-state-v1.enc")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val random = SecureRandom()

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext AppState()
        val bytes = stateFile.readBytes()
        require(bytes.size >= HEADER_BYTES + GCM_TAG_BYTES) { "Archivio locale danneggiato" }
        require(bytes[0] == FORMAT_VERSION) { "Versione archivio non supportata" }
        val iv = bytes.copyOfRange(1, HEADER_BYTES)
        val ciphertext = bytes.copyOfRange(HEADER_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(ciphertext)
        try {
            json.decodeFromString<AppState>(plaintext.decodeToString())
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        val plaintext = json.encodeToString(state).encodeToByteArray()
        try {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext)
            val temp = File(stateFile.parentFile, "${stateFile.name}.tmp")
            temp.outputStream().use { output ->
                output.write(byteArrayOf(FORMAT_VERSION))
                output.write(iv)
                output.write(encrypted)
                output.flush()
                if (output is java.io.FileOutputStream) output.fd.sync()
            }
            check(temp.renameTo(stateFile) || run {
                stateFile.delete()
                temp.renameTo(stateFile)
            }) { "Impossibile salvare l'archivio locale" }
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun deleteIdentityAndData() = withContext(Dispatchers.IO) {
        if (stateFile.exists()) stateFile.delete()
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "brotherhood_state_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val HEADER_BYTES = 1 + IV_BYTES
        private const val GCM_TAG_BYTES = 16
        private const val FORMAT_VERSION: Byte = 1
    }
}
