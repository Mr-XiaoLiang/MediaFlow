package com.lollipop.mediaflow.page.sort

/**
 * 自然顺序比较器（纯逻辑，无状态）。
 *
 * 逐字符扫描两个字符串：
 * - 双方当前位置都是数字时，各自取出一整段数字，**按数值**比较（`2` < `12`）；
 * - 其余情况按**忽略大小写**的字符序比较。
 *
 * 这样 `2-aabb` 会排在 `12-ccdd` 之前，符合人类直觉，又不会像纯字典序那样
 * 把 `10` 排到 `2` 前面。
 */
object NaturalOrder {

    /**
     * 比较两个字符串的自然顺序，返回值语义与 [String.compareTo] 一致。
     */
    fun compare(left: String, right: String): Int {
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftChar = left[leftIndex]
            val rightChar = right[rightIndex]
            if (leftChar.isDigit() && rightChar.isDigit()) {
                val leftEnd = digitEnd(left, leftIndex)
                val rightEnd = digitEnd(right, rightIndex)
                val result = compareNumber(
                    left.substring(leftIndex, leftEnd),
                    right.substring(rightIndex, rightEnd)
                )
                if (result != 0) {
                    return result
                }
                leftIndex = leftEnd
                rightIndex = rightEnd
            } else {
                val result = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
                if (result != 0) {
                    return result
                }
                leftIndex++
                rightIndex++
            }
        }
        // 一方先耗尽：剩余长度短的在前面。
        return (left.length - leftIndex) - (right.length - rightIndex)
    }

    /** 从 [start] 开始，返回连续数字段的结束下标（不含）。 */
    private fun digitEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isDigit()) {
            index++
        }
        return index
    }

    /**
     * 按数值比较两段纯数字字符串：先去掉前导零比长度，再逐位比较。
     * 这样天然避免整数溢出（不依赖 `toInt`）。
     */
    private fun compareNumber(left: String, right: String): Int {
        val leftValue = left.trimStart('0')
        val rightValue = right.trimStart('0')
        if (leftValue.length != rightValue.length) {
            return leftValue.length - rightValue.length
        }
        return leftValue.compareTo(rightValue)
    }
}
