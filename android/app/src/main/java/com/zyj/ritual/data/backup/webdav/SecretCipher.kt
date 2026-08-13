package com.zyj.ritual.data.backup.webdav

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 用手机自带的硬件密钥库（AndroidKeyStore）加解密一小段字符串。
 * 目前只用来存坚果云的应用密码。
 *
 * **为什么不是计划里写的 `EncryptedSharedPreferences`：**
 * 一是那要新加 `androidx.security:security-crypto` 依赖，本工作区的规矩是装新依赖先确认；
 * 二是 Google 自己已经把 `EncryptedSharedPreferences` 标成废弃，指向直接用 Keystore。
 * 用 Keystore 手写这几十行，不欠新依赖，也不会跟着一个废弃库走。
 *
 * 密钥本身出不了手机的安全芯片，App 只能请它加解密，读不到密钥内容。
 * 卸载 App 或恢复出厂设置，密钥一起没，密文就永远解不开了——这是对的，
 * 那种情况下本来也该让用户重新输一次密码。
 */
internal object SecretCipher {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "ritual_webdav_secret_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** 解不开时返回它，而不是抛异常或返回空串——调用方要能区分「没存过」和「存了但解不开」。 */
    sealed interface Decrypted {
        data class Ok(val value: String) : Decrypted
        /** 密钥没了（换机、恢复出厂、清了钥匙串）。密码要作废，让用户重输。 */
        data object KeyLost : Decrypted
    }

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // iv 和密文拼在一起存，解密时按固定长度切开
        Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(stored: String): Decrypted {
        if (stored.isEmpty()) return Decrypted.Ok("")
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            }
            Decrypted.Ok(String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8))
        }.getOrElse { Decrypted.KeyLost }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // 不要求解锁屏幕才能用：备份是后台动作，锁屏时也得能传
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKey()
    }
}
