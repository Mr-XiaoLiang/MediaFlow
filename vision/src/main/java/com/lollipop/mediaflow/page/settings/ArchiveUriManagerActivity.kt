package com.lollipop.mediaflow.page.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.lollipop.mediaflow.MainActivity
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.ArchiveManager
import com.lollipop.mediaflow.data.ArchiveQuick
import com.lollipop.mediaflow.data.MediaChooser
import com.lollipop.mediaflow.data.MediaChooser.MediaResult
import com.lollipop.mediaflow.data.MediaLoader
import com.lollipop.common.tools.LLog.Companion.registerLog
import com.lollipop.common.ui.page.BasicComposeActivity
import com.lollipop.common.ui.theme.currentThemeColor
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

    private val mediaChooser by lazy {
        MediaChooser(::onChooseResult)
    }

    private val log = registerLog()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaChooser.register(this)
        reloadCache()
    }

    private fun onChooseResult(result: MediaResult) {
        result.remember(this)
        val resultUri = result.uri
        val uriPath = resultUri?.toString()
        if (resultUri != null && uriPath != null) {
            val activity = this
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val name = MediaLoader.getRootFolderName(activity, resultUri)
                    if (name != null) {
                        ArchiveManager.addBasket(activity, name = name, uri = resultUri)
                        log.i("onChooseResult: resultUri = $resultUri, uriPath = $uriPath")
                    }
                }
            }
        }
    }

    private fun reloadCache() {
        ArchiveManager.init(this)
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        Box(modifier = Modifier.fillMaxSize()) {
            ArchiveContent(innerPadding)
        }
    }

    @Composable
    private fun ArchiveContent(innerPadding: PaddingValues) {
        val activity = this
        val basketList = remember { ArchiveManager.archiveBasketList }
        val favorite by remember { ArchiveManager.favorite }
        val special by remember { ArchiveManager.special }
        val thumpUp by remember { ArchiveManager.thumpUp }
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
            items(basketList) { info ->
                val currentIcon = when (info.uriString) {
                    favorite?.uriString -> ArchiveQuick.Favorite
                    special?.uriString -> ArchiveQuick.Special
                    thumpUp?.uriString -> ArchiveQuick.ThumpUp
                    else -> ArchiveQuick.Other
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropdownQuickIcon(
                        current = currentIcon,
                        onSelect = { quick ->
                            ArchiveManager.setQuick(activity, quick, info)
                        }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1F)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = info.name,
                            fontSize = 16.sp
                        )
                        Text(
                            text = info.uriPath,
                            fontSize = 12.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                ArchiveManager.removeBasket(activity, info)
                            }
                            .padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
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

    @Composable
    private fun DropdownQuickIcon(
        current: ArchiveQuick,
        onSelect: (ArchiveQuick) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            Icon(
                painter = painterResource(current.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        expanded = true
                    }
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4F),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp)
            )
            // 菜单会自动跟随这个 Box
            DropdownMenu(
                shape = MaterialTheme.shapes.medium,
                containerColor = currentThemeColor().buttonBackground,
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ArchiveQuick.entries.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(item.labelRes)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        }
                    )
                }
            }
        }

    }

}