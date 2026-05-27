package com.srspassword.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
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
 *  1. Master-PIN hashing  (SHA-256 + random salt, stored as "saltHex:hashHex").
 *  2. Biometric-enrollment-change detection via an Android KeyStore key.
 *
 * ── Why the key must have setUserAuthenticationRequired(true) ────────────────
 *
 * Android documentation states:
 *   "setInvalidatedByBiometricEnrollment(true) has effect ONLY for keys that
 *    require user authentication (setUserAuthenticationRequired(true))."
 *
 * Without that flag the key is never tied to biometric enrollment, so Android
 * never marks it as permanently invalid when a fingerprint is added or removed,
 * and Cipher.init() never throws KeyPermanentlyInvalidatedException.
 * Every previous attempt in this codebase silently failed for this reason.
 *
 * ── How the detection check works ────────────────────────────────────────────
 *
 * We call Cipher.init() on the key without a BiometricPrompt CryptoObject:
 *
 *   • Key permanently invalidated (enrollment changed)
 *     → KeyPermanentlyInvalidatedException  →  return true  ✓
 *
 *   • Key valid but user has not authenticated in this process session
 *     (normal cold-start or resume)
 *     → UserNotAuthenticatedException       →  return false ✓
 *
 *   • Anything else (no key yet, device not supported, etc.)
 *     → Exception                           →  return false ✓
 *
 * The two exceptions are mutually exclusive: Android evaluates permanent
 * invalidation before checking session authentication, so KPIE always wins
 * when the enrollment has changed.
 */
@Singleton
class PinManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS         = "srs_vault_biometric_validity_key"
        private const val AES_GCM           = "AES/GCM/NoPadding"
    }

    // ── PIN operations ────────────────────────────────────────────────────────

    /**
     * Derives a storable hash from [pin].
     * Returns "saltHex:sha256Hex" so the salt is embedded for later verification.
     */
    fun hashPin(pin: String): String {
        val salt    = ByteArray(16).also { SecureRandom().nextBytes(it) }
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

    // ── Biometric-change detection ────────────────────────────────────────────

    /**
     * Creates (or replaces) a KeyStore AES key correctly bound to biometric
     * enrollment. Call this after every successful authentication so the baseline
     * always reflects the most recent valid enrollment state.
     *
     * Critical flags:
     *  • setUserAuthenticationRequired(true)  — binds the key to biometric auth;
     *    WITHOUT this, setInvalidatedByBiometricEnrollment has NO effect at all.
     *  • setUserAuthenticationValidityDurationSeconds(-1) — per-use only, requires
     *    a BiometricPrompt CryptoObject; strongest binding, no time-based fallback.
     *  • setInvalidatedByBiometricEnrollment(true) — marks the key as permanently
     *    invalid whenever the device's biometric enrollment changes.
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
                    // ↓↓ These two lines are the critical fix ↓↓
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(-1)
                    // ↑↑ Without setUserAuthenticationRequired(true), the line  ↑↑
                    // ↑↑ below is completely ignored by Android's KeyStore.     ↑↑
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
            )
            kg.generateKey()
        } catch (_: Exception) {
            // Older devices / emulators without a secure element — detection
            // simply won't work; fail silently rather than crash.
        }
    }

    /**
     * Returns **true** when the device's biometric enrollment has changed since
     * [createBiometricBoundKey] was last called (i.e. a fingerprint was added,
     * removed, or all biometrics were cleared).
     *
     * Returns **false** in all safe-to-continue cases:
     *  - Key does not exist yet (first run / PIN not yet set up).
     *  - Key is still valid; enrollment unchanged.
     *  - Device does not support hardware-backed key invalidation.
     */
    fun isBiometricKeyInvalidated(): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            if (!ks.containsAlias(KEY_ALIAS)) return false
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return false
            // Deliberately init without a CryptoObject / active biometric session.
            // The only outcomes we care about are KPIE (changed) vs UAE (unchanged).
            Cipher.getInstance(AES_GCM).init(Cipher.ENCRYPT_MODE, key)
            false // succeeded — key is valid, biometrics have not changed
        } catch (_: KeyPermanentlyInvalidatedException) {
            true  // ← enrollment changed: fingerprint added, removed, or wiped
        } catch (_: UserNotAuthenticatedException) {
            false // ← key is intact; user simply hasn't biometrically authed yet
        } catch (_: Exception) {
            false // ← device doesn't support this mechanism; treat as no-change
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
