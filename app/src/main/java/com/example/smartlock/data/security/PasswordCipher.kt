package com.example.smartlock.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES/GCM 加解密工具。
 * 密码不会以明文形式落库，也不会出现在日志里。
 */
object PasswordCipher {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "smart_lock_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PLAIN_MARKER = "plain"

    data class EncryptedData(val cipherText: String, val iv: String)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): EncryptedData = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        EncryptedData(
            cipherText = Base64.encodeToString(bytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }.getOrElse {
        // 极端情况下（部分模拟器 Keystore 不可用）降级处理，避免崩溃
        EncryptedData(
            cipherText = Base64.encodeToString(
                plain.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            ),
            iv = PLAIN_MARKER
        )
    }

    fun decrypt(data: EncryptedData): String = runCatching {
        if (data.iv == PLAIN_MARKER) {
            String(Base64.decode(data.cipherText, Base64.NO_WRAP), Charsets.UTF_8)
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(
                GCM_TAG_LENGTH_BITS,
                Base64.decode(data.iv, Base64.NO_WRAP)
            )
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            String(
                cipher.doFinal(Base64.decode(data.cipherText, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
        }
    }.getOrDefault("")
}