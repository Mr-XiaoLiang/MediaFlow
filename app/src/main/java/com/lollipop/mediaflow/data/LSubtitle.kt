package com.lollipop.mediaflow.data

import android.net.Uri

/**
 * 字幕（跨来源通用抽象，来源轴）。
 *
 * 与 [LMedia] 同属"面向业务定义"的抽象：只描述播放 / 展示所需的素材信息，
 * **不承载来源专属字段**（如 SAF 的 tree / docId）。各来源在其专属字幕类型里实现本接口。
 *
 * ## 约定
 * - 字幕是媒体信息的一部分，但**不保证存在**：[LMedia.subtitleList] 为空即表示无字幕；
 * - [mimeType] 无法识别时为 null，调用方应据此跳过该条。
 */
interface LSubtitle {

    /** 字幕文件地址。 */
    val uri: Uri

    /** 展示名（含后缀的文件名）。 */
    val name: String

    /** 语言标识（从文件名解析，无法识别时为空串）。 */
    val language: String

    /** MIME 类型；无法识别时为 null。 */
    val mimeType: String?
}
