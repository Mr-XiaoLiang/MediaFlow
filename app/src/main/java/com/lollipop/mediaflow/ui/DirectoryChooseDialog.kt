package com.lollipop.mediaflow.ui

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.ui.page.fetchCallback
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaSource
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaType
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.ui.dialog.ComposeHalfDialog
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import com.lollipop.mediaflow.ui.theme.selectionAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectoryChooseDialog : ComposeHalfDialog() {

    companion object {

        private const val ARGUMENTS_GALLERY_VISIBILITY = "gallery_visibility"
        private const val ARGUMENTS_GALLERY_MEDIA_TYPE = "gallery_media_type"

        private const val EMPTY_FOLDER_ID = ""

        fun create(visibility: MediaVisibility, mediaType: MediaType): DirectoryChooseDialog {
            return DirectoryChooseDialog().apply {
                val newArgs = arguments ?: Bundle()
                newArgs.putString(ARGUMENTS_GALLERY_VISIBILITY, visibility.key)
                newArgs.putString(ARGUMENTS_GALLERY_MEDIA_TYPE, mediaType.dataKey)
                arguments = newArgs
            }
        }

        private fun findVisibility(arg: Bundle?): MediaVisibility? {
            return arg?.getString(ARGUMENTS_GALLERY_VISIBILITY)?.let {
                MediaVisibility.findByKey(it)
            }
        }

        private fun findMediaType(arg: Bundle?): MediaType? {
            return arg?.getString(ARGUMENTS_GALLERY_MEDIA_TYPE)?.let {
                MediaType.findByKey(it)
            }
        }

        private val EMPTY_FOLDER by lazy {
            Folder(
                folderId = EMPTY_FOLDER_ID,
                name = ".",
                current = null,
                contentCount = 0,
                folderCount = 0,
                level = 0
            )
        }

    }

    private val rootFolder = mutableStateOf(EMPTY_FOLDER)
    private val selectedFolderId = mutableStateOf("")

    private var onFolderClickListener: OnFolderClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        updateDirectoryTree(context)
        onFolderClickListener = fetchCallback(context)
    }

    private fun updateDirectoryTree(context: Context) {
        log.i("updateDirectoryTree")
        val visibility = findVisibility(arguments)
        val mediaType = findMediaType(arguments)
        if (visibility == null || mediaType == null) {
            rootFolder.value = EMPTY_FOLDER
            selectedFolderId.value = EMPTY_FOLDER.folderId
            return
        }
        val state = LocalState.of(visibility, mediaType)
        val source = MediaSource.of(visibility, mediaType)
        val appContext = context.applicationContext
        lifecycleScope.launch {
            // fill 只从缓存投影（快），目录树随之更新
            SourceLoader.Local.fill(appContext, state)
            updateFolderList(source.directoryTree.toList(), mediaType)
            selectedFolderId.value = state.scopeId.value
            log.i("updateDirectoryTree success")
        }
    }

    private fun onFolderClick(folder: Folder) {
        onFolderClickListener?.onFolderClick(folder.current)
        dismiss()
    }

    private fun updateFolderList(treeList: List<MediaDirectoryTree>, mediaType: MediaType) {
        log.i("updateFolderList, tree count: ${treeList.size}")
        lifecycleScope.launch(Dispatchers.IO) {
            val folderList = treeList.map { it.toFolder(mediaType, 1) }
            var allCountCount = 0
            var allFolderCount = 0
            folderList.forEach {
                allCountCount += it.contentCount
                allFolderCount += it.folderCount
            }
            val folder = Folder(
                folderId = EMPTY_FOLDER_ID,
                name = getString(R.string.all),
                current = null,
                contentCount = allCountCount,
                folderCount = allFolderCount,
                level = 0,
            )
            folder.subNodes.also {
                Snapshot.withMutableSnapshot {
                    it.clear()
                    it.addAll(folderList)
                }
            }
            withContext(Dispatchers.Main) {
                rootFolder.value = folder
                log.i("updateFolderList success, count: ${folder.contentCount}")
            }
        }
    }

    private fun flatten(folder: Folder, result: MutableList<Folder>) {
        result.add(folder)
        // 关键点：这一行代码触发了对 node.expand 这个 State 的“读取”
        if (folder.expand) {
            folder.subNodes.forEach { flatten(it, result) }
        }
    }

    fun Modifier.treeGuidelines(
        level: Int,
        indentWidth: Dp = 12.dp, // 每一级的缩进宽度
        guideLineWidth: Dp = 1.dp,
        color: Color = Color.LightGray.copy(alpha = 0.5f)
    ): Modifier = this.drawBehind {
        val strokeWidth = guideLineWidth.toPx()
        val stepPx = indentWidth.toPx()

        // 从第 1 层开始画（第 0 层通常是根目录，不需要线）
        for (i in 1..level) {
            val x = i * stepPx - (stepPx / 2) // 线条居中在缩进区域
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }
    }

    @Composable
    override fun DialogContent() {
        val root by remember { rootFolder }
        val selectedId by remember { selectedFolderId }
        val displayList by remember {
            derivedStateOf {
                val res = mutableListOf<Folder>()
                flatten(root, res) // 执行 flatten 时，所有被访问到的 expand 状态都被“录音”了
                res
            }
        }

        val themeColor = currentThemeColor()
        val darkTheme = isSystemInDarkTheme()
        val defaultTextColor = themeColor.buttonText
        // 选中色由主题主色叠加得到：深色主题提亮、浅色主题加深，
        // 既保留主题的青蓝色相，又保证在两种主题的弹层底色上都清晰可辨
        val selectedTextColor = selectionAccentColor(
            primary = MaterialTheme.colorScheme.primary,
            darkTheme = darkTheme
        )
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶栏固定，不随目录列表滚动
            FolderToolBar(
                onCollapseAll = { collapseAll() },
                onExpandAll = { expandAll() }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F)
            ) {
                items(
                    displayList,
                    key = { info -> info.current?.id ?: info.name }
                ) { info ->
                    val leftPadding = 12.dp * info.level
                    // 整行统一取色：名称文本与右侧折叠箭头必须同色。
                    // 否则箭头会退回默认的 LocalContentColor（深色主题下为黑色），
                    // 在本弹层的深色背景上无法辨认。
                    val rowTextColor = if (selectedId == info.folderId) {
                        selectedTextColor
                    } else {
                        defaultTextColor
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onFolderClick(info)
                            }
                            .padding(horizontal = 32.dp)
                            .treeGuidelines(info.level),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(
                            modifier = Modifier.width(leftPadding)
                        )
                        Text(
                            fontFamily = FontFamily.Monospace,
                            text = info.name,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            color = rowTextColor
                        )
                        val hasCount = info.contentCount > 0
                        val hasFolder = info.folderCount > 0
                        if (hasCount || hasFolder) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .weight(1F)
                                    .padding(horizontal = 12.dp)
                            )
                        }
                        if (hasCount) {
                            Text(
                                fontFamily = FontFamily.Monospace,
                                text = "${info.contentCount}",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(4.dp),
                                color = defaultTextColor
                            )
                        }
                        if (hasFolder) {
                            if (info.expand) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Close",
                                    tint = rowTextColor,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable(onClick = { info.expand = false })
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = rowTextColor,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable(onClick = { info.expand = true })
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    /**
     * 顶部固定工具栏：批量收起 / 展开整个目录树。
     * 两个按钮恒为可点击状态——目录树为空时点击不产生任何副作用，
     * 因此这里刻意不读取任何 expand 状态，避免单个节点的展开/收起连带重组顶栏。
     */
    @Composable
    private fun FolderToolBar(
        onCollapseAll: () -> Unit,
        onExpandAll: () -> Unit
    ) {
        val iconTint = currentThemeColor().buttonText
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapseAll) {
                Icon(
                    imageVector = Icons.Rounded.UnfoldLess,
                    contentDescription = stringResource(R.string.folder_collapse_all),
                    tint = iconTint
                )
            }
            IconButton(onClick = onExpandAll) {
                Icon(
                    imageVector = Icons.Rounded.UnfoldMore,
                    contentDescription = stringResource(R.string.folder_expand_all),
                    tint = iconTint
                )
            }
        }
    }

    /**
     * 全部折叠：只收起根节点「全部」以下的所有子节点，根节点自身始终保持展开。
     * 这样折叠后依然能看到所有一级目录，而不是整屏只剩下一行。
     */
    private fun collapseAll() {
        val root = rootFolder.value
        root.subNodes.forEach { child ->
            child.setExpandRecursively(false)
        }
    }

    /**
     * 全部展开：递归展开根节点「全部」以下的所有子节点。
     */
    private fun expandAll() {
        val root = rootFolder.value
        root.subNodes.forEach { child ->
            child.setExpandRecursively(true)
        }
    }

    /**
     * 递归设置自身及全部后代的展开状态。
     * 该方法由主线程的事件回调调用，Compose 会把同一次点击内的所有状态写入合并到同一帧重组，
     * 因此无需借助 Snapshot.withMutableSnapshot 做跨线程的原子迁移。
     */
    private fun Folder.setExpandRecursively(expand: Boolean) {
        this.expand = expand
        subNodes.forEach { child ->
            child.setExpandRecursively(expand)
        }
    }

    private fun MediaDirectoryTree.toFolder(mediaType: MediaType, level: Int): Folder {
        val tree = this
        return Folder(
            folderId = tree.id,
            name = tree.name,
            current = tree,
            level = level,
            folderCount = tree.folderCount,
            contentCount = when (mediaType) {
                MediaType.Image -> {
                    tree.imageCount
                }

                MediaType.Video -> {
                    tree.videoCount
                }
            }
        ).apply {
            for (child in tree.children) {
                subNodes.add(child.toFolder(mediaType, level + 1))
            }
        }
    }

    private class Folder(
        val folderId: String,
        val name: String,
        val current: MediaDirectoryTree?,
        val contentCount: Int,
        val folderCount: Int,
        val level: Int
    ) {
        var expand by mutableStateOf(true)
        val subNodes = mutableStateListOf<Folder>()
    }

    fun interface OnFolderClickListener {
        fun onFolderClick(folder: MediaDirectoryTree?)
    }

}
