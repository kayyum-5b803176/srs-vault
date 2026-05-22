package com.srspassword.app.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption backed by Android Keystore.
 *
 * Keys never leave the secure hardware element (on supported devices).
 * Each encryption uses a fresh random 12-byte IV.
 * Authentication tag is 128 bits (GCM default).
 */
@Singleton
class PasswordEncryptor @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS         = "SRSPasswordVaultKey_v1"
        private const val ALGORITHM         = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE        = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING           = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION    = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val GCM_TAG_LENGTH    = 128
        private const val KEY_SIZE          = 256
    }

    init { ensureKeyExists() }

    /** Encrypt plaintext password → returns (base64Ciphertext, base64IV) */
    fun encrypt(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv         = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return Pair(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /** Decrypt using stored ciphertext + IV → plaintext password */
    fun decrypt(base64Ciphertext: String, base64Iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv     = Base64.decode(base64Iv, Base64.NO_WRAP)
        val spec   = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)

        val ciphertext = Base64.decode(base64Ciphertext, Base64.NO_WRAP)
        val plaintext  = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(KEY_SIZE)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setUserAuthenticationRequired(false)  // Key accessible after biometric unlock via app layer
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ensureKeyExists() { getOrCreateKey() }
}
