package com.lollipop.common.tools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

abstract class PreferencesBasic {


    class StringItem(
        val name: String,
        val def: String
    ) : TypedItem<String>() {
        override fun getPreferencesValue(): String {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: String) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class BooleanItem(
        val name: String,
        val def: Boolean
    ) : TypedItem<Boolean>() {
        override fun getPreferencesValue(): Boolean {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Boolean) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class IntItem(
        val name: String,
        val def: Int
    ) : TypedItem<Int>() {
        override fun getPreferencesValue(): Int {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Int) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class LongItem(
        val name: String,
        val def: Long
    ) : TypedItem<Long>() {
        override fun getPreferencesValue(): Long {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Long) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    class FloatItem(
        val name: String,
        val def: Float
    ) : TypedItem<Float>() {
        override fun getPreferencesValue(): Float {
            return PreferencesDelegate.get(name = name, def = def)
        }

        override fun setPreferencesValue(value: Float) {
            PreferencesDelegate.set(name = name, value = value)
        }

    }

    abstract class TypedItem<T> {

        protected val stateImpl by lazy {
            mutableStateOf(getPreferencesValue())
        }

        val state: State<T>
            get() {
                return stateImpl
            }

        fun get(): T {
            return state.value
        }

        fun set(value: T) {
            this.stateImpl.value = value
            setPreferencesValue(value)
        }

        protected abstract fun getPreferencesValue(): T

        protected abstract fun setPreferencesValue(value: T)

    }

    protected object PreferencesDelegate {
        private const val PREFERENCES_NAME = "MediaFlow"

        private var preferences: SharedPreferences? = null

        fun init(context: Context) {
            preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }

        fun get(name: String, def: Int = 0): Int {
            return preferences?.getInt(name, def) ?: return def
        }

        fun set(name: String, value: Int) {
            preferences?.edit {
                putInt(name, value)
            }
        }

        fun get(name: String, def: Boolean = false): Boolean {
            return preferences?.getBoolean(name, def) ?: return def
        }

        fun set(name: String, value: Boolean) {
            preferences?.edit {
                putBoolean(name, value)
            }
        }

        fun get(name: String, def: Long = 0): Long {
            return preferences?.getLong(name, def) ?: return def
        }

        fun set(name: String, value: Long) {
            preferences?.edit {
                putLong(name, value)
            }
        }

        fun get(name: String, def: String = ""): String {
            return preferences?.getString(name, def) ?: return def
        }

        fun set(name: String, value: String) {
            preferences?.edit {
                putString(name, value)
            }
        }

        fun get(name: String, def: Float = 0F): Float {
            return preferences?.getFloat(name, def) ?: return def
        }

        fun set(name: String, value: Float) {
            preferences?.edit {
                putFloat(name, value)
            }
        }
    }

}