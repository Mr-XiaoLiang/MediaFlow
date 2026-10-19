package com.lollipop.mediaflow.page.sort

/**
 * 排序助手的重命名规则（纯逻辑，无 UI / 无 IO 依赖）。
 *
 * 命名规约：`MF{定宽序号}-{原始名}`，例如 `MF001-holiday.mp4`。
 * - `MF` 为软件标识，用于把「软件写入的前缀」与用户原始命名区分开；
 * - 序号定宽（至少 3 位），保证字典序与数值序一致；
 * - 分隔符固定为 `-`，与「用于决定顺序的候选分隔符」解耦（拆分不改写规则）。
 *
 * 「软件前缀族」既包含本助手写入的 `MF{数字}-`，也包含归档搬运写入的
 * `MF{17位时间戳}-`。按既定方案，写入前统一剥离，最终只保留一层前缀
 * （即归档时间戳信息会被排序前缀替换）。
 */
object SortRename {

    /** 软件前缀标识。 */
    private const val PREFIX = "MF"

    /** 前缀与原始名之间的分隔符。 */
    private const val SEPARATOR = "-"

    /** 最小序号宽度：不足 3 位时左侧补零。 */
    private const val MIN_WIDTH = 3

    /** 软件前缀族：`MF` + 至少一位数字 + `-`（同时覆盖归档时间戳前缀）。 */
    private val SOFTWARE_PREFIX = Regex("^MF\\d+-")

    /** 顺延后缀：`_数字`。 */
    private val SUFFIX_PATTERN = Regex("_(\\d+)$")

    /** 依据待编号条目数量计算序号宽度（至少 [MIN_WIDTH] 位）。 */
    fun widthOf(count: Int): Int {
        val digits = count.toString().length
        return if (digits < MIN_WIDTH) MIN_WIDTH else digits
    }

    /** 生成目标文件名：`MF{定宽序号}-{原始名}`。 */
    fun generate(sequence: Int, width: Int, originalName: String): String {
        val number = sequence.toString().padStart(width, '0')
        val builder = StringBuilder()
        builder.append(PREFIX)
        builder.append(number)
        builder.append(SEPARATOR)
        builder.append(originalName)
        return builder.toString()
    }

    /**
     * 剥离全部软件前缀，得到用户的原始文件名。
     *
     * 循环剥离以兼容历史叠加（如曾多次被写入前缀），保证幂等。
     */
    fun stripSoftwarePrefix(fileName: String): String {
        var result = fileName
        while (true) {
            val match = SOFTWARE_PREFIX.find(result)
            if (match == null || match.range.first != 0) {
                return result
            }
            result = result.substring(match.range.last + 1)
        }
    }

    /**
     * 追加一级顺延后缀：`a.mp4` → `a_1.mp4`；`a_1.mp4` → `a_2.mp4`。
     * 后缀落在扩展名之前。
     */
    fun nextAvailableName(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < fileName.length - 1
        val baseName = if (hasExtension) fileName.substring(0, dotIndex) else fileName
        val extension = if (hasExtension) fileName.substring(dotIndex) else ""
        val match = SUFFIX_PATTERN.find(baseName)
        return if (match == null) {
            "${baseName}_1$extension"
        } else {
            val next = match.groupValues[1].toInt() + 1
            "${baseName.substring(0, match.range.first)}_$next$extension"
        }
    }
}
