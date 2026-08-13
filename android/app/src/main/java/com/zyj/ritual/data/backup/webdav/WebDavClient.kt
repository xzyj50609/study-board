package com.zyj.ritual.data.backup.webdav

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface WebDavResult<out T> {
    data class Success<T>(val data: T) : WebDavResult<T>
    data class Error(val message: String, val statusCode: Int? = null) : WebDavResult<Nothing>
}

object WebDavClient {

    private const val USER_AGENT = "RitualApp/1.0 (Android)"

    /** PUT 认为成功的状态码：新建 201、覆盖 200/204。 */
    private val SUCCESS_CODES = setOf(200, 201, 204)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    fun createAuthHeader(username: String, password: String): String {
        return Credentials.basic(username, password)
    }

    /**
     * 自动创建目标文件夹（若不存在）
     */
    private fun ensureFolderExists(config: WebDavConfig) {
        val folderUrl = config.normalizedServerUrl()
        if (folderUrl == config.baseServerUrl()) return
        try {
            val auth = createAuthHeader(config.username, config.password)
            val request = Request.Builder()
                .url(folderUrl)
                .header("Authorization", auth)
                .header("User-Agent", USER_AGENT)
                .method("MKCOL", null)
                .build()

            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // 忽略 MKCOL 异常（如果文件夹已存在，服务器通常返回 405/409，属于正常现象）
        }
    }

    /**
     * 测试 WebDAV 连通性与凭据（发 PROPFIND 请求）。
     */
    fun testConnection(config: WebDavConfig): WebDavResult<Unit> {
        if (!config.isValid) {
            return WebDavResult.Error("坚果云配置不完整：服务器地址、邮箱和应用密码均不能为空")
        }

        return try {
            val auth = createAuthHeader(config.username, config.password)
            // 先尝试连根目录
            val request = Request.Builder()
                .url(config.baseServerUrl())
                .header("Authorization", auth)
                .header("User-Agent", USER_AGENT)
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 207, 301, 302 -> WebDavResult.Success(Unit)
                    401, 403 -> WebDavResult.Error("坚果云登录失败：用户名或 16 位应用密码错误", response.code)
                    else -> {
                        val body = response.body?.string()?.take(100) ?: ""
                        WebDavResult.Error("连接坚果云失败 (HTTP ${response.code}${if (body.isNotBlank()) ": $body" else ""})", response.code)
                    }
                }
            }
        } catch (e: IOException) {
            WebDavResult.Error("网络连接失败：请检查网络连接是否正常 (${e.message})")
        } catch (e: Exception) {
            WebDavResult.Error("连接异常：${e.message}")
        }
    }

    /**
     * 上传备份 JSON (HTTP PUT)，**传完立刻原路读回来比对内容**。
     *
     * 为什么要多一次读：HTTP 200 只说明服务器收下了请求，不说明存进去的东西是对的。
     * 代理截断、服务端写盘失败、路径被别的东西占了，都会让你在界面上看到「备份成功」，
     * 而云端那个文件是空的或者是旧的——等到真需要恢复的那天才发现，那时已经晚了。
     * 这正是合并计划「哪些故障会伪装成正常」里的第 7 条。
     *
     * ⚠️ 回读必须读**实际传成功的那个地址**。上传有「主路径 404 就退回根路径」的兜底，
     * 如果回读时固定去读主路径，走了兜底的那次会 404，反而误报成失败。
     */
    fun uploadBackup(config: WebDavConfig, jsonContent: String): WebDavResult<Unit> {
        if (!config.isValid) {
            return WebDavResult.Error("坚果云配置不完整，无法上传")
        }

        // 自动尝试创建文件夹（防止因为远程文件夹未创建抛 404）
        ensureFolderExists(config)

        return try {
            // 1. 优先尝试包含 folderName 的完整路径
            val primaryUrl = config.fullRemoteUrl()
            val (code, detail) = putOnce(config, primaryUrl, jsonContent)
            if (code in SUCCESS_CODES) {
                return verifyUploaded(config, primaryUrl, jsonContent)
            }

            // 2. 如果返回 404 且设置了 folderName，自动回退尝试根路径 /dav/ritual-backup.json
            if (code == 404 && config.folderName.isNotBlank()) {
                val fallbackUrl = config.baseServerUrl() + config.remoteFileName
                val (fallbackCode, _) = putOnce(config, fallbackUrl, jsonContent)
                if (fallbackCode in SUCCESS_CODES) {
                    return verifyUploaded(config, fallbackUrl, jsonContent)
                }
            }

            when (code) {
                401, 403 -> WebDavResult.Error("坚果云登录失败：用户名或 16 位应用密码错误", code)
                404 -> WebDavResult.Error("坚果云返回 404：如果在网页端创建应用密码时填写了应用名称（例如「夜读」），请在弹窗中将文件夹名称填为对应的应用名称。", code)
                else -> WebDavResult.Error("上传备份失败 (HTTP $code${if (detail.isNotBlank()) ": $detail" else ""})", code)
            }
        } catch (e: IOException) {
            WebDavResult.Error("没网、没传上去：请检查网络连接 (${e.message})", null)
        } catch (e: Exception) {
            WebDavResult.Error("上传失败：${e.message}")
        }
    }

    /** 往一个具体地址 PUT 一次，返回 (状态码, 响应体前 150 字)。 */
    private fun putOnce(config: WebDavConfig, url: String, jsonContent: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", createAuthHeader(config.username, config.password))
            .header("User-Agent", USER_AGENT)
            .put(jsonContent.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to (response.body?.string()?.take(150) ?: "")
        }
    }

    /**
     * 把刚传上去的文件读回来，跟本地内容比一遍。
     * 对不上就当上传失败——宁可让用户看见「没传成功」再传一次，
     * 也不能让他以为有备份、真要恢复时才发现是空的。
     */
    private fun verifyUploaded(
        config: WebDavConfig,
        url: String,
        expected: String,
    ): WebDavResult<Unit> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", createAuthHeader(config.username, config.password))
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val remote = client.newCall(request).execute().use { response ->
            if (response.code != 200) {
                return WebDavResult.Error(
                    "传上去了，但立刻读回来验证时失败 (HTTP ${response.code})，请再传一次",
                    response.code,
                )
            }
            response.body?.string() ?: ""
        }
        // 末尾空白不算差异（有的服务端会补一个换行），其余一个字都不能差
        return if (remote.trim() == expected.trim()) {
            WebDavResult.Success(Unit)
        } else {
            WebDavResult.Error(
                "传上去了，但云端存的内容跟本地对不上"
                    + "（本地 ${expected.length} 字，云端 ${remote.length} 字），请再传一次"
            )
        }
    }

    /**
     * 下载备份 JSON (HTTP GET)。
     */
    fun downloadBackup(config: WebDavConfig): WebDavResult<String> {
        if (!config.isValid) {
            return WebDavResult.Error("坚果云配置不完整，无法恢复")
        }

        return try {
            val auth = createAuthHeader(config.username, config.password)
            
            // 1. 尝试主路径
            val primaryUrl = config.fullRemoteUrl()
            val request = Request.Builder()
                .url(primaryUrl)
                .header("Authorization", auth)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            var code: Int
            var content: String = ""
            client.newCall(request).execute().use { response ->
                code = response.code
                if (code == 200) {
                    content = response.body?.string() ?: ""
                    if (content.isNotBlank()) return WebDavResult.Success(content)
                }
            }

            // 2. 如果主路径 404，回退尝试根路径
            if (code == 404 && config.folderName.isNotBlank()) {
                val fallbackUrl = config.baseServerUrl() + config.remoteFileName
                val fallbackReq = Request.Builder()
                    .url(fallbackUrl)
                    .header("Authorization", auth)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                client.newCall(fallbackReq).execute().use { response ->
                    if (response.code == 200) {
                        content = response.body?.string() ?: ""
                        if (content.isNotBlank()) return WebDavResult.Success(content)
                    }
                }
            }

            when (code) {
                200 -> WebDavResult.Error("云端备份文件内容为空")
                404 -> WebDavResult.Error("云端暂无备份文件 (404)。请确认坚果云文件夹名称是否正确。", 404)
                401, 403 -> WebDavResult.Error("坚果云登录失败：用户名或 16 位应用密码错误", code)
                else -> WebDavResult.Error("从云端获取备份失败 (HTTP $code)", code)
            }
        } catch (e: IOException) {
            WebDavResult.Error("没网、无法恢复：请检查网络连接 (${e.message})", null)
        } catch (e: Exception) {
            WebDavResult.Error("下载失败：${e.message}")
        }
    }
}
