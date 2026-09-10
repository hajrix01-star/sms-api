package com.appenza.smsapi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object RelayStore {
    const val URL = "url"
    const val ENABLED = "enabled"
    const val SENDERS = "senders"
    private const val ENCRYPTED_TOKEN = "encrypted_token"
    private const val KEY_ALIAS = "bank_relay_api_token"

    fun p(context: Context) = context.getSharedPreferences("bank_relay", Context.MODE_PRIVATE)

    fun senders(context: Context): List<String> {
        val values = JSONArray(p(context).getString(SENDERS, "[]") ?: "[]")
        return (0 until values.length()).map { values.getString(it) }
    }

    fun saveSenders(context: Context, values: List<String>) {
        p(context).edit().putString(SENDERS, JSONArray(values).toString()).apply()
    }

    fun saveToken(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        p(context).edit().putString(ENCRYPTED_TOKEN, "$iv:$encrypted").apply()
    }

    fun token(context: Context): String? = runCatching {
        val parts = p(context).getString(ENCRYPTED_TOKEN, null)?.split(":") ?: return null
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), javax.crypto.spec.GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrNull()

    fun isOtp(value: String): Boolean {
        val text = value.lowercase()
        return listOf("otp", "one-time", "verification code", "رمز التحقق", "رمز التاكيد", "كود التحقق")
            .any(text::contains)
    }

    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }
}
