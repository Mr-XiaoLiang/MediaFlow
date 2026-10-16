package com.lollipop.mediaflow.page.sort

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.SourceLoader
import com.lollipop.mediaflow.data.local.LocalState
import com.lollipop.mediaflow.data.local.MediaDirectoryTree
import com.lollipop.mediaflow.data.local.MediaInfo
import com.lollipop.mediaflow.data.local.MediaVisibility
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.tools.PrivacyLock
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.DirectoryChooseDialog
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.mediaflow.ui.effect.FloatingButtonHeight
import com.lollipop.mediaflow.ui.effect.FloatingButtonIconPadding
import com.lollipop.mediaflow.ui.effect.FloatingButtonShape
import com.lollipop.mediaflow.ui.effect.FloatingButtonWidth
import com.lollipop.mediaflow.ui.effect.FloatingSurfaceElevation
import com.lollipop.mediaflow.ui.effect.effectSurfaceSolid
import com.lollipop.mediaflow.ui.image.MediaImage
import com.lollipop.mediaflow.ui.theme.currentThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 卡片封面尺寸（竖版，足够高，便于右侧纵向排布排序按钮）。 */
private val CoverWidth = 72.dp
private val CoverHeight = 96.dp

/**
 * 排序助手页面。
 *
 * 职责：让用户选定范围 → 自动按文件名顺序拍平 → 手动微调 → 确认后以
 * `MF{序号}-{原名}` 批量重命名，把主观顺序固化进文件名。
 *
 * 页面结构与批量重命名页（[com.lollipop.mediaflow.page.archive.ArchiveRenameActivity]）保持一致：
 * `Column(innerPadding) { TopBar + 内嵌卡片列表(weight 1F) + 底部按钮栏 }`。
 *
 * ## 与首页的关系
 * 数据来自同一份共享 [com.lollipop.mediaflow.data.source.MediaCatalog]（只读），
 * 但范围与顺序都是本页私有——不复用 `LocalState` / `MediaSource`，范围选择也
 * **不写回** 首页筛选，因此不会污染首页。仅在改名完成后调用一次 `refresh`
 * 让缓存与新名字同步（`refresh` 不修改 `scopeId`）。
 *
 * ## 未确认即离开
 * 页面的顺序调整只存在于内存，返回时不落盘，什么也不会发生。
 */
class SortAssistantActivity : BasicComposeActivity(), DirectoryChooseDialog.OnFolderClickListener {

    companion object {
        private const val ARGUMENTS_PAGE = "sort_assistant_page"
        private const val TAG_DIRECTORY = "SortAssistantDirectoryDialog"

        fun start(context: Context, page: HomePage) {
            val intent = Intent(context, SortAssistantActivity::class.java)
            intent.putExtra(ARGUMENTS_PAGE, page.key)
            context.startActivity(intent)
        }
    }

    /** 入口页面（决定可见性与媒体类型）。 */
    private val page: HomePage by lazy {
        HomePage.findPage(intent.getStringExtra(ARGUMENTS_PAGE) ?: "") ?: HomePage.PublicVideo
    }

    private val model: SortAssistantModel by lazy {
        SortAssistantModel(page.visibility, page.mediaType)
    }

    /**
     * 首页对应的业务状态，**仅用于改名后的 `refresh`**。
     * `refresh` 不写 `scopeId`，因此不会影响首页筛选。
     */
    private val localState: LocalState by lazy {
        LocalState.of(page.visibility, page.mediaType)
    }

    private val runningState = mutableStateOf(false)
    private val progressState = mutableStateOf(0 to 0)
    private val pendingPlanState = mutableStateOf<RenamePlan?>(null)
    private val rangeNameState = mutableStateOf("")

    private val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isPrivateBlocked()) {
            finish()
            return
        }
        rangeNameState.value = getString(R.string.all)
        // 分割关键词在进入组合前初始化，避免快照读取期写入状态
        SortSplitKeywords.init()
        // 从缓存 / 数据库填充共享数据单元（fill 只读，不改任何业务状态）
        lifecycleScope.launch {
            SourceLoader.Local.fill(this@SortAssistantActivity, localState)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPrivateBlocked()) {
            finish()
        }
    }

    /** 隐私页数据不得在锁定状态下外泄。 */
    private fun isPrivateBlocked(): Boolean {
        return page.visibility == MediaVisibility.Private && PrivacyLock.isLocked
    }

    override fun onFolderClick(folder: MediaDirectoryTree?) {
        // 只更新本助手的私有范围，绝不写回 LocalState
        model.setScopeId(folder?.id ?: "")
        rangeNameState.value = folder?.name ?: getString(R.string.all)
    }

    private fun openDirectoryChooser() {
        DirectoryChooseDialog.create(page.visibility, page.mediaType)
            .show(supportFragmentManager, TAG_DIRECTORY)
    }

    /** 底部确认：生成计划 → 无变化则提示 → 有冲突则先确认 → 否则直接执行。 */
    private fun requestConfirm() {
        if (runningState.value) {
            return
        }
        val plan = model.buildPlan()
        if (plan.pendingTasks.isEmpty()) {
            lifecycleScope.launch {
                snackbarHostState.showSnackbar(getString(R.string.sort_assistant_no_change))
            }
            return
        }
        if (plan.conflicts.isNotEmpty()) {
            pendingPlanState.value = plan
            return
        }
        executePlan(plan)
    }

    private fun executePlan(plan: RenamePlan) {
        if (runningState.value) {
            return
        }
        runningState.value = true
        progressState.value = 0 to plan.pendingTasks.size
        lifecycleScope.launch {
            val result = SortRenameRunner.run(contentResolver, plan) { done, total ->
                progressState.value = done to total
            }
            runningState.value = false
            when (result) {
                is RenameResult.Completed -> {
                    // 重扫，让内存缓存与数据库同步新名字（不阻塞失败提示）
                    withContext(Dispatchers.IO) {
                        SourceLoader.Local.refresh(this@SortAssistantActivity, localState)
                    }
                    snackbarHostState.showSnackbar(
                        getString(R.string.sort_assistant_done, result.renamed) +
                            "\n" + getString(R.string.sort_assistant_tip_name_asc)
                    )
                }

                is RenameResult.Interrupted -> {
                    snackbarHostState.showSnackbar(
                        getString(R.string.sort_assistant_interrupted, result.task.currentName)
                    )
                }
            }
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val projection by model.projection
        val splitKeywords = SortSplitKeywords.enabled.toList()
        LaunchedEffect(projection, splitKeywords) {
            model.rebuild(projection, splitKeywords)
        }
        // 隐私锁在后台被重新锁定时，立即结束本页，避免隐私内容外泄
        LaunchedEffect(Unit) {
            snapshotFlow { PrivacyLock.isLocked }.collect { locked ->
                if (locked && page.visibility == MediaVisibility.Private) {
                    finish()
                }
            }
        }
        // 一次性提示：告知可长按卡片拖动排序（仅首次进入时展示）
        LaunchedEffect(Unit) {
            if (!Preferences.isSortAssistantReorderTipShown.get()) {
                snackbarHostState.showSnackbar(getString(R.string.sort_assistant_reorder_tip))
                Preferences.isSortAssistantReorderTipShown.set(true)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            )
            if (runningState.value) {
                RunningOverlay()
            }
            val pendingPlan = pendingPlanState.value
            if (pendingPlan != null) {
                ConflictDialog(
                    conflictCount = pendingPlan.conflicts.size,
                    onConfirm = {
                        pendingPlanState.value = null
                        executePlan(pendingPlan)
                    },
                    onDismiss = {
                        pendingPlanState.value = null
                    }
                )
            }
        }
    }

    @Composable
    private fun TopBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier
                    .size(width = FloatingButtonWidth, height = FloatingButtonHeight)
                    .effectSurfaceSolid(
                        shape = FloatingButtonShape,
                        fallbackColor = currentThemeColor().buttonBackground,
                        shadowElevation = FloatingSurfaceElevation,
                    )
                    .clickable {
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .padding(FloatingButtonIconPadding),
                tint = currentThemeColor().buttonText,
            )
            // 范围选择：与批量重命名页一致的下拉样式（底衬 + 右侧箭头），点击弹文件夹选择
            Row(
                modifier = Modifier
                    .weight(1F)
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { openDirectoryChooser() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1F),
                    text = rangeNameState.value,
                    color = currentThemeColor().buttonText,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = currentThemeColor().buttonText,
                )
            }
            // 分割关键词：右上角过滤器入口。
            // 与返回按钮同款悬浮胶囊（同尺寸 / 同投影 / 同形状），
            // 从而范围选择器与左右两个按钮的间隙完全相同。
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = getString(R.string.sort_split_title),
                modifier = Modifier
                    .size(width = FloatingButtonWidth, height = FloatingButtonHeight)
                    .effectSurfaceSolid(
                        shape = FloatingButtonShape,
                        fallbackColor = currentThemeColor().buttonBackground,
                        shadowElevation = FloatingSurfaceElevation,
                    )
                    .clickable { SortSplitDialog.show(supportFragmentManager) }
                    .padding(FloatingButtonIconPadding),
                tint = currentThemeColor().buttonText,
            )
        }
    }

    /** 内嵌卡片列表：与批量重命名页一致（surfaceContainer 底衬 + 大圆角）。 */
    @Composable
    private fun ColumnScope.ContentList() {
        val workingList = model.workingList
        val density = LocalDensity.current
        val coverSize = remember(density) {
            with(density) {
                IntSize(CoverWidth.roundToPx(), CoverHeight.roundToPx())
            }
        }
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            model.moveItem(from.index, to.index)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F)
                .padding(4.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            if (workingList.isEmpty()) {
                Text(
                    text = getString(R.string.sort_assistant_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = currentThemeColor().buttonText.copy(alpha = 0.6F),
                )
                return@Box
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = workingList,
                    key = { _, file -> file.mediaId }
                ) { index, file ->
                    ReorderableItem(
                        state = reorderState,
                        key = file.mediaId
                    ) { isDragging ->
                        FileRow(
                            index = index,
                            file = file,
                            coverSize = coverSize,
                            isDragging = isDragging,
                            handleModifier = Modifier.longPressDraggableHandle(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BottomBar() {
        Button(
            enabled = !runningState.value && model.workingList.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            onClick = { requestConfirm() }
        ) {
            Text(
                text = getString(R.string.confirm),
                color = currentThemeColor().buttonText,
            )
        }
    }

    /**
     * 卡片：`[序号 | 封面 | 文件名] + [排序按钮（纵向）]`。
     *
     * 左侧「序号 + 封面 + 文件名」区域挂载长按拖拽手柄 [handleModifier]；
     * 右侧箭头只响应点击——两块区域互不重叠，避免误触。
     * 展示当前文件名（不展示改名结果）。
     */
    @Composable
    private fun FileRow(
        index: Int,
        file: MediaInfo.File,
        coverSize: IntSize,
        isDragging: Boolean,
        handleModifier: Modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .shadow(
                    elevation = if (isDragging) 8.dp else 0.dp,
                    shape = MaterialTheme.shapes.large,
                )
                .clip(MaterialTheme.shapes.large)
                .background(currentThemeColor().preferencesGroup)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 长按拾取区：序号 + 封面 + 文件名（不含右侧箭头）
            Row(
                modifier = Modifier
                    .weight(1F)
                    .then(handleModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(end = 8.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = currentThemeColor().buttonText,
                )
                MediaImage(
                    data = file.uri,
                    contentDescription = file.name,
                    modifier = Modifier
                        .size(width = CoverWidth, height = CoverHeight)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                    targetSize = coverSize,
                )
                Text(
                    modifier = Modifier
                        .weight(1F)
                        .padding(horizontal = 12.dp),
                    text = file.name,
                    fontSize = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = currentThemeColor().buttonText,
                )
            }
            Column(verticalArrangement = Arrangement.Center) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = index > 0) { model.moveUp(index) },
                    tint = currentThemeColor().buttonText,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(enabled = index < model.workingList.size - 1) {
                            model.moveDown(index)
                        },
                    tint = currentThemeColor().buttonText,
                )
            }
        }
    }

    @Composable
    private fun RunningOverlay() {
        val progress = progressState.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4F)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = getString(
                        R.string.sort_assistant_progress,
                        progress.first,
                        progress.second
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    color = Color.White,
                )
            }
        }
    }

    @Composable
    private fun ConflictDialog(
        conflictCount: Int,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(getString(R.string.sort_assistant_conflict_title)) },
            text = { Text(getString(R.string.sort_assistant_conflict_message, conflictCount)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(getString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(getString(R.string.cancel))
                }
            }
        )
    }
}
