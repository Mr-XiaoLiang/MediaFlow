package com.lollipop.mediaflow.page.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.MainActivity
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaChooser
import com.lollipop.mediaflow.data.MediaChooser.MediaResult
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.data.MediaVisibility
import com.lollipop.mediaflow.data.RootUri
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.ui.BasicComposeActivity
import com.lollipop.mediaflow.ui.theme.currentThemeColor

class RootUriManagerActivity : BasicComposeActivity() {

    companion object {
        const val PARAMS_VISIBILITY = "mediaVisibility"

        fun start(context: Context, visibility: MediaVisibility) {
            val intent = Intent(context, RootUriManagerActivity::class.java)
            intent.putExtra(PARAMS_VISIBILITY, visibility.key)
            if (context !is MainActivity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

    }

    private var visibility: MediaVisibility = MediaVisibility.Public

    private val rootUriList = SnapshotStateList<RootUri>()

    private val mediaStore by lazy {
        MediaStore.loadStore(this, visibility)
    }

    private val mediaChooser by lazy {
        MediaChooser(mediaStore, ::onChooseResult)
    }

    private val log = registerLog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visibility = MediaVisibility.findByKey(intent.getStringExtra(PARAMS_VISIBILITY) ?: "")
        mediaChooser.register(this)
        reloadCache()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun onChooseResult(result: MediaResult) {
        result.remember(this) {
            if (it) {
                reloadCache()
            } else {
                log.e("onChooseResult: 选择根目录失败")
            }
        }
    }

    private fun reloadCache() {
        rootUriList.clear()
        rootUriList.addAll(mediaStore.cache.rootList)
        log.i("reloadCache: 刷新根目录成功: ${rootUriList.size}")
    }

    private fun refreshList() {
        mediaStore.loadRootUri {
            if (it) {
                reloadCache()
            } else {
                log.e("refreshList: 刷新根目录失败")
            }
        }
    }

    private fun removeRootUri(rootUri: RootUri) {
        mediaStore.remove(rootUri.uri) {
            if (!it) {
                reloadCache()
            }
        }
        rootUriList.remove(rootUri)
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val isVisible by remember { mutableStateOf(visibility == MediaVisibility.Public) }
        val uriList = remember { rootUriList }
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isVisible) {
                Icon(
                    modifier = Modifier
                        .size(300.dp),
                    painter = painterResource(id = R.drawable.domino_mask_24),
                    tint = currentThemeColor().buttonMask,
                    contentDescription = null
                )
            }
            ContentColumn(
                innerPadding = innerPadding
            ) {
                items(uriList) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = it.name,
                            modifier = Modifier.weight(1F)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier
                                .clickable {
                                    removeRootUri(it)
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                mediaChooser.launch()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}