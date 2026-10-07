package com.lollipop.mediaflow.data.common

/**
 * 目录树通用工具（跨来源，无类型依赖）。
 * 提供与具体来源模型无关的纯算法：拍平遍历、按媒体类型计数。
 * 各来源的树节点（如 local/MediaDirectoryTree）可调用本工具完成遍历/统计，
 * 避免在每个来源重复实现。
 */
object DirectoryTreeKit {

    /**
     * 拍平目录树：返回所有叶子节点（通过 [isLeaf] 判定），深度优先。
     */
    fun <T> flatten(
        root: T,
        getChildren: (T) -> List<T>,
        isLeaf: (T) -> Boolean
    ): List<T> {
        val result = mutableListOf<T>()
        val stack = ArrayDeque<T>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (isLeaf(node)) {
                result.add(node)
            } else {
                // 末尾入栈，保持深度优先顺序
                stack.addAll(getChildren(node).asReversed())
            }
        }
        return result
    }

    /**
     * 广度优先遍历整棵树（含非叶子节点），[onNode] 在每个节点调用一次。
     */
    fun <T> traverseBfs(
        root: T,
        getChildren: (T) -> List<T>,
        onNode: (T) -> Unit
    ) {
        val queue = ArrayDeque<T>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            onNode(node)
            queue.addAll(getChildren(node))
        }
    }

    /**
     * 统计目录下某类型的文件数量。
     * [isType] 返回 true 表示该叶子计入统计；[eachNode] 提供获取子节点与判定叶子的能力。
     */
    fun <T> countByType(
        root: T,
        getChildren: (T) -> List<T>,
        isLeaf: (T) -> Boolean,
        isType: (T) -> Boolean
    ): Int {
        return flatten(root, getChildren, isLeaf).count(isType)
    }

}
