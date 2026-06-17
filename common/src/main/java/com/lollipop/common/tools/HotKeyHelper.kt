package com.lollipop.common.tools

class HotKeyHelper {

    private val keyCodeSet = HashSet<Int>()
    private val touchDownKeySet = HashSet<Int>()
    private val keyObserverList = ArrayList<KeyObserverHolder>()
    private val activeObserverList = ArrayList<KeyObserverHolder>()


    /**
     * 按键按下
     */
    fun onKeyDown(keyCode: Int): Boolean {
        // 如果这个Key不被注册，那么直接返回
        if (!keyCodeSet.contains(keyCode)) {
            return false
        }
        // 记录被按下
        touchDownKeySet.add(keyCode)
        // 转换出被按下的KeyCode数组，并排序
        val activeArray = touchDownKeySet.toTypedArray().sortedArray()
        // 检查所有的监听器，如果匹配上了，那么就触发事件，使用的是原始监听器
        for (holder in keyObserverList) {
            // 如果匹配了，就表示可以触发事件了
            if (holder.isSame(codeArray = activeArray)) {
                // 记录被激活的监听器
                activeObserverList.add(holder)
                // 触发按下
                holder.onKeyDown()
            }
        }
        return true
    }

    /**
     * 按键松开
     */
    fun onKeyUp(keyCode: Int): Boolean {
        // 如果这个Key不被注册，那么直接返回
        if (!keyCodeSet.contains(keyCode)) {
            return false
        }
        // 移除按下的事件
        touchDownKeySet.remove(keyCode)
        // 转换出被按下的KeyCode数组，并排序
        val activeArray = touchDownKeySet.toTypedArray().sortedArray()
        // 基于已经被激活的集合来迭代
        val iterator = activeObserverList.iterator()
        while (iterator.hasNext()) {
            val holder = iterator.next()
            // 如果不匹配了，表示需要放弃事件了，然后移除
            if (!holder.isSame(codeArray = activeArray)) {
                // 移除本条监听器
                iterator.remove()
                // 触发抬起手指
                holder.onKeyUp()
            }
        }
        return true
    }

    /**
     * 注册监听事件
     */
    fun register(keyCodeList: List<Int>, callback: KeyObserver) {
        // 如果注册的Key为空的，那么就拒绝
        if (keyCodeList.isEmpty()) {
            return
        }
        // 注册Key到全局集合，利用Set来去重复
        keyCodeSet.addAll(keyCodeList)
        // 添加监听器到列表
        keyObserverList.add(KeyObserverHolder(keyCodeList, callback))
    }

    class KeyObserverHolder(
        keyCodeList: List<Int>,
        val callback: KeyObserver
    ) {

        /**
         * 排序后的keyCode列表
         */
        val sortedCodeList = keyCodeList.toTypedArray().sortedArray()

        fun onKeyDown() {
            callback.onKeyDown()
        }

        fun onKeyUp() {
            callback.onKeyUp()
        }

        fun isSame(codeArray: Array<Int>): Boolean {
            // 如果codeArray为空，那么直接返回false
            if (codeArray.isEmpty()) {
                return false
            }
            // 如果长度不符合，那么直接返回false
            if (codeArray.size != sortedCodeList.size) {
                return false
            }
            // 如果长度为1，那么直接比较第一个元素是否相同，这是高频场景
            if (codeArray.size == 1) {
                return codeArray[0] == sortedCodeList[0]
            }
            // 否则，就循环挨个检查，不符合就返回false
            for (i in codeArray.indices) {
                if (codeArray[i] != sortedCodeList[i]) {
                    return false
                }
            }
            return true
        }

    }

    interface KeyObserver {
        fun onKeyDown(): Boolean

        fun onKeyUp(): Boolean
    }

}