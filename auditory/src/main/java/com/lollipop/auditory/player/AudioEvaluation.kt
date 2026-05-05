package com.lollipop.auditory.player

/**
 * | 勋章名称 | 视觉类型 | 触发逻辑 (评定标准) | 排版建议 / 含义 |
 * |---|---|---|---|
 * | MASTER | 阴刻 (实心) | 采样率 $\ge 192\text{kHz}$ 或 DSD/DXD 格式 | 顶级音质，象征录音室原母带。 |
 * | HI-RES | 阴刻 (实心) | 采样率 $> 44.1\text{kHz}$ 且位深 $\ge 24\text{-bit}$ | 高解析度音频，发烧友最爱晒的标签。 |
 * | STUDIO | 阴刻 (实心) | 采样率 $= 96\text{kHz}$ 且为无损格式 | 模拟专业录音室的监听环境。 |
 * | LOSSLESS | 阳刻 (线框) | 格式为 FLAC/WAV/APE/ALAC | 代表音乐信号无损还原。 |
 * | VINYL | 阳刻 (线框) | 模拟黑胶抓轨或采样率为 $176.4\text{k}$ | 具有黑胶唱片般的模拟质感。 |
 * | 24-BIT | 阳刻 (线框) | 位深 (Bit Depth) $= 24\text{-bit}$ | 强调动态范围，适合展示专业解码。 |
 * | DSD / DXD | 阴刻 (实心) | 编码格式为 DSF 或 DFF | 音频格式的“劳斯莱斯”，极具收藏价值。 |
 * | 5.1 CH | 阳刻 (线框) | 声道数 (Channels) $> 2$ | 环绕声标识，适合家庭影院爱好者。 |
 * | 1411K | 小字 (无框) | 比特率 (Bitrate) $\ge 1411\text{kbps}$ | CD 级别的传输速率指标。 |
 * | ARCHIVE | 阳刻 (细线) | 所有本地导入且带完整标签的歌曲 | 强调该歌曲已入库，属于个人博物馆。 |
 *
 * | 声道数 | 常见名称 | 推荐勋章文案 | 建议类型 | 视觉隐喻 |
 * |---|---|---|---|---|
 * | 1 ch | Mono | [ MONO ] | 阳刻 (线框) | 复古感。多见于 50-60 年代爵士或古典，代表历史韵味。 |
 * | 2 ch | Stereo | [ STEREO ] | 小字 (无框) | 标准感。最常见的立体声，无需过度强调。 |
 * | 4 ch | Quad | [ QUAD ] | 阳刻 (线框) | 稀有感。70 年代流行的四声道环绕，很有收藏价值。 |
 * | 6 ch | 5.1 Surround | [ 5.1 CH ] | 阴刻 (实心) | 沉浸感。最主流的环绕声，代表大片级/演奏会级别的音源。 |
 * | 8 ch | 7.1 Surround | [ 7.1 CH ] | 阴刻 (实心) | 顶配感。极高规格的蓝光转录音源。 |
 * | 12 ch+ | Atmos / Auro | [ SPATIAL ] | 阴刻 (实心) | 空间感。全景声轨道，属于现代音频技术的顶峰。 |
 *
 * | 比特率 (Bitrate) | 推薦文案 | 視覺類型 | 意義 |
 * |---|---|---|---|
 * | > 3000 kbps | ULTRA / [碼率值]K | 陰刻 (實心) | 極致細節。多見於 192kHz 或 DSD 轉錄，代表訊息量爆表。 |
 * | 1411 kbps | 1411K / CD | 陽刻 (線框) | 標準無損。CD 抓軌的標誌性速率，是高品質的門檻。 |
 * | 320 kbps | 320K / HQ | 小字 (無框) | 高品質有損。MP3 的天花板，雖有損但聽感紮實。 |
 * | < 128 kbps | LQ | 淡化小字 | 低碼率。通常不主動展示，除非是為了體現復古感。 |
 *
 */
object AudioEvaluation {

    val MASTER by lazy {
        AudioFlag.Excellent("MASTER")
    }

    val HI_RES by lazy {
        AudioFlag.Excellent("Hi-Res")
    }

    val STUDIO by lazy {
        AudioFlag.Excellent("Studio")
    }

    val LOSSLESS by lazy {
        AudioFlag.Good("Lossless")
    }

    val VINYL by lazy {
        AudioFlag.Good("VINYL")
    }

    val BIT_DEPTH_32 by lazy {
        AudioFlag.Excellent("32bit")
    }

    val BIT_DEPTH_24 by lazy {
        AudioFlag.Good("24bit")
    }

    val BIT_DEPTH_16 by lazy {
        AudioFlag.Ordinary("16bit")
    }

    val DSD_DXD by lazy {
        AudioFlag.Excellent("DSD / DXD")
    }

    val MONO by lazy {
        AudioFlag.Good("MONO")
    }

    val STEREO by lazy {
        AudioFlag.Ordinary("STEREO")
    }

    val QUAD by lazy {
        AudioFlag.Good("QUAD")
    }

    val FIVE_ONE_CH by lazy {
        AudioFlag.Excellent("5.1 CH")
    }

    val SEVEN_ONE_CH by lazy {
        AudioFlag.Excellent("7.1 CH")
    }

    val SPATIAL by lazy {
        AudioFlag.Excellent("SPATIAL")
    }

    val ARCHIVE by lazy {
        AudioFlag.Excellent("Archive")
    }

    val LQ by lazy {
        AudioFlag.Ordinary("LQ")
    }

    val HQ by lazy {
        AudioFlag.Ordinary("HQ")
    }

    val CD by lazy {
        AudioFlag.Good("CD")
    }

    val ULTRA by lazy {
        AudioFlag.Excellent("ULTRA")
    }

}