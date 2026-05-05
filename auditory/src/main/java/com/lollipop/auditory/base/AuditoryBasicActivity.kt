package com.lollipop.auditory.base

import android.content.res.Configuration
import android.os.Bundle
import com.lollipop.auditory.state.UIStateRepository
import com.lollipop.common.ui.page.CustomOrientationActivity

abstract class AuditoryBasicActivity : CustomOrientationActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateDarkMode(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 捕获系统暗色模式变化
        updateDarkMode(newConfig)
    }

    private fun updateDarkMode(config: Configuration) {
        // 捕获系统暗色模式变化
        UIStateRepository.onDarkModeChanged(checkUiModeIsDark(config.uiMode))
    }

    private fun checkUiModeIsDark(uiMode: Int): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

}