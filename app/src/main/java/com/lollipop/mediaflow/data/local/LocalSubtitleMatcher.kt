package com.lollipop.mediaflow.data.local

/**
 * Local 字幕关联：将扫描到的字幕按文件名 baseName 关联到对应视频文件。
 * 从原 MediaLoader.loadDirectorySync 抽取，作为 Local 专属职责。
 */
object LocalSubtitleMatcher {

    /**
     * @param subtitleList 目录下解析出的字幕列表
     * @param videoMap 以视频文件名（不含后缀）为 key 的视频文件映射
     */
    fun match(
        subtitleList: List<SubtitleFile>,
        videoMap: Map<String, MediaInfo.File>
    ) {
        subtitleList.forEach { subtitle ->
            val baseName = subtitle.baseName
            videoMap[baseName]?.let { file ->
                subtitle.videoId = file.docId
                file.subtitleList.add(subtitle)
            }
        }
    }

}
