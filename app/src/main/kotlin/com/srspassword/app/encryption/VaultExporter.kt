package com.srspassword.app.encryption

import android.util.Base64
import com.google.gson.Gson
import com.srspassword.app.data.PasswordCardExportDto
import kotlinx.coroutines.delay
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted vault export/import — v2 binary format.
 *
 * Security properties:
 * ─────────────────────────────────────────────────────────────────────────────
 * Key derivation : PBKDF2-HMAC-SHA256 — 310,000 iterations (NIST SP 800-132)
 * Encryption     : AES-256-GCM (authenticated encryption, 128-bit tag)
 * File integrity : HMAC-SHA256 over the entire binary blob (appended last)
 *                  Verified BEFORE decryption — prevents decryption oracle attacks
 * KDF produces   : 64 bytes split into 32-byte AES key + 32-byte HMAC key
 *                  (separate keys, never reused)
 * Salt           : 16 bytes cryptographically random (unique per export)
 * IV             : 12 bytes cryptographically random (unique per export)
 * Wrong passphrase: artificial 500ms delay — rate-limits offline brute force
 *
 * Binary layout (before base64):
 *   [4]  magic "SRSV"
 *   [1]  version = 2
 *   [16] salt
 *   [12] IV
 *   [4]  card count (big-endian int32, plaintext for preview)
 *   [8]  export timestamp (big-endian int64, plaintext)
 *   [N]  AES-256-GCM ciphertext (JSON payload + 16-byte GCM tag)
 *   [32] HMAC-SHA256 over all preceding bytes
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Singleton
class VaultExporter @Inject constructor(private val gson: Gson) {

    companion object {
        private const val MAGIC         = "SRSV"
        private const val VERSION       = 2.toByte()
        private const val PBKDF2_ITER   = 310_000
        private const val KEY_MATERIAL  = 512        // 64 bytes = 32 AES + 32 HMAC
        private const val SALT_BYTES    = 16
        private const val IV_BYTES      = 12
        private const val GCM_TAG_LEN   = 128
        private const val HMAC_BYTES    = 32
        private const val AES_ALGO      = "AES/GCM/NoPadding"
        private const val KDF_ALGO      = "PBKDF2WithHmacSHA256"
        private const val HMAC_ALGO     = "HmacSHA256"
        private const val WRONG_PW_DELAY_MS = 500L
    }

    data class ExportPayload(
        val version    : Int  = 2,
        val exportedAt : Long = System.currentTimeMillis(),
        val appId      : String = "com.srspassword.app",
        val cards      : List<PasswordCardExportDto>
    )

    /** Metadata decoded from file header WITHOUT decrypting — safe to show in preview UI. */
    data class VaultPreview(
        val cardCount  : Int,
        val exportedAt : Long,
        val version    : Int
    )

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportVault(cards: List<PasswordCardExportDto>, passphrase: String): String {
        val now       = System.currentTimeMillis()
        val payload   = gson.toJson(ExportPayload(exportedAt = now, cards = cards))
        val rng       = SecureRandom()
        val salt      = ByteArray(SALT_BYTES).also(rng::nextBytes)
        val iv        = ByteArray(IV_BYTES).also(rng::nextBytes)
        val (aesKey, hmacKey) = deriveKeys(passphrase.toCharArray(), salt)

        // Encrypt payload
        val cipher = Cipher.getInstance(AES_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LEN, iv))
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        // Build binary: header + plaintext metadata + ciphertext
        val cardCount = cards.size
        val headerSize = 4 + 1 + SALT_BYTES + IV_BYTES + 4 + 8
        val body = ByteArray(headerSize + ciphertext.size)
        var pos = 0

        MAGIC.toByteArray().copyInto(body, pos);                    pos += 4
        body[pos] = VERSION;                                        pos += 1
        salt.copyInto(body, pos);                                   pos += SALT_BYTES
        iv.copyInto(body, pos);                                     pos += IV_BYTES
        // card count (big-endian int32)
        body[pos++] = (cardCount shr 24).toByte()
        body[pos++] = (cardCount shr 16).toByte()
        body[pos++] = (cardCount shr  8).toByte()
        body[pos++] = (cardCount       ).toByte()
        // export timestamp (big-endian int64)
        for (i in 7 downTo 0) body[pos++] = (now shr (i * 8)).toByte()

        ciphertext.copyInto(body, pos)

        // Append HMAC over entire body
        val mac  = Mac.getInstance(HMAC_ALGO)
        mac.init(hmacKey)
        val hmac = mac.doFinal(body)

        val final = body + hmac
        return Base64.encodeToString(final, Base64.NO_WRAP)
    }

    // ── Preview (no decryption, no passphrase needed) ─────────────────────────

    fun previewVault(base64Data: String): VaultPreview? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            require(bytes.size > 4 + 1 + SALT_BYTES + IV_BYTES + 4 + 8 + HMAC_BYTES)

            val magic = String(bytes.copyOfRange(0, 4))
            require(magic == MAGIC)

            val version = bytes[4].toInt()
            var pos = 5 + SALT_BYTES + IV_BYTES   // skip magic, version, salt, iv

            // card count (big-endian int32)
            val cardCount =
                ((bytes[pos].toInt() and 0xFF) shl 24) or
                ((bytes[pos+1].toInt() and 0xFF) shl 16) or
                ((bytes[pos+2].toInt() and 0xFF) shl 8) or
                (bytes[pos+3].toInt() and 0xFF)
            pos += 4

            // export timestamp (big-endian int64)
            var ts = 0L
            for (i in 0 until 8) ts = (ts shl 8) or (bytes[pos + i].toLong() and 0xFF)

            VaultPreview(cardCount = cardCount, exportedAt = ts, version = version)
        } catch (e: Exception) { null }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Decrypt and deserialize. Returns null if passphrase is wrong or file is tampered.
     * Enforces artificial delay on failure to slow brute-force.
     */
    suspend fun importVault(base64Data: String, passphrase: String): ExportPayload? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            require(bytes.size > 4 + 1 + SALT_BYTES + IV_BYTES + 4 + 8 + HMAC_BYTES)

            // Validate magic
            require(String(bytes.copyOfRange(0, 4)) == MAGIC) { "Invalid vault file" }

            // Split body and HMAC
            val body = bytes.copyOfRange(0, bytes.size - HMAC_BYTES)
            val fileHmac = bytes.copyOfRange(bytes.size - HMAC_BYTES, bytes.size)

            // Read salt for key derivation (before HMAC check — salt is not secret)
            val salt = bytes.copyOfRange(5, 5 + SALT_BYTES)
            val (aesKey, hmacKey) = deriveKeys(passphrase.toCharArray(), salt)

            // Verify HMAC FIRST — prevents decryption oracle attacks
            val mac      = Mac.getInstance(HMAC_ALGO)
            mac.init(hmacKey)
            val expected = mac.doFinal(body)
            if (!expected.constantTimeEquals(fileHmac)) {
                delay(WRONG_PW_DELAY_MS)    // rate-limit brute force
                return null
            }

            // Extract IV and ciphertext
            var pos  = 5 + SALT_BYTES
            val iv   = bytes.copyOfRange(pos, pos + IV_BYTES);  pos += IV_BYTES
            pos     += 4 + 8   // skip card count + timestamp (already in plaintext)
            val ciphertext = body.copyOfRange(pos, body.size)

            // Decrypt
            val cipher = Cipher.getInstance(AES_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LEN, iv))
            val plaintext = cipher.doFinal(ciphertext)

            gson.fromJson(String(plaintext, Charsets.UTF_8), ExportPayload::class.java)
        } catch (e: Exception) {
            delay(WRONG_PW_DELAY_MS)
            null
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Derives two independent 256-bit keys from one passphrase using PBKDF2.
     * First 32 bytes → AES key. Last 32 bytes → HMAC key.
     */
    private fun deriveKeys(passphrase: CharArray, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val factory  = SecretKeyFactory.getInstance(KDF_ALGO)
        val spec     = PBEKeySpec(passphrase, salt, PBKDF2_ITER, KEY_MATERIAL)
        val material = factory.generateSecret(spec).encoded
        spec.clearPassword()
        passphrase.fill('\u0000')   // zero passphrase in memory
        val aesKey  = SecretKeySpec(material.copyOfRange(0, 32),  "AES")
        val hmacKey = SecretKeySpec(material.copyOfRange(32, 64), "HmacSHA256")
        material.fill(0)
        return Pair(aesKey, hmacKey)
    }

    /** Constant-time comparison — prevents timing oracle on HMAC. */
    private fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
        if (size != other.size) return false
        var diff = 0
        for (i in indices) diff = diff or (this[i].toInt() xor other[i].toInt())
        return diff == 0
    }
}
