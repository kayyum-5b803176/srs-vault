package com.srspassword.app.encryption

import android.util.Base64
import com.google.gson.Gson
import com.srspassword.app.data.PasswordCardExportDto
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted export/import using:
 * - PBKDF2WithHmacSHA256 key derivation (310,000 iterations — NIST 2023 recommendation)
 * - AES-256-GCM for the vault payload
 * - Random 16-byte salt + 12-byte IV embedded in output
 *
 * Export format (base64 of):
 *   [4 bytes: "SRSV"] [1 byte: version] [16 bytes: salt] [12 bytes: IV] [N bytes: GCM ciphertext]
 */
@Singleton
class VaultExporter @Inject constructor(private val gson: Gson) {

    companion object {
        private const val MAGIC       = "SRSV"
        private const val VERSION     = 1.toByte()
        private const val PBKDF2_ITER = 310_000
        private const val KEY_LENGTH  = 256
        private const val SALT_BYTES  = 16
        private const val IV_BYTES    = 12
        private const val GCM_TAG_LEN = 128
        private const val ALGORITHM   = "AES/GCM/NoPadding"
        private const val KDF_ALGO    = "PBKDF2WithHmacSHA256"
    }

    data class ExportPayload(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val cards: List<PasswordCardExportDto>
    )

    /** Serialize and encrypt the vault to a base64 string for file export. */
    fun exportVault(cards: List<PasswordCardExportDto>, passphrase: String): String {
        val payload   = gson.toJson(ExportPayload(cards = cards))
        val salt      = SecureRandom().let { ByteArray(SALT_BYTES).also(it::nextBytes) }
        val iv        = SecureRandom().let { ByteArray(IV_BYTES).also(it::nextBytes) }
        val secretKey = deriveKey(passphrase.toCharArray(), salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LEN, iv))
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        // Pack: magic(4) + version(1) + salt(16) + iv(12) + ciphertext(N)
        val packed = ByteArray(4 + 1 + SALT_BYTES + IV_BYTES + ciphertext.size)
        var pos = 0
        MAGIC.toByteArray().copyInto(packed, pos);         pos += 4
        packed[pos] = VERSION;                              pos += 1
        salt.copyInto(packed, pos);                        pos += SALT_BYTES
        iv.copyInto(packed, pos);                          pos += IV_BYTES
        ciphertext.copyInto(packed, pos)

        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** Decrypt and deserialize a base64 vault string. Returns null if passphrase is wrong. */
    fun importVault(base64Data: String, passphrase: String): ExportPayload? {
        return try {
            val packed = Base64.decode(base64Data, Base64.NO_WRAP)

            // Validate magic
            val magic = String(packed.copyOfRange(0, 4))
            require(magic == MAGIC) { "Not a valid SRS vault file" }

            var pos = 5  // skip magic(4) + version(1)
            val salt       = packed.copyOfRange(pos, pos + SALT_BYTES);  pos += SALT_BYTES
            val iv         = packed.copyOfRange(pos, pos + IV_BYTES);    pos += IV_BYTES
            val ciphertext = packed.copyOfRange(pos, packed.size)

            val secretKey = deriveKey(passphrase.toCharArray(), salt)
            val cipher    = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LEN, iv))

            val plaintext = cipher.doFinal(ciphertext)
            gson.fromJson(String(plaintext, Charsets.UTF_8), ExportPayload::class.java)
        } catch (e: Exception) {
            null  // Wrong passphrase or corrupted file
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF_ALGO)
        val spec    = PBEKeySpec(passphrase, salt, PBKDF2_ITER, KEY_LENGTH)
        val raw     = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }
}
