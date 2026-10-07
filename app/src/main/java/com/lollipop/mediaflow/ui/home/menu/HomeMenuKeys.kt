package com.lollipop.mediaflow.ui.home.menu

/**
 * 首页操作气泡菜单的条目标识。
 * 菜单本身通过 [ComposePopupMenu.Builder] 声明条目，这里只统一 tag 常量的来源。
 */
object HomeMenuKeys {
    const val SOURCE_MANAGER = "SourceManager"
    const val DEBUG_MODE = "DebugMode"
    const val PREFERENCES = "Preferences"
    const val ARCHIVE = "Archive"
    const val VIDEO_DUPLICATE = "VideoDuplicate"
    const val ARCHIVE_RENAME = "ArchiveRename"
}
