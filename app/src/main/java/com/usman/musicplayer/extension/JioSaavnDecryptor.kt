package com.usman.musicplayer.extension

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Native implementation of JioSaavn's DES-based stream URL decryption.
 * Decrypts encrypted_media_url into a playable audio stream URL.
 */
object JioSaavnDecryptor {
    private const val ALGORITHM = "DES"
    private const val TRANSFORMATION = "DES/ECB/PKCS5Padding"
    private const val SECRET_KEY = "38346591"

    fun decryptUrl(encryptedMediaUrl: String?): String? {
        if (encryptedMediaUrl.isNullOrBlank()) {
            android.util.Log.w("USMAN_PLAYBACK", "decryptUrl: Input string is null or blank")
            return null
        }
        
        return try {
            // 1. Clean and decode the Base64 string
            val cleaned = encryptedMediaUrl.trim()
            val decodedBytes = Base64.decode(cleaned, Base64.DEFAULT)

            // 2. Setup DES key spec
            val keySpec = SecretKeySpec(SECRET_KEY.toByteArray(Charsets.UTF_8), ALGORITHM)

            // 3. Initialize Cipher in Decrypt Mode
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec)

            // 4. Perform decryption
            val decryptedBytes = cipher.doFinal(decodedBytes)

            // 5. Convert bytes back to standard UTF-8 string
            String(decryptedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            android.util.Log.e("USMAN_PLAYBACK", "Decryption failed for input: ${encryptedMediaUrl.take(20)}..., error: ${e.message}", e)
            null
        }
    }
}
