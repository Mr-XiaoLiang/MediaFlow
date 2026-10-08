package com.lollipop.mediaflow.data.source

import com.lollipop.mediaflow.data.LMedia
import com.lollipop.mediaflow.data.local.MediaDirectoryTree

/**
 * 某来源、某可见性下的「原始数据」快照。
 *
 * 它是**未按 mediaType / sort / scope 筛选**的完整集合，因此可以被同一可见性下的
 * 所有视图（视频 / 图片）共享——这正是"同源模拟独立数据"的物理基础。
 *
 * 不可变：一旦构建即不再修改，更新以整体替换（见 [MediaCatalog.publish]）。
 */
class MediaSnapshot(
    val items: List<LMedia>,
    val trees: List<MediaDirectoryTree>,
    val version: Long
) {

    companion object {
        /** 初始空快照：version = -1，保证首次 publish 一定生效。 */
        val Empty: MediaSnapshot = MediaSnapshot(
            items = emptyList(),
            trees = emptyList(),
            version = -1L
        )
    }
}
