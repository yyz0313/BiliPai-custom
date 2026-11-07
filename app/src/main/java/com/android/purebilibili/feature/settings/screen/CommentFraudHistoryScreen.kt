package com.android.purebilibili.feature.settings.screen

import com.android.purebilibili.core.ui.AppContentColors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.android.purebilibili.core.ui.components.AppLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.database.entity.CommentFraudRecord
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.animation.EntranceGroup
import com.android.purebilibili.core.ui.animation.entrance
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.data.model.CommentFraudStatus
import com.android.purebilibili.data.repository.CommentFraudRepository
import com.android.purebilibili.feature.settings.ui.SettingsPageScaffold
import com.android.purebilibili.feature.settings.ui.settingsScrollContentPadding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFraudHistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by CommentFraudRepository.getAllRecordsFlow().collectAsState(initial = emptyList())

    var showClearDialog by remember { mutableStateOf(false) }
    var recordToDeleteOnBili by remember { mutableStateOf<CommentFraudRecord?>(null) }
    var recheckingRpid by remember { mutableStateOf<Long?>(null) }

    // 导入选择器
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (!content.isNullOrBlank()) {
                        val result = CommentFraudRepository.importFromJson(content)
                        result.onSuccess { count ->
                            Toast.makeText(context, "已导入 $count 条记录", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导出选择器 (SAF 另存为 .json)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = CommentFraudRepository.exportToJson()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    Toast.makeText(context, "已导出备份文件", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    EntranceGroup {
        SettingsPageScaffold(
            title = "评论检查记录",
            onBack = onBack,
            backContentDescription = "返回",
            bottomContentPadding = bottomPadding,
            scrollHost = com.android.purebilibili.feature.settings.SettingsPageScrollHost.External,
            actions = {
                AppIconButton(onClick = { importLauncher.launch("application/json") }) {
                    AppIcon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_file_download_24), contentDescription = "导入备份", tint = AppContentColors.primaryText())
                }
                AppIconButton(onClick = {
                    val fileName = "biliSendCheck_backup_${System.currentTimeMillis()}.json"
                    exportLauncher.launch(fileName)
                }) {
                    AppIcon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_file_upload_24), contentDescription = "导出备份", tint = AppContentColors.primaryText())
                }
                if (records.isNotEmpty()) {
                    AppIconButton(onClick = { showClearDialog = true }) {
                        AppIcon(com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_delete_sweep_24), contentDescription = "清空全部", tint = AppContentColors.error())
                    }
                }
            }
        ) {
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(top = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(
                            com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_shield_24),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = AppContentColors.secondaryText().copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AppText(
                            text = "暂无评论检查记录",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppContentColors.secondaryText()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AppText(
                            text = "在此查看已发评论是否正常显示",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = settingsScrollContentPadding(extraHorizontal = 16.dp, extraVertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records, key = { "${it.rpid}_${it.timestamp}_${it.hashCode()}" }) { record ->
                        Box(modifier = Modifier.entrance()) {
                            CommentFraudItemCard(
                                record = record,
                                isRechecking = recheckingRpid == record.rpid,
                                onRecheck = {
                                    scope.launch {
                                        recheckingRpid = record.rpid
                                        val res = CommentFraudRepository.recheckRecord(record)
                                        recheckingRpid = null
                                        res.onSuccess { status ->
                                            Toast.makeText(context, "检查完成：$status", Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            Toast.makeText(context, "检查失败：${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onCopyMessage = {
                                    copyText(context, record.message)
                                    Toast.makeText(context, "已复制评论内容", Toast.LENGTH_SHORT).show()
                                },
                                onCopyScheme = {
                                    val scheme = generateBiliUrlScheme(record)
                                    copyText(context, scheme)
                                    Toast.makeText(context, "已复制评论链接", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteLocal = {
                                    scope.launch { CommentFraudRepository.deleteLocalRecord(record.rpid) }
                                },
                                onDeleteBili = {
                                    recordToDeleteOnBili = record
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AppAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { AppText("清空评论检查记录？", fontWeight = FontWeight.Bold) },
            text = { AppText("删除本地保存的全部评论检查记录，无法恢复。B 站上的评论会保留。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch { CommentFraudRepository.clearAllRecords() }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                ) { AppText("确认清空") }
            },
            dismissButton = {
                AppTextButton(onClick = { showClearDialog = false }) { AppText("取消") }
            }
        )
    }

    recordToDeleteOnBili?.let { record ->
        AppAlertDialog(
            onDismissRequest = { recordToDeleteOnBili = null },
            title = { AppText("在 B 站永久删除此评论？", fontWeight = FontWeight.Bold, color = AppContentColors.error()) },
            text = { AppText("删除 B 站上的这条评论及本地记录，无法恢复。") },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch {
                            val res = CommentFraudRepository.deleteBiliComment(record)
                            res.onSuccess {
                                Toast.makeText(context, "评论已删除", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "删除失败：${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        recordToDeleteOnBili = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                ) { AppText("彻底删除") }
            },
            dismissButton = {
                AppTextButton(onClick = { recordToDeleteOnBili = null }) { AppText("取消") }
            }
        )
    }
}

/**
 * 终极手风琴式可展开全息卡片
 */
@Composable
private fun CommentFraudItemCard(
    record: CommentFraudRecord,
    isRechecking: Boolean,
    onRecheck: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyScheme: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteBili: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val postTimeStr = remember(record.post_time) {
        if (record.post_time > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.post_time))
        else "发评时间未记录"
    }

    val checkTimeStr = remember(record.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }

    val status = record.fraudStatus
    val rememberedSecondaryTextColor = AppContentColors.secondaryText()
    val rememberedErrorColor = AppContentColors.error()
    val rememberedSuccessColor = AppContentColors.success()
    val rememberedWarningColor = AppContentColors.warning()
    val (statusLabel, statusColor) = remember(status, rememberedSecondaryTextColor, rememberedErrorColor, rememberedSuccessColor, rememberedWarningColor) {
        when (status) {
            CommentFraudStatus.NORMAL -> "评论正常" to rememberedSuccessColor
            CommentFraudStatus.SHADOW_BANNED -> "仅自己可见" to rememberedErrorColor
            CommentFraudStatus.DELETED -> "已被秒删" to rememberedSecondaryTextColor
            CommentFraudStatus.INVISIBLE -> "软屏蔽" to rememberedWarningColor
            CommentFraudStatus.UNDER_REVIEW -> "审核中" to rememberedWarningColor
            CommentFraudStatus.UNKNOWN -> "状态未知" to rememberedSecondaryTextColor
        }
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // 👈 手风琴平滑展开折叠动画
        shape = AppCardShape.Semantic(ContainerLevel.Card),
        colors = AppCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // --- 顶部信息栏：发送时间 + 源ID/BV号 + 检测状态 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    AppText(
                        text = postTimeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText()
                    )
                    Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                    AppText(
                        text = record.source_id ?: "OID: ${record.oid}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AppContentColors.link()
                    )
                }

                // 状态标签
                Box(
                    modifier = Modifier
                        .clip(AppShapes.container(ContainerLevel.Tag))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    AppText(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 评论正文内容 (点击复制/展开) ---
            AppText(
                text = record.message,
                style = MaterialTheme.typography.bodyMedium,
                color = AppContentColors.primaryText(),
                modifier = Modifier.clickable { onCopyMessage() }
            )

            // --- 展开后的全息元数据区块 ---
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                AppHorizontalDivider()
                Spacer(modifier = Modifier.height(AppSpacingTokens.Medium))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailRow(label = "当前状态", value = statusLabel, valueColor = statusColor) {
                        AppTextButton(
                            onClick = onRecheck,
                            enabled = !isRechecking,
                            contentPadding = PaddingValues(horizontal = AppSpacingTokens.Small, vertical = 2.dp)
                        ) {
                            AppText(
                                if (isRechecking) "更新中…" else "重新检查",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    record.initial_status?.let {
                        val initLabel = CommentFraudRecord.parseStatus(it).name
                        DetailRow(label = "初始状态", value = initLabel)
                    }

                    DetailRow(label = "检查结果", value = record.fraudAssessment, valueColor = statusColor)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "Source ID", value = record.source_id ?: "无")
                    DetailRow(label = "OID", value = "${record.oid}")
                    DetailRow(label = "评论区类型", value = record.typeName)

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "UID", value = if (record.uid > 0L) "${record.uid}" else "未记录")
                    DetailRow(label = "RPID", value = "${record.rpid}")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "Root ID", value = if (record.root > 0L) "${record.root}" else "0（主评论）")
                    DetailRow(label = "Parent ID", value = if (record.parent > 0L) "${record.parent}" else "0")

                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(label = "发送日期", value = postTimeStr)
                    DetailRow(label = "检查日期", value = checkTimeStr)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 底部操作栏：复检、Scheme、移除、删评 + 【无字收起图标】 ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧快捷动作按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppTextButton(
                        onClick = onRecheck,
                        enabled = !isRechecking,
                    ) {
                        AppText(
                            if (isRechecking) "检查中…" else "重新检查",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    AppTextButton(onClick = onCopyScheme) {
                        AppText("复制链接", style = MaterialTheme.typography.labelMedium)
                    }

                    AppTextButton(onClick = onDeleteLocal) {
                        AppText("移除记录", style = MaterialTheme.typography.labelMedium)
                    }

                    AppTextButton(
                        onClick = onDeleteBili,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppContentColors.error())
                    ) {
                        AppText("删除评论", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 右侧：展开/收起无字图标按钮
                AppIconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    AppIcon(
                        imageVector = if (isExpanded) com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_up_24) else com.android.purebilibili.feature.settings.rememberMaterialSymbol(com.android.purebilibili.R.drawable.ms_keyboard_arrow_down_24),
                        contentDescription = if (isExpanded) "收起" else "展开详情",
                        tint = AppContentColors.secondaryText()
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = AppContentColors.primaryText(),
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            AppText(
                text = "$label：",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
                fontWeight = FontWeight.Medium
            )
            AppText(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                fontWeight = FontWeight.Normal
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
}

private fun generateBiliUrlScheme(record: CommentFraudRecord): String {
    val rootId = if (record.root > 0L) record.root else 0L
    return if (record.type == 1) {
        if (rootId > 0L) "bilibili://video/${record.oid}/?comment_root_id=${rootId}&comment_secondary_id=${record.rpid}"
        else "bilibili://video/${record.oid}/?comment_root_id=${record.rpid}"
    } else {
        if (rootId > 0L) "bilibili://comment/detail/${record.type}/${record.oid}/${rootId}?anchor=${record.rpid}"
        else "bilibili://comment/detail/${record.type}/${record.oid}/${record.rpid}"
    }
}
