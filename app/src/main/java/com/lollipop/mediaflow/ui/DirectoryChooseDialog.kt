package com.lollipop.mediaflow.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaDirectoryTree
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaType
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.doAsync
import com.lollipop.mediaflow.tools.fetchCallback
import com.lollipop.mediaflow.tools.onUI
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme
import com.lollipop.mediaflow.ui.theme.currentThemeColor

class DirectoryChooseDialog : DialogFragment() {

    companion object {

        private const val ARGUMENTS_GALLERY_VISIBILITY = "gallery_visibility"
        private const val ARGUMENTS_GALLERY_MEDIA_TYPE = "gallery_media_type"

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
            Folder(".", null, 0, 0, 0)
        }

    }

    private var rootFolder = mutableStateOf(EMPTY_FOLDER)

    private var onFolderClickListener: OnFolderClickListener? = null

    private val log = registerLog()

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        updateWindowSize(resources.configuration)
        window.setBackgroundDrawable(Color.Transparent.toArgb().toDrawable())
        val windowParams = window.attributes
        windowParams.dimAmount = 0.5f // 保持背景变暗，但去掉白色框
        window.attributes = windowParams
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateWindowSize(newConfig)
    }

    private fun updateWindowSize(config: Configuration) {
        val window = dialog?.window ?: return
        val dm = resources.displayMetrics

        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // 横屏：宽度占一半，高度占满（或根据需要调整）
            window.setLayout((dm.widthPixels * 0.5).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.END)
        } else {
            // 竖屏：高度占一半，宽度占满
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.5).toInt())
            window.setGravity(Gravity.TOP)
        }
    }

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
        } else {
            MediaStore.loadGallery(context, visibility, mediaType).loadAll { gallery, bool ->
                updateFolderList(gallery.directoryTree, mediaType)
                log.i("updateDirectoryTree success")
            }
        }
    }

    private fun onFolderClick(folder: Folder) {
        onFolderClickListener?.onFolderClick(folder.current)
        dismiss()
    }

    private fun updateFolderList(treeList: List<MediaDirectoryTree>, mediaType: MediaType) {
        log.i("updateFolderList, tree count: ${treeList.size}")
        doAsync {
            val folderList = treeList.map { it.toFolder(mediaType, 1) }
            var allCountCount = 0
            var allFolderCount = 0
            folderList.forEach {
                allCountCount += it.contentCount
                allFolderCount += it.folderCount
            }
            val folder = Folder(
                name = getString(R.string.all),
                current = null,
                contentCount = allCountCount,
                folderCount = allFolderCount,
                level = 0,
            )
            folder.subNodes.also {
                it.clear()
                it.addAll(folderList)
            }
            onUI {
                rootFolder.value = folder
                log.i("updateFolderList success, count: ${folder.contentCount}")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(inflater.context).apply {
            setContent {
                DialogContent()
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
    private fun DialogContent() {
        val root by remember { rootFolder }
        val displayList by remember {
            derivedStateOf {
                val res = mutableListOf<Folder>()
                flatten(root, res) // 执行 flatten 时，所有被访问到的 expand 状态都被“录音”了
                res
            }
        }
        MediaFlowTheme {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent
            ) { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .clip(MaterialTheme.shapes.large)
                        .background(color = currentThemeColor().windowBackground),
                    contentAlignment = Alignment.Center
                ) {
                    val textColor = currentThemeColor().buttonText
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        items(
                            displayList,
                            key = { info -> info.current?.id ?: info.name }
                        ) { info ->
                            val leftPadding = 12.dp * info.level
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
                                    color = textColor
                                )
                                val hasCount = info.contentCount > 0
                                val hasFolder = info.folderCount > 0
                                if (hasCount || hasFolder) {
                                    HorizontalDivider(modifier = Modifier.weight(1F).padding(horizontal = 12.dp))
                                }
                                if (hasCount) {
                                    Text(
                                        fontFamily = FontFamily.Monospace,
                                        text = "${info.contentCount}",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(4.dp),
                                        color = textColor
                                    )
                                }
                                if (hasFolder) {
                                    if (info.expand) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = "Close",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable(onClick = { info.expand = false })
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Expand",
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
        }
    }

    private fun MediaDirectoryTree.toFolder(mediaType: MediaType, level: Int): Folder {
        val tree = this
        return Folder(
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