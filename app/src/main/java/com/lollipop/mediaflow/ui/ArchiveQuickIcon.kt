package com.lollipop.mediaflow.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.lollipop.mediaflow.data.local.ArchiveQuick

/**
 * 快速归档类目 → Material 图标。
 *
 * 图标只服务于 Compose 渲染，所以映射放在 UI 层：`ArchiveQuick` 保持纯数据，
 * 避免把 Compose 类型下沉到 data 层。
 */
val ArchiveQuick.archiveIcon: ImageVector
    get() {
        return when (this) {
            ArchiveQuick.Favorite -> Icons.Filled.Favorite
            ArchiveQuick.Special -> Icons.Filled.Star
            ArchiveQuick.ThumpUp -> Icons.Filled.ThumbUp
            ArchiveQuick.Other -> Icons.Filled.Archive
        }
    }
