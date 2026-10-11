package com.lollipop.mediaflow.data

import com.lollipop.mediaflow.data.common.FileSizeFormatter

/**
 * [LMedia] 的**展示派生**扩展。
 *
 * 这些值由既有信息（[LMedia.uri] / [LMedia.name] / [LMedia.size]）推出，**不是媒体的固有信息**，
 * 因此不做成 [LMedia] 的成员——保持接口面向业务、不臃肿。
 *
 * 说明：来源具体类型（如 `MediaInfo.File`）若有同名成员，成员优先，扩展对其无影响。
 */

/** 地址字符串（列表 key、日志常用）。 */
val LMedia.uriString: String
    get() = uri.toString()

/** 文件后缀（不含点）。 */
val LMedia.suffix: String
    get() = name.substringAfterLast('.', "")

/** 人类可读的体积文本。 */
val LMedia.sizeFormat: String
    get() = FileSizeFormatter.format(size)
