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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
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
import java.util.LinkedList

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

    }

    private val directoryTree = SnapshotStateList<Folder>()

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
            directoryTree.clear()
        } else {
            MediaStore.loadGallery(context, visibility, mediaType).load { gallery, bool ->
                updateFolderList(gallery.directoryTree, mediaType)
                log.i("updateDirectoryTree success, count: ${directoryTree.size}")
            }
        }
    }

    private fun onFolderClick(folder: Folder) {
        onFolderClickListener?.onFolderClick(folder.current)
        dismiss()
    }

    private fun getFileCount(tree: MediaDirectoryTree, mediaType: MediaType): Int {
        return when (mediaType) {
            MediaType.Video -> tree.videoCount
            MediaType.Image -> tree.imageCount
        }
    }

    private fun updateFolderList(treeList: List<MediaDirectoryTree>, mediaType: MediaType) {
        log.i("updateFolderList, tree count: ${treeList.size}")
        doAsync {
            val tempList = ArrayList<Folder>()
            val pendingList = LinkedList<Folder>()
            val fileCount = treeList.sumOf { getFileCount(it, mediaType) }
            val rootFolder = Folder(
                level = 0,
                name = ".",
                current = null,
                isLast = true,
                prefix = "",
                table = "",
                count = fileCount.toString()
            )
            tempList.add(rootFolder)
            val lastIndex = treeList.lastIndex
            for (i in treeList.indices.reversed()) {
                val tree = treeList[i]
                val isLast = i == lastIndex
                pendingList.addFirst(
                    Folder(
                        level = 1,
                        name = tree.name,
                        current = tree,
                        isLast = isLast,
                        prefix = "",
                        table = if (isLast) {
                            "└"
                        } else {
                            "├"
                        },
                        count = getFileCount(tree, mediaType).toString()
                    )
                )
            }
            while (pendingList.isNotEmpty()) {
                val folder = pendingList.removeFirst()
                tempList.add(folder)
                val children = folder.current?.children ?: continue
                val maxIndex = children.lastIndex
                for (i in children.indices.reversed()) {
                    val child = children[i]
                    // 倒序的添加到头部，就可以保证取出来的顺序是正确的
                    val nameBuilder = StringBuilder()
                    nameBuilder.append(folder.prefix)
                    if (folder.isLast) {
                        nameBuilder.append(" ")
                    } else {
                        nameBuilder.append("│")
                    }
                    val prefix = nameBuilder.toString()
                    val isChildLast = i == maxIndex
                    if (isChildLast) {
                        nameBuilder.append("└")
                    } else {
                        nameBuilder.append("├")
                    }
                    nameBuilder.append(" ")
                    pendingList.addFirst(
                        Folder(
                            level = folder.level + 1,
                            name = child.name,
                            current = child,
                            isLast = isChildLast,
                            table = nameBuilder.toString(),
                            prefix = prefix,
                            count = getFileCount(child, mediaType).toString()
                        )
                    )
                }
            }
            onUI {
                directoryTree.clear()
                directoryTree.addAll(tempList)
                log.i("updateFolderList success, count: ${directoryTree.size}")
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

    @Composable
    private fun DialogContent() {
        val tree = remember { directoryTree }
        val colorA = MaterialTheme.colorScheme.surface
        val colorB = MaterialTheme.colorScheme.surface.copy(alpha = 0.5F)
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        itemsIndexed(
                            tree,
                            key = { _, info -> info.current?.id ?: info.name }
                        ) { index, info ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onFolderClick(info)
                                    }
                                    .background(
                                        color = if (index % 2 == 0) {
                                            colorA
                                        } else {
                                            colorB
                                        }
                                    )
                                    .padding(horizontal = 32.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    fontFamily = FontFamily.Monospace,
                                    text = info.table,
                                    fontSize = 28.sp,
                                )
                                Text(
                                    text = info.name,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .weight(1F)
                                        .padding(vertical = 6.dp)
                                )
                                Text(
                                    text = info.count,
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .padding(vertical = 6.dp)
                                )
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


    private class Folder(
        val level: Int,
        val name: String,
        val isLast: Boolean,
        val prefix: String,
        val table: String,
        val current: MediaDirectoryTree?,
        val count: String
    )

    fun interface OnFolderClickListener {
        fun onFolderClick(folder: MediaDirectoryTree?)
    }

}