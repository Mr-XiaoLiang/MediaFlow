package com.lollipop.mediaflow.page.sort

/**
 * 文件名排序分析（纯逻辑，无状态）。
 *
 * 排序规约（对齐产品定义）：
 * 1. 先去掉扩展名，只分析主名；
 * 2. 按**已启用关键词的顺序**逐个检查，取**第一个命中**的关键词作为分隔词，切分主名；
 * 3. 取首段作为主排序键，首段相同则逐段比较，段内采用 [NaturalOrder] 自然顺序。
 *
 * 「顺序匹配、首次命中即停止」是让不同命名规则的文件公平参与同一套排序的关键：
 * 已启用 `[-, _]` 时，`10-aaa` 命中 `-`、`2_bbd` 命中 `_`，两者都能取到首段 `10` / `2`。
 *
 * 注意：排序 **不剥离** 软件前缀。已排序的 `MF001-原名` 首段即 `MF001`，
 * 会按序号自然排开，从而在反复编辑时稳定保持上次顺序。
 */
object FileNameOrder {

    /** 去掉扩展名后的主名。 */
    fun baseName(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < fileName.length - 1) {
            fileName.substring(0, dotIndex)
        } else {
            fileName
        }
    }

    /**
     * 按 [keywords]（有序）取第一个命中的关键词，用它切分主名（忽略空段）。
     * 无命中时整名作为唯一分段。
     */
    fun splitSegments(fileName: String, keywords: List<String>): List<String> {
        val base = baseName(fileName)
        val keyword = keywords.firstOrNull { it.isNotEmpty() && base.contains(it) }
        if (keyword == null) {
            return listOf(base)
        }
        return base.split(keyword).filter { it.isNotEmpty() }
    }

    /**
     * 按分段自然顺序比较两个文件名。
     *
     * 逐段比较：段内用 [NaturalOrder]；全部相同则段数多者靠后。
     */
    fun compare(leftName: String, rightName: String, keywords: List<String>): Int {
        val left = splitSegments(leftName, keywords)
        val right = splitSegments(rightName, keywords)
        val size = minOf(left.size, right.size)
        for (index in 0 until size) {
            val result = NaturalOrder.compare(left[index], right[index])
            if (result != 0) {
                return result
            }
        }
        return left.size - right.size
    }
}
