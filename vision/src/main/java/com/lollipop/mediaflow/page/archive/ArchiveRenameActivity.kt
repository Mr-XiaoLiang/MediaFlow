package com.lollipop.mediaflow.page.archive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.common.tools.doAsync
import com.lollipop.common.tools.onUI
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.ArchiveBasket
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor

class ArchiveRenameActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ArchiveRenameActivity::class.java))
        }
    }

    private val refreshState = mutableStateOf(false)
    private val selectedBasketState = mutableStateOf<ArchiveBasket?>(null)
    private val selectedBasketNameState = mutableStateOf("")
    private val selectedAllState = mutableStateOf(false)
    private val basketFileList = SnapshotStateList<FileItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedBasketNameState.value = getString(R.string.archive_dropdown_empty)
        reloadBasketList()
    }

    private fun reloadBasketList() {
        ArchiveManager.init(this)
    }

    private fun notifyRefresh() {
        reloadBasketFileList()
    }

    private fun invokeRename() {
        if (refreshState.value) {
            return
        }
        refreshState.value = true
        doAsync {
            val resolver = contentResolver
            val fileList = mutableListOf<FileItem>()
            fileList.addAll(basketFileList)
            fileList.forEach { item ->
                if (item.isSelected) {
                    ArchiveManager.rename(resolver, item.file, item.targetFileName)
                }
            }
            // 改完了再刷新一次
            reloadBasketFileList()
        }
    }

    private fun onSelectedBasketChanged(basket: ArchiveBasket) {
        selectedBasketNameState.value = basket.name
        selectedBasketState.value = basket
        notifyRefresh()
    }

    private fun reloadBasketFileList() {
        refreshState.value = true
        doAsync {
            val basket = selectedBasketState.value
            if (basket == null) {
                refreshState.value = false
                basketFileList.clear()
                return@doAsync
            }
            val basketUri = basket.docUri
            val mediaRoot = MediaLoader.loadTreeSync(this@ArchiveRenameActivity, basketUri, "")
            val fileList = MediaLoader.expandFolderSync(mediaRoot.children)
            val itemList = mutableListOf<FileItem>()
            val nameMap = hashMapOf<String, Int>()
            fileList.forEach {
                itemList.add(buildFileItem(nameMap, it))
            }
            itemList.sortBy { !it.renameEnable }
            if (basketUri == selectedBasketState.value?.docUri) {
                // 如果 basket 未被修改，则直接刷新列表
                onUI {
                    resetBasketFileList(itemList)
                    refreshState.value = false
                }
            }
        }
    }

    private fun buildFileItem(nameMap: HashMap<String, Int>, file: MediaInfo.File): FileItem {
        val fileName = file.name
        val targetFileName = findFileName(
            nameMap,
            ArchiveManager.restoreOriginalFileName(fileName)
        )
        val hasChange = fileName != targetFileName
        return FileItem(
            file = file,
            uriString = file.uriString,
            currentFileName = fileName,
            targetFileName = targetFileName,
            renameEnable = hasChange,
            isSelected = hasChange
        )
    }

    private fun findFileName(nameMap: HashMap<String, Int>, currentName: String): String {
        val repeatCount = nameMap[currentName]
        if (repeatCount == null) {
            nameMap[currentName] = 1
            return currentName
        }

        val dotIndex = currentName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < currentName.length - 1

        val baseName = if (hasExtension) currentName.substring(0, dotIndex) else currentName
        val extension = if (hasExtension) currentName.substring(dotIndex) else ""

        // 1. 🔥 从当前已知的计数开始尝试
        var count = repeatCount
        var newName: String

        // 2. 🔥 核心防御：如果算出来的名字已经被别人占用了（比如用户原本就导入过带 _1 的文件）
        // 就自动往后顺延（count++），直到找到没人占用的空档
        while (true) {
            newName = "${baseName}_${count}${extension}"
            if (!nameMap.containsKey(newName)) {
                break
            }
            count++
        }

        // 3. 🔥 将最终成功的数字 + 1 反馈给母体 Key，下次直接从更新后的数字开始猜，维持高效率
        nameMap[currentName] = count + 1
        nameMap[newName] = 1 // 锁定新名字，防止被后面来的文件撞车

        return newName
    }

    private fun changeSelectedAll(checked: Boolean) {
        selectedAllState.value = checked
        val tempList = mutableListOf<FileItem>()
        basketFileList.forEach {
            tempList.add(it.optSelected(checked))
        }
        resetBasketFileList(tempList)
    }

    private fun resetBasketFileList(list: List<FileItem>) {
        Snapshot.withMutableSnapshot {
            basketFileList.clear()
            basketFileList.addAll(list)
        }
    }

    private fun changeSelected(file: FileItem, checked: Boolean) {
        if (file.isSelected != checked) {
            val index = basketFileList.indexOfFirst { it.uriString == file.uriString }
            if (index >= 0) {
                basketFileList[index] = file.optSelected(checked)
            }
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            TopBar()
            ContentList()
            BottomBar()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ColumnScope.ContentList() {
        val isRefreshing by remember { refreshState }
        val refreshState = rememberPullToRefreshState()
        val fileList = remember { basketFileList }
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F)
                .padding(4.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            isRefreshing = isRefreshing,
            state = refreshState,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                // 将 LoadingIndicator 传入以展示形变效果
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                )
            },
            onRefresh = {
                notifyRefresh()
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(fileList, key = { it.uriString }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(shape = MaterialTheme.shapes.large)
                            .background(color = currentThemeColor().preferencesGroup)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1F)
                        ) {
                            Text(
                                text = item.targetFileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = currentThemeColor().buttonText
                            )
                            HorizontalDivider(
                                modifier = Modifier.height(1.dp),
                                color = currentThemeColor().buttonMask
                            )
                            Text(
                                text = item.currentFileName,
                                fontSize = 12.sp,
                                color = currentThemeColor().buttonText.copy(alpha = 0.7F)
                            )
                        }
                        Checkbox(
                            enabled = item.renameEnable,
                            checked = item.isSelected,
                            onCheckedChange = {
                                changeSelected(item, it)
                            }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TopBar() {
        val basketList = remember { ArchiveManager.archiveBasketList }
        var basketDropdownMenuExpanded by remember { mutableStateOf(false) }
        val selectedBasketName by remember { selectedBasketNameState }
        val selectedAll by remember {
            derivedStateOf {
                var selected = true
                for (item in basketFileList) {
                    if (item.renameEnable && !item.isSelected) {
                        selected = false
                        break
                    }
                }
                selected
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = currentThemeColor().buttonBackground
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .padding(6.dp),
                    tint = currentThemeColor().buttonText,
                )
            }
            // 1. 外层容器，负责管理下拉菜单的展开状态和定位
            ExposedDropdownMenuBox(
                modifier = Modifier
                    .weight(1F)
                    .padding(horizontal = 16.dp),
                expanded = basketDropdownMenuExpanded,
                onExpandedChange = { basketDropdownMenuExpanded = it },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large
                        )
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1F),
                        text = selectedBasketName,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = basketDropdownMenuExpanded)
                }
                // 3. 弹出菜单：展示所有可选项
                ExposedDropdownMenu(
                    shape = MaterialTheme.shapes.large,
                    expanded = basketDropdownMenuExpanded,
                    onDismissRequest = { basketDropdownMenuExpanded = false },
                ) {
                    basketList.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.name) },
                            onClick = {
                                onSelectedBasketChanged(selectionOption)
                                basketDropdownMenuExpanded = false // 点击后关闭菜单
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            Checkbox(
                checked = selectedAll,
                onCheckedChange = {
                    changeSelectedAll(it)
                }
            )
        }
    }

    @Composable
    private fun BottomBar() {
        val isRenameEnable by remember {
            derivedStateOf {
                basketFileList.isNotEmpty()
            }
        }
        Button(
            enabled = isRenameEnable,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            onClick = {
                invokeRename()
            }
        ) {
            Text(
                text = "重命名",
                color = currentThemeColor().buttonText,
            )
        }
    }

    data class FileItem(
        val file: com.lollipop.mediaflow.data.MediaInfo.File,
        val uriString: String,
        val currentFileName: String,
        val targetFileName: String,
        val renameEnable: Boolean,
        val isSelected: Boolean
    ) {

        fun toggleSelected(): FileItem {
            if (!renameEnable) {
                return this
            }
            return FileItem(
                file = this.file,
                uriString = this.uriString,
                currentFileName = this.currentFileName,
                targetFileName = this.targetFileName,
                renameEnable = true,
                isSelected = !this.isSelected
            )
        }

        fun optSelected(selected: Boolean): FileItem {
            if (!renameEnable) {
                return this
            }
            if (selected == this.isSelected) {
                return this
            }
            return toggleSelected()
        }

    }

}