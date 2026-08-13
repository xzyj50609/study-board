package com.zyj.ritual.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.data.backup.webdav.WebDavClient
import com.zyj.ritual.data.backup.webdav.WebDavConfig
import com.zyj.ritual.data.backup.webdav.WebDavResult
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WebDavSetupDialog(
    initialConfig: WebDavConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (WebDavConfig) -> Unit,
) {
    var serverUrl by remember { mutableStateOf(initialConfig.serverUrl) }
    var folderName by remember { mutableStateOf(initialConfig.folderName) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }

    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        containerColor = RitualColors.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "配置坚果云 WebDAV",
                style = RitualTypography.titleMedium,
                color = RitualColors.onBg,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "说明：密码请输入坚果云网页端生成的 16 位应用密码（账号信息 → 安全选项 → 第三方应用管理）。",
                    fontSize = 12.sp,
                    color = RitualColors.onBgMuted
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("服务器地址", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("文件夹名称（如在坚果云生成密码时填了应用名）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("例如：夜读") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("注册邮箱", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("16 位应用密码", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (testResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testResult!!,
                        fontSize = 12.sp,
                        color = if (testResult!!.startsWith("连接成功")) RitualColors.accentInk else RitualColors.warnText
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = !isTesting && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        isTesting = true
                        testResult = "连接测试中…"
                        val cfg = WebDavConfig(
                            serverUrl = serverUrl,
                            folderName = folderName,
                            username = username,
                            password = password
                        )
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                WebDavClient.testConnection(cfg)
                            }
                            isTesting = false
                            testResult = when (res) {
                                is WebDavResult.Success -> "连接成功！凭据有效。"
                                is WebDavResult.Error -> res.message
                            }
                        }
                    }
                ) {
                    Text(if (isTesting) "测试中…" else "测试连接")
                }

                TextButton(
                    onClick = {
                        val cfg = WebDavConfig(
                            serverUrl = serverUrl,
                            folderName = folderName,
                            username = username,
                            password = password
                        )
                        onSaveConfig(cfg)
                        onDismiss()
                    }
                ) {
                    Text("保存配置")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
