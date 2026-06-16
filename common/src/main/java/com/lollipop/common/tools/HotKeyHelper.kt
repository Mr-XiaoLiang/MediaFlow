package com.lollipop.common.tools

class HotKeyHelper {

    private val keyCodeSet = HashSet<Int>()
    private val keyObserverList = ArrayList<KeyObserverHolder>()
    private val activeObserverList = ArrayList<KeyObserverHolder>()

    fun onKeyDown(keyCode: Int): Boolean {
        if (keyCodeSet.contains(keyCode)) {
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        if (keyCodeSet.contains(keyCode)) {
            return true
        }

        return false
    }

    class KeyObserverHolder(
        val keyCodeList: List<Int>,
        val callback: () -> Unit
    )

    interface KeyObserver {
        fun onKeyDown(): Boolean

        fun onKeyUp(): Boolean
    }

}