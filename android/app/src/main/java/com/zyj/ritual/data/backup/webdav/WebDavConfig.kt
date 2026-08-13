package com.zyj.ritual.data.backup.webdav

data class WebDavConfig(
    val serverUrl: String = "https://dav.jianguoyun.com/dav/",
    val folderName: String = "夜读",
    val username: String = "",
    val password: String = "",
    val remoteFileName: String = "ritual-backup.json",
) {
    val isValid: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun normalizedServerUrl(): String {
        var url = serverUrl.trim()
        if (!url.endsWith("/")) {
            url += "/"
        }
        val folder = folderName.trim().trim('/')
        if (folder.isNotEmpty()) {
            url += "$folder/"
        }
        return url
    }

    fun baseServerUrl(): String {
        var url = serverUrl.trim()
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    fun fullRemoteUrl(): String = normalizedServerUrl() + remoteFileName
}
