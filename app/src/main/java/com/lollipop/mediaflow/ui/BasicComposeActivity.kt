package com.lollipop.mediaflow.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lollipop.common.ui.page.BasicPageActivity
import com.lollipop.mediaflow.ui.theme.MediaFlowTheme

/**
 * Compose 页面的共同父类（app 内 Compose 宿主）。
 *
 * ## 继承树（View / Compose 两套 insets 实现在 `BasicPageActivity` 之下**分叉**）
 *
 * ```
 * AppCompatActivity
 *  └─ BasicPageActivity            (common：日志 / edge-to-edge / 系统栏 / 朝向回调)
 *      ├─ ViewInsetsActivity       (common：View 侧 insets 采集 + Guideline 分发)
 *      │    └─ CustomOrientationActivity
 *      │         └─ BasicFlowActivity        (旧 View 外壳)
 *      └─ BasicComposeActivity     (本类：Compose 宿主)
 *           └─ BasicFlowComposeActivity      (Flow 页 Compose 外壳)
 * ```
 *
 * ## insets 归属
 * 本类**不桥接** View 的 insets：Compose 使用自身自洽的 `WindowInsets`
 * （`WindowInsets.systemBars` / `displayCutout` 等），与 View 分支的 Guideline 体系互不干扰。
 * 需要 insets 的 Compose 内容在组合期自行读取。
 *
 * ## 关于朝向
 * 继承 [BasicPageActivity] 的默认行为：朝向变化只通知、不切换系统栏，
 * 避免既有 Compose 页面在横屏下被意外全屏；需要全屏策略的页面自行覆盖 [onOrientationChanged]。
 */
abstract class BasicComposeActivity : BasicPageActivity() {

    /**
     * 内容承载方式。
     *
     * - `true`（默认）：使用 Material3 [Scaffold]，内容通过 [Content] 的 `innerPadding`
     *   自行内边距，保持既有页面的行为契约；
     * - `false`：自绘外壳（如 Flow 页）铺满渲染，insets 由页面自己处理。
     */
    protected open val useScaffold: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaFlowTheme {
                if (useScaffold) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Content(innerPadding)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Content(PaddingValues())
                    }
                }
            }
        }
    }

    @Composable
    abstract fun Content(innerPadding: PaddingValues)

    @Composable
    protected fun ContentColumn(
        modifier: Modifier = Modifier.fillMaxSize(),
        innerPadding: PaddingValues,
        showBack: Boolean = true,
        content: LazyListScope.() -> Unit
    ) {
        ContentColumn(
            modifier = modifier,
            innerPadding = innerPadding,
            showBack = showBack,
            onBack = {
                onBackPressedDispatcher.onBackPressed()
            },
            content = content
        )
    }

}
