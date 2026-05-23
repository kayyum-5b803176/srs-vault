package com.srspassword.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages:
 *  1. Master-PIN hashing (SHA-256 + random salt, stored as "saltHex:hashHex").
 *  2. Biometric-enrollment-change detection via an Android KeyStore key that is
 *     automatically invalidated whenever the device's biometric set changes
 *     (new finger enrolled, existing finger removed, etc.).
 */
@Singleton
class PinManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "srs_vault_biometric_validity_key"
        private const val AES_GCM = "AES/GCM/NoPadding"
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PIN operations
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Derives a storable hash from [pin].
     * Returns "saltHex:sha256Hex" so the salt is embedded for later verification.
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        return "$saltHex:${sha256(saltHex + pin)}"
    }

    /**
     * Returns true iff [pin] matches the previously-stored [storedHash]
     * (format produced by [hashPin]).
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val (saltHex, expectedHash) = parts
        return sha256(saltHex + pin) == expectedHash
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Biometric-change detection
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates (or replaces) a KeyStore AES key bound to the *current* biometric
     * enrollment. Android automatically invalidates this key when the enrollment
     * changes. Call this right after the user successfully sets up or verifies
     * their master PIN so the baseline is always fresh.
     */
    fun createBiometricBoundKey() {
        try {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            kg.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
            )
            kg.generateKey()
        } catch (_: Exception) {
            // Some older/emulator devices don't support this — fail silently.
        }
    }

    /**
     * Returns **true** if the biometric enrollment has changed since the last
     * call to [createBiometricBoundKey], meaning the app should require the
     * master PIN before re-binding to the new enrollment.
     *
     * Returns **false** when:
     *  - The key doesn't exist yet (first run / no PIN set).
     *  - The key is still valid (enrollment unchanged).
     *  - The device's KeyStore doesn't support key invalidation (safe fallback).
     */
    fun isBiometricKeyInvalidated(): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            if (!ks.containsAlias(KEY_ALIAS)) return false
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return false
            Cipher.getInstance(AES_GCM).init(Cipher.ENCRYPT_MODE, key)
            false // key still valid → biometrics unchanged
        } catch (_: KeyPermanentlyInvalidatedException) {
            true  // biometric enrollment changed
        } catch (_: Exception) {
            false // device doesn't support this; treat as no-change
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
