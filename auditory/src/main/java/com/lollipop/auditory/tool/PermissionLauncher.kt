package com.lollipop.auditory.tool

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import com.lollipop.auditory.R
import com.lollipop.common.tools.LLog.Companion.registerLog

val LocalPermissionLauncher = staticCompositionLocalOf {
    PermissionLauncher.Delegate(null)
}

class PermissionLauncher(
    private val activity: AppCompatActivity,
    private val onResultCallback: (StateMap) -> Unit
) {

    private val permissionList = listOf(
        Permission.ReadMedia,
        Permission.Notification
    )

    private val requestPermissionLauncher by lazy {
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { map: Map<String, Boolean> ->
            onResult(StateMap(map))
        }
    }

    val stateList = SnapshotStateList<PermissionResult>()
    private val isAllGrantedState = mutableStateOf(false)

    val delegate by lazy {
        Delegate(this)
    }

    val isAllGranted: State<Boolean>
        get() {
            return isAllGrantedState
        }

    fun onCreate() {
        // 调用来初始化并且注册
        requestPermissionLauncher
    }

    fun onResume() {
        // 检查权限并更新状态
        setResult(checkMusicPermission())
    }

    fun request() {
        val request = mutableMapOf<String, Boolean>()
        val unGrantedList = mutableListOf<String>()
        permissionList.forEach { permission ->
            val check = permission.check(activity)
            request[permission.key] = check
            if (!check) {
                unGrantedList.add(permission.key)
            }
        }
        if (unGrantedList.isEmpty()) {
            val state = StateMap(request)
            setResult(state)
            return
        }
        requestPermissionLauncher.launch(unGrantedList.toTypedArray())
    }

    fun checkMusicPermission(): StateMap {
        val stateMap = mutableMapOf<String, Boolean>()
        permissionList.forEach { permission ->
            stateMap[permission.key] = permission.check(activity)
        }
        return StateMap(stateMap)
    }

    private fun onResult(stateMap: StateMap) {
        setResult(stateMap)
    }

    private fun setResult(state: StateMap) {
        val temp = mutableListOf<PermissionResult>()
        permissionList.forEach { permission ->
            temp.add(PermissionResult(permission.isGranted(state), permission))
        }
        stateList.clear()
        stateList.addAll(temp)
        isAllGrantedState.value = temp.all { it.isGranted }
        onResultCallback(state)
    }

    class Delegate(
        val launcher: PermissionLauncher?
    ) {

        val stateList: SnapshotStateList<PermissionResult>
            get() {
                return launcher?.stateList ?: SnapshotStateList()
            }

        val isAllGranted: State<Boolean>
            get() {
                return launcher?.isAllGranted ?: mutableStateOf(false)
            }

        fun launch() {
            launcher?.request()
        }

    }

    sealed class Permission(
        val minSdkVersion: Int = 0,
        val maxSdkVersion: Int = Int.MAX_VALUE
    ) {
        abstract val key: String
        abstract val labelRes: Int
        abstract val explanationRes: Int

        protected val log by lazy {
            registerLog("Permission")
        }

        fun isGranted(stateMap: StateMap): Boolean {
            if (minSdkVersion > Build.VERSION.SDK_INT) {
                return true
            }
            if (maxSdkVersion < Build.VERSION.SDK_INT) {
                return true
            }
            return stateMap.state[key] ?: false
        }

        fun check(context: Context): Boolean {
            if (minSdkVersion > Build.VERSION.SDK_INT) {
                return true
            }
            if (maxSdkVersion < Build.VERSION.SDK_INT) {
                return true
            }
            return ContextCompat.checkSelfPermission(
                context,
                key
            ) == PackageManager.PERMISSION_GRANTED
        }

        open fun settingsPage(context: Context) {
            openAppDetailsSettings(context)
        }

        protected fun openAppDetailsSettings(context: Context) {
            try {
                val intent = Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    // 核心：使用 package 协议包裹你当前 App 的包名
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                log.e("openAppDetailsSettings", e)
            }
        }

        object ReadMedia : Permission() {

            override val key: String
                get() {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO // Android 13+
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE // Android 12 及以下
                    }
                }

            override val labelRes: Int
                get() {
                    return R.string.title_permission_read_media_audio
                }

            override val explanationRes: Int
                get() {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        R.string.info_permission_read_media_audio_explanation
                    } else {
                        R.string.info_permission_read_external_storage_explanation
                    }
                }

        }

        object Notification : Permission(
            minSdkVersion = Build.VERSION_CODES.TIRAMISU
        ) {
            override val key: String
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                get() {
                    return Manifest.permission.POST_NOTIFICATIONS
                }

            override val labelRes: Int
                get() {
                    return R.string.title_permission_notification
                }

            override val explanationRes: Int
                get() {
                    return R.string.info_permission_notification_explanation
                }

            override fun settingsPage(context: Context) {
                try {
                    val intent = Intent().apply {
                        // Android 8.0 及以上，直接定位到当前应用的通知开关页
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        // 在 Activity 之外（如 Service 或 Dialog 回调中）启动时需要加这个 Flag
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    log.e("settingsPage", e)
                    // 防止极少数魔改系统无法识别，降级跳转到“应用详情页”
                    openAppDetailsSettings(context)
                }
            }

        }

    }

    class StateMap(
        state: Map<String, Boolean>
    ) {

        val state: Map<String, Boolean> = HashMap(state)

        val isGranted: Boolean by lazy {
            state.values.all { it }
        }

    }

    class PermissionResult(
        val isGranted: Boolean,
        val permission: Permission
    )

}