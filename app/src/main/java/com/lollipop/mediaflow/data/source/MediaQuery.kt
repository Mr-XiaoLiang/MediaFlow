package com.lollipop.mediaflow.data.source

import com.lollipop.mediaflow.data.common.MediaSort
import com.lollipop.mediaflow.data.local.MediaType

/**
 * 视图查询参数（视图轴）。
 *
 * 同一份 [MediaCatalog] 的多种"切法"：视频 / 图片只是 [mediaType] 不同，
 * 排序与范围由各视图独立持有——这就是"同源数据模拟出独立视图"的关键参数。
 */
data class MediaQuery(
    val mediaType: MediaType,
    val sort: MediaSort,
    val scopeId: String
)
