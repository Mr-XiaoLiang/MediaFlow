package com.lollipop.mediaflow.page.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.MainActivity
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.MediaChooser
import com.lollipop.mediaflow.data.MediaChooser.MediaResult
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.mediaflow.tools.LLog.Companion.registerLog
import com.lollipop.mediaflow.tools.Preferences
import com.lollipop.mediaflow.ui.BasicComposeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveUriManagerActivity : BasicComposeActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ArchiveUriManagerActivity::class.java)
            if (context !is MainActivity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

    }

    private val archiveUri = mutableStateOf<Uri>(Uri.EMPTY)
    private val archiveDirName = mutableStateOf("")

    private val mediaChooser by lazy {
        MediaChooser(::onChooseResult)
    }

    private val log = registerLog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaChooser.register(this)
        reloadCache()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun onChooseResult(result: MediaResult) {
        val resultUri = result.uri
        val uriPath = resultUri?.path
        if (resultUri != null && uriPath != null) {
            val activity = this
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val name = MediaLoader.getRootFolderName(activity, resultUri)
                    if (name != null) {
                        archiveUri.value = resultUri
                        archiveDirName.value = name
                        Preferences.archiveDirUri.set(uriPath)
                        Preferences.archiveDirName.set(name)
                    } else {
                        resetEmptyUri()
                    }
                }
            }
        } else {
            resetEmptyUri()
        }
    }

    private fun resetEmptyUri() {
        archiveUri.value = Uri.EMPTY
        archiveDirName.value = ""
    }

    private fun reloadCache() {
        val uriStr = Preferences.archiveDirUri.get()
        val nameStr = Preferences.archiveDirName.get()
        if (uriStr.isEmpty()) {
            resetEmptyUri()
        } else {
            archiveUri.value = uriStr.toUri()
            archiveDirName.value = nameStr
        }
    }

    private fun refreshList() {
        reloadCache()
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val uriState by remember { archiveUri }
        val dirName by remember { archiveDirName }
        Box(modifier = Modifier.fillMaxSize()) {
            ContentColumn(
                innerPadding = innerPadding
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.hint_archive_uri_manager),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7F)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = dirName,
                            fontSize = 16.sp
                        )
                        Text(
                            text = uriState.path ?: "",
                            fontSize = 14.sp
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
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}