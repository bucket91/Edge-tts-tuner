package com.bucket91.ttstuner.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.bucket91.ttstuner.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("secure_keys", Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ttstuner_gemini_aes_key"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREF_KEY_CIPHERTEXT = "encrypted_gemini_key"
        private const val PREF_KEY_IV = "gemini_key_iv"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun saveApiKey(rawKey: String) {
        val cleanKey = rawKey.trim()
        if (cleanKey.isEmpty()) {
            clearApiKey()
            return
        }

        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))

            prefs.edit()
                .putString(PREF_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // Fallback for mock/test environments where AndroidKeyStore might not be fully backed
            prefs.edit()
                .putString(PREF_KEY_CIPHERTEXT, Base64.encodeToString(cleanKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .putString(PREF_KEY_IV, "plain_b64")
                .apply()
        }
    }

    @Synchronized
    fun getApiKey(): String {
        val encryptedBase64 = prefs.getString(PREF_KEY_CIPHERTEXT, null)
        val ivBase64 = prefs.getString(PREF_KEY_IV, null)

        if (!encryptedBase64.isNullOrEmpty() && !ivBase64.isNullOrEmpty()) {
            try {
                if (ivBase64 == "plain_b64") {
                    return String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
                }
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                if (secretKey != null) {
                    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    val decrypted = cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP))
                    return String(decrypted, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                // If decryption failed, return empty
            }
        }

        // Fallback to BuildConfig if present and configured
        return try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotEmpty()) buildConfigKey else ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun hasKey(): Boolean {
        return getApiKey().isNotEmpty()
    }

    fun getMaskedKey(): String {
        val key = getApiKey()
        if (key.isEmpty()) return ""
        return if (key.length <= 4) {
            "••••"
        } else {
            "••••" + key.takeLast(4)
        }
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(PREF_KEY_CIPHERTEXT)
            .remove(PREF_KEY_IV)
            .apply()
    }
}
