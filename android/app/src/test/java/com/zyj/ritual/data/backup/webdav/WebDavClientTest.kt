package com.zyj.ritual.data.backup.webdav

import org.junit.Assert.*
import org.junit.Test

class WebDavClientTest {

    @Test
    fun `webdav config validation and url normalization`() {
        val config = WebDavConfig(
            serverUrl = "https://dav.jianguoyun.com/dav",
            folderName = "夜读",
            username = "user@example.com",
            password = "sixteencharpass",
            remoteFileName = "ritual-backup.json"
        )

        assertTrue(config.isValid)
        assertEquals("https://dav.jianguoyun.com/dav/", config.baseServerUrl())
        assertEquals("https://dav.jianguoyun.com/dav/夜读/", config.normalizedServerUrl())
        assertEquals("https://dav.jianguoyun.com/dav/夜读/ritual-backup.json", config.fullRemoteUrl())
    }

    @Test
    fun `webdav config with empty folderName`() {
        val config = WebDavConfig(
            serverUrl = "https://dav.jianguoyun.com/dav",
            folderName = "",
            username = "user@example.com",
            password = "sixteencharpass",
            remoteFileName = "ritual-backup.json"
        )

        assertEquals("https://dav.jianguoyun.com/dav/", config.normalizedServerUrl())
        assertEquals("https://dav.jianguoyun.com/dav/ritual-backup.json", config.fullRemoteUrl())
    }

    @Test
    fun `basic auth header format`() {
        val authHeader = WebDavClient.createAuthHeader("user@example.com", "secretpass")
        assertTrue(authHeader.startsWith("Basic "))
    }
}
