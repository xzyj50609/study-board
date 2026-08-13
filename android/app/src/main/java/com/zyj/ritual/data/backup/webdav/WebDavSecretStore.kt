package com.zyj.ritual.data.backup.webdav

import android.content.Context
import android.content.SharedPreferences

/**
 * 坚果云 WebDAV 配置存储。
 *
 * 地址、用户名、文件夹名这些是普通配置，明文存没关系；
 * **应用密码单独走 [SecretCipher] 加密**，密钥在手机的安全芯片里，App 自己也读不到。
 *
 * 2026-08-07 之前这里是整个 `SharedPreferences` 明文存的，包括密码——
 * 而合并计划 Phase 4 白纸黑字要求加密存。当时代码和注释写成了「私有存储……严禁硬编码」，
 * 读起来像做到了，实际只是换了个更弱的方案。现在补上。
 */
class WebDavSecretStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("webdav_secrets", Context.MODE_PRIVATE)

    init {
        migrateLegacyPlaintextPassword()
    }

    /**
     * 读配置。
     *
     * @return [Loaded.Ready] 正常；[Loaded.NeedsPasswordAgain] 表示密文还在但密钥没了
     *         （换了手机、恢复出厂、清了钥匙串），必须让用户重新输一次，
     *         **不能默默当成空密码去连**——那样界面上只会看到一个莫名其妙的 401。
     */
    fun load(): Loaded {
        val base = WebDavConfig(
            serverUrl = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL,
            folderName = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER,
            username = prefs.getString(KEY_USER, "") ?: "",
            password = "",
            remoteFileName = prefs.getString(KEY_FILE, DEFAULT_FILE) ?: DEFAULT_FILE,
        )
        return when (val d = SecretCipher.decrypt(prefs.getString(KEY_PASSWORD_ENC, "") ?: "")) {
            is SecretCipher.Decrypted.Ok -> Loaded.Ready(base.copy(password = d.value))
            SecretCipher.Decrypted.KeyLost -> Loaded.NeedsPasswordAgain(base)
        }
    }

    /** 老调用方还在用这个。密钥丢了时退化成空密码，连接会失败并提示重输。 */
    fun getWebDavConfig(): WebDavConfig = when (val r = load()) {
        is Loaded.Ready -> r.config
        is Loaded.NeedsPasswordAgain -> r.configWithoutPassword
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        val editor = prefs.edit()
            .putString(KEY_URL, config.serverUrl)
            .putString(KEY_FOLDER, config.folderName)
            .putString(KEY_USER, config.username)
            .putString(KEY_FILE, config.remoteFileName)
            .remove(KEY_PASSWORD_LEGACY)
        val encrypted = SecretCipher.encrypt(config.password)
        // 加密失败就宁可不存，也绝不退回明文
        if (encrypted != null) editor.putString(KEY_PASSWORD_ENC, encrypted)
        else editor.remove(KEY_PASSWORD_ENC)
        editor.apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /**
     * 把 0.5.2 及以前明文存的密码就地加密，然后删掉明文那条。
     * 只会发生一次；用户不用重新输密码。
     */
    private fun migrateLegacyPlaintextPassword() {
        val legacy = prefs.getString(KEY_PASSWORD_LEGACY, null) ?: return
        val editor = prefs.edit().remove(KEY_PASSWORD_LEGACY)
        if (legacy.isNotEmpty()) {
            SecretCipher.encrypt(legacy)?.let { editor.putString(KEY_PASSWORD_ENC, it) }
        }
        editor.apply()
    }

    sealed interface Loaded {
        /** 密码解得开，可以直接用。 */
        data class Ready(val config: WebDavConfig) : Loaded

        /** 密文还在但密钥没了，得让用户重输密码。其余配置照旧可用。 */
        data class NeedsPasswordAgain(val configWithoutPassword: WebDavConfig) : Loaded
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_FOLDER = "folder_name"
        const val KEY_USER = "username"
        const val KEY_FILE = "remote_file_name"
        const val KEY_PASSWORD_ENC = "password_enc"

        /** 0.5.2 及以前明文存密码用的键。只在迁移时读一次，之后永远删掉。 */
        const val KEY_PASSWORD_LEGACY = "password"

        const val DEFAULT_URL = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_FOLDER = "夜读"
        const val DEFAULT_FILE = "ritual-backup.json"
    }
}
