package org.brotherhood.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
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
    private val atomicStateFile = AtomicFile(stateFile)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext AppState()
        val bytes = atomicStateFile.openRead().use { it.readBytes() }
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
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // With randomized encryption enabled, Android Keystore must generate the IV.
            // Supplying an IV from the app causes "Caller-provided IV not permitted".
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = requireNotNull(cipher.iv) { "Android Keystore non ha generato un IV" }
            require(iv.size == IV_BYTES) { "Dimensione IV GCM non valida: ${iv.size}" }
            val encrypted = cipher.doFinal(plaintext)

            var pendingOutput: FileOutputStream? = null
            try {
                val output = atomicStateFile.startWrite()
                pendingOutput = output
                output.write(byteArrayOf(FORMAT_VERSION))
                output.write(iv)
                output.write(encrypted)
                output.flush()
                output.fd.sync()
                atomicStateFile.finishWrite(output)
                pendingOutput = null
            } catch (error: Throwable) {
                pendingOutput?.let(atomicStateFile::failWrite)
                throw error
            }
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun deleteIdentityAndData() = withContext(Dispatchers.IO) {
        atomicStateFile.delete()
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
