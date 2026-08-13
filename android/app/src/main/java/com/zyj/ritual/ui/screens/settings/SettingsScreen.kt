package com.zyj.ritual.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyj.ritual.BuildConfig
import com.zyj.ritual.data.backup.BackupSerializer
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualRadius
import com.zyj.ritual.ui.theme.RitualSpace
import com.zyj.ritual.ui.theme.RitualTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 设置页。
 *
 * 全部 8 行：
 * - 基础节奏 ›
 * - 时区（只读）
 * - 调整起点与总篇数 ›
 * - 重新排期 ›
 * - 撤销第 N 篇的完成 ›
 * - 导出备份 ›
 * - 从备份恢复 ›
 * - 说明文字
 *
 * 阶段 8-9 的功能入口全在这里。
 */
@Composable
fun SettingsScreen(
    state: com.zyj.ritual.data.repository.TodayState,
    settingsViewModel: SettingsViewModel,
    settingsState: SettingsViewModel.SettingsState,
    onReEnterSetup: () -> Unit,
    onReschedule: () -> Unit = {},
    onUndoLastArticle: () -> Unit = {},
    onChangeDaysPerArticle: (Int) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }

    val defaultFileName = remember {
        val now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        "夜读备份-${now.format(fmt)}.json"
    }

    // 导出备份：SAF CreateDocument
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val data = settingsViewModel.exportData()
                        ?: return@withContext "导出失败：没有可导出的数据"
                    val json = BackupSerializer.encode(data)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: return@withContext "导出失败：无法打开文件"
                    "导出成功"
                } catch (e: Exception) {
                    "导出失败：${e.message}"
                }
            }
            feedback = result
        }
    }

    // 导入备份：SAF OpenDocument
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@withContext "导入失败：无法读取文件"
                    val data = BackupSerializer.decode(json)
                    val ok = settingsViewModel.importData(data)
                    if (ok) "导入成功，已回到当前计划" else "导入失败"
                } catch (e: Exception) {
                    "导入失败：${e.message}"
                }
            }
            feedback = result
        }
    }

    // 导入背词指南灯备份：SAF OpenDocument
    val importBeiciLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@withContext "导入失败：无法读取文件"
                    val (config, records) = com.zyj.ritual.data.backup.BeiciBackupImporter.parseBeiciJson(json)
                    val app = context.applicationContext as com.zyj.ritual.RitualApp
                    app.vocabRepository.importBeiciData(config, records)
                    "导入背词指南灯数据成功 (${records.size} 条记录)"
                } catch (e: Exception) {
                    "导入背词指南灯失败：${e.message}"
                }
            }
            feedback = result
        }
    }

    // 坚果云 WebDAV 凭据
    val webDavSecretStore = remember { com.zyj.ritual.data.backup.webdav.WebDavSecretStore(context) }
    val loadedWebDav = remember { webDavSecretStore.load() }
    var webDavConfig by remember {
        mutableStateOf(
            when (loadedWebDav) {
                is com.zyj.ritual.data.backup.webdav.WebDavSecretStore.Loaded.Ready ->
                    loadedWebDav.config
                is com.zyj.ritual.data.backup.webdav.WebDavSecretStore.Loaded.NeedsPasswordAgain ->
                    loadedWebDav.configWithoutPassword
            }
        )
    }
    // 密钥丢了（换机 / 恢复出厂 / 清了钥匙串）时，地址和邮箱还在但密码解不开。
    // 这时候界面必须说清是「密码丢了要重输」，而不是和「从来没配过」显示成同一句
    // ——那正是本项目反复栽过的「把不同状态压成同一个画面」。
    val webDavKeyLost = loadedWebDav is
        com.zyj.ritual.data.backup.webdav.WebDavSecretStore.Loaded.NeedsPasswordAgain
    var showWebDavDialog by remember { mutableStateOf(false) }

    val onWebDavUpload = {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!webDavConfig.isValid) {
                    return@withContext "请先配置坚果云账号与应用密码"
                }
                val app = context.applicationContext as com.zyj.ritual.RitualApp
                val articleExport = settingsViewModel.exportData()
                    ?: return@withContext "导出失败：没有可导出的数据"

                val vocabConfig = app.vocabRepository.getConfig()
                val vocabRecords = app.vocabRepository.recordsFlow().first()

                val fullExport = articleExport.copy(
                    vocabConfig = vocabConfig,
                    vocabRecords = vocabRecords,
                )
                val json = BackupSerializer.encode(fullExport)

                when (val res = com.zyj.ritual.data.backup.webdav.WebDavClient.uploadBackup(webDavConfig, json)) {
                    is com.zyj.ritual.data.backup.webdav.WebDavResult.Success -> "上传云备份成功！"
                    is com.zyj.ritual.data.backup.webdav.WebDavResult.Error -> res.message
                }
            }
            feedback = result
        }
    }

    val onWebDavRestore = {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!webDavConfig.isValid) {
                    return@withContext "请先配置坚果云账号与应用密码"
                }
                when (val res = com.zyj.ritual.data.backup.webdav.WebDavClient.downloadBackup(webDavConfig)) {
                    is com.zyj.ritual.data.backup.webdav.WebDavResult.Error -> res.message
                    is com.zyj.ritual.data.backup.webdav.WebDavResult.Success -> {
                        try {
                            val data = BackupSerializer.decode(res.data)
                            val ok = settingsViewModel.importData(data)
                            val app = context.applicationContext as com.zyj.ritual.RitualApp
                            val vCfg = data.vocabConfig
                            val vRecs = data.vocabRecords
                            if (vCfg != null && vRecs != null) {
                                app.vocabRepository.importBeiciData(vCfg, vRecs)
                            }
                            if (ok) "云端备份恢复成功！" else "恢复失败"
                        } catch (e: Exception) {
                            "恢复失败：${e.message}"
                        }
                    }
                }
            }
            feedback = result
        }
    }

    if (showWebDavDialog) {
        WebDavSetupDialog(
            initialConfig = webDavConfig,
            onDismiss = { showWebDavDialog = false },
            onSaveConfig = { newCfg ->
                webDavConfig = newCfg
                webDavSecretStore.saveWebDavConfig(newCfg)
                feedback = "已保存坚果云配置"
            }
        )
    }

    SettingsScreenContent(
        state = state,
        onReEnterSetup = onReEnterSetup,
        onReschedule = onReschedule,
        onUndoLastArticle = onUndoLastArticle,
        onChangeDaysPerArticle = onChangeDaysPerArticle,
        onExportClick = { exportLauncher.launch(defaultFileName) },
        onImportClick = { importLauncher.launch(arrayOf("application/json")) },
        onImportBeiciClick = { importBeiciLauncher.launch(arrayOf("application/json")) },
        webDavConfig = webDavConfig,
        webDavKeyLost = webDavKeyLost,
        onConfigureWebDavClick = { showWebDavDialog = true },
        onWebDavUploadClick = { onWebDavUpload() },
        onWebDavRestoreClick = { onWebDavRestore() },
        feedback = feedback,
    )
}

/**
 * 设置页的纯展示部分。
 *
 * 为什么要从 [SettingsScreen] 里拆出来：外层握着 ViewModel 和两个 SAF launcher，
 * 截图测试造不出来，于是整页在 2026-08-07 之前**一张图都没有**。
 * 拆出这一层之后，只要喂 TodayState 就能渲染，亮色下哪行字看不清能被截图抓住。
 *
 * 拆的是渲染，不是行为：外层签名一个字没改，导航和 instrumented 测试照旧。
 */
@Composable
internal fun SettingsScreenContent(
    state: com.zyj.ritual.data.repository.TodayState,
    onReEnterSetup: () -> Unit,
    onReschedule: () -> Unit,
    onUndoLastArticle: () -> Unit,
    onChangeDaysPerArticle: (Int) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onImportBeiciClick: () -> Unit = {},
    webDavConfig: com.zyj.ritual.data.backup.webdav.WebDavConfig = com.zyj.ritual.data.backup.webdav.WebDavConfig(),
    /** 密码密文还在但密钥没了（换机/恢复出厂），要提示重输，别和「从没配过」显示成一句话。 */
    webDavKeyLost: Boolean = false,
    onConfigureWebDavClick: () -> Unit = {},
    onWebDavUploadClick: () -> Unit = {},
    onWebDavRestoreClick: () -> Unit = {},
    feedback: String?,
) {
    var showRescheduleDialog by remember { mutableStateOf(false) }
    var showUndoArticleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RitualColors.bg)
            .padding(horizontal = RitualSpace.screenPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(RitualSpace.statusBarPad))
        Spacer(Modifier.height(RitualSpace.sectionGap))

        Text("设置", style = RitualTypography.headlineMedium)

        Spacer(Modifier.height(RitualSpace.sectionGap))

        // 基础节奏
        val plan = state.plan
        run {
            val rhythmText = when (plan.daysPerArticle) {
                1 -> "1 天/篇"
                2 -> "2 天/篇"
                3 -> "3 天/篇"
                else -> "${plan.daysPerArticle} 天/篇"
            }
            // 这一行只显示当前值，切换靠下面那三个按钮，所以不做成可点的。
            // 之前它带着一个空 onClick，点了没反应——那是在骗用户。
            SettingsRow(
                title = "基础节奏",
                value = rhythmText,
                onClick = null,
            )

            // 直接放三个按钮方便切换（原型里的三选一也放这里）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(RitualSpace.listGap),
            ) {
                listOf(1, 2, 3).forEach { days ->
                    val selected = plan.daysPerArticle == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(RitualRadius.button))
                            .background(
                                if (selected) RitualColors.accentInk.copy(alpha = 0.15f)
                                else RitualColors.surfaceLow
                            )
                            .clickable { onChangeDaysPerArticle(days) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$days 天/篇",
                            style = RitualTypography.bodyMedium.copy(
                                color = if (selected) RitualColors.accentInk else RitualColors.onBg,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }

        // 时区
        SettingsRow(
            title = "时区",
            value = "Asia/Shanghai",
            onClick = null,
        )

        SettingsDivider()

        // 调整起点
        SettingsRow(
            title = "调整起点与总篇数",
            value = null,
            onClick = onReEnterSetup,
        )

        // 重新排期
        SettingsRow(
            title = "重新排期",
            value = "以当前进度为起点",
            onClick = { showRescheduleDialog = true },
        )

        // 撤销整篇
        val completeArticles = state.progress.completeArticles
        val completedBefore = state.plan.completedBeforeStart
        val canUndoArticle = completeArticles > completedBefore
        SettingsRow(
            title = "撤销第 $completeArticles 篇的完成",
            value = null,
            onClick = if (canUndoArticle) {
                { showUndoArticleDialog = true }
            } else null,
            enabled = canUndoArticle,
        )

        if (showRescheduleDialog) {
            AlertDialog(
                containerColor = RitualColors.surface,
                onDismissRequest = { showRescheduleDialog = false },
                title = { Text("重新排期？", style = RitualTypography.titleMedium) },
                text = {
                    Text(
                        "计划会从今天重新开始，额度归零；已有历史记录和完成时间不会改变。",
                        style = RitualTypography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onReschedule()
                        showRescheduleDialog = false
                    }) { Text("确认重排") }
                },
                dismissButton = {
                    TextButton(onClick = { showRescheduleDialog = false }) { Text("取消") }
                },
            )
        }

        if (showUndoArticleDialog) {
            AlertDialog(
                containerColor = RitualColors.surface,
                onDismissRequest = { showUndoArticleDialog = false },
                title = { Text("撤销第 $completeArticles 篇？", style = RitualTypography.titleMedium) },
                text = {
                    Text(
                        "会删除这一篇的 6 项完成记录，但撤销事件仍会保留在历史记录中。",
                        style = RitualTypography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onUndoLastArticle()
                        showUndoArticleDialog = false
                    }) { Text("确认撤销") }
                },
                dismissButton = {
                    TextButton(onClick = { showUndoArticleDialog = false }) { Text("取消") }
                },
            )
        }

        SettingsDivider()

        // 导出备份
        SettingsRow(
            title = "导出备份",
            value = "保存为 JSON 文件",
            onClick = onExportClick,
        )

        // 从备份恢复
        SettingsRow(
            title = "从备份恢复",
            value = "会覆盖现有数据",
            onClick = onImportClick,
        )

        // 导入指南灯备份
        SettingsRow(
            title = "导入指南灯备份",
            value = "支持 v15 原生 .json 文件",
            onClick = onImportBeiciClick,
        )

        SettingsDivider()

        // 云备份（坚果云 WebDAV）
        val displayUser = if (webDavConfig.username.length > 18) webDavConfig.username.take(15) + "..." else webDavConfig.username
        val webDavStatusText = when {
            webDavConfig.isValid -> "已配置 ($displayUser)"
            webDavKeyLost -> "密码需要重新输入"
            else -> "未配置"
        }
        SettingsRow(
            title = "坚果云 WebDAV 账号",
            value = webDavStatusText,
            onClick = onConfigureWebDavClick,
        )

        SettingsRow(
            title = "备份到坚果云",
            value = "一键上传全看板数据",
            onClick = if (webDavConfig.isValid) onWebDavUploadClick else onConfigureWebDavClick,
            enabled = true,
        )

        SettingsRow(
            title = "从坚果云恢复",
            value = "从云端获取最新备份",
            onClick = if (webDavConfig.isValid) onWebDavRestoreClick else onConfigureWebDavClick,
            enabled = true,
        )

        if (feedback != null) {
            Text(
                feedback,
                style = RitualTypography.bodyMedium.copy(color = RitualColors.accentInk),
                modifier = Modifier.padding(vertical = RitualSpace.listGap),
            )
        }

        SettingsDivider()

        // 版本号。屏幕上写清楚，方便排查。
        Text(
            "夜读 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
                if (BuildConfig.DEBUG) " · debug" else "",
            style = RitualTypography.bodySmall.copy(color = RitualColors.onBgFaint),
            modifier = Modifier.padding(top = RitualSpace.listGap),
        )

        Spacer(Modifier.height(RitualSpace.navBarHeight + 32.dp))
    }
}

// ——— 子组件 ———

@Composable
private fun SettingsRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
) {
    val clickAction = onClick.takeIf { enabled }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(RitualRadius.card))
            .then(
                if (clickAction != null) Modifier.clickable(onClick = clickAction)
                else Modifier
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = RitualTypography.bodyLarge.copy(
                color = if (enabled) RitualColors.onBg else RitualColors.onBgFaint,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.width(16.dp))

        if (value != null) {
            Text(
                text = value,
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
        }

        if (clickAction != null) {
            Text(
                text = "›",
                style = RitualTypography.bodyMedium.copy(color = RitualColors.onBgFaint),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(RitualColors.divider),
    )
}
