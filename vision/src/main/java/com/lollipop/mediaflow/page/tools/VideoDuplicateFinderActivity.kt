package com.lollipop.mediaflow.page.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.data.ArchiveQuick
import com.lollipop.mediaflow.data.MediaInfo
import com.lollipop.mediaflow.data.MediaStore
import com.lollipop.mediaflow.tools.ArchiveHelper
import com.lollipop.mediaflow.tools.VideoDuplicateFinder
import com.lollipop.common.tools.doAsync
import com.lollipop.common.tools.onUI
import com.lollipop.common.ui.page.BasicComposeActivity
import com.lollipop.mediaflow.ui.HomePage
import com.lollipop.common.ui.view.PreferencesDivider
import com.lollipop.common.ui.view.PreferencesGroup
import com.lollipop.common.ui.view.PreferencesGroupItem

class VideoDuplicateFinderActivity : BasicComposeActivity() {

    companion object {
        private const val KEY_SOURCE_PAGE = "SourcePage"
        fun start(context: Context, page: HomePage) {
            val intent = Intent(context, VideoDuplicateFinderActivity::class.java)
            intent.putExtra(KEY_SOURCE_PAGE, page.key)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val duplicateList = SnapshotStateList<DuplicateGroup>()
    private val isLoadingState = mutableStateOf(true)

    private val sourcePage by lazy {
        HomePage.findPage(intent.getStringExtra(KEY_SOURCE_PAGE) ?: "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = sourcePage
        val sourceList = if (page != null) {
            MediaStore.loadGallery(this, page.visibility, page.mediaType).fileList
        } else {
            emptyList()
        }
        VideoDuplicateFinder.findDuplicates(this, sourceList, ::onDuplicatesFound)
    }

    private fun onDuplicatesFound(duplicates: List<VideoDuplicateFinder.Duplicate>) {
        // 拿到之后，按照Compose的要求进行一次转换格式
        doAsync {
            val tempList = ArrayList<DuplicateGroup>()
            duplicates.forEach { duplicate ->
                val group = DuplicateGroup()
                for (media in duplicate.list) {
                    group.add(media.file)
                }
                tempList.add(group)
            }
            onUI {
                isLoadingState.value = false
                duplicateList.clear()
                duplicateList.addAll(tempList)
            }
        }
    }

    private fun archive(group: DuplicateGroup, media: MediaInfo.File) {
        ArchiveHelper.remove(this, media, ArchiveQuick.Other, null) {
            if (group.size < 3) {
                duplicateList.remove(group)
            } else {
                group.remove(media)
            }
        }
    }

    @Composable
    override fun Content(innerPadding: PaddingValues) {
        val isLoading by remember { isLoadingState }
        if (isLoading) {
            Loading(innerPadding)
        } else {
            DuplicateList(innerPadding)
        }
    }

    @Composable
    private fun Loading(innerPadding: PaddingValues) {
        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalGlideComposeApi::class)
    @Composable
    private fun DuplicateList(innerPadding: PaddingValues) {
        val mediaList = remember { duplicateList }
        ContentColumn(
            innerPadding = innerPadding,
            showBack = true
        ) {

            PreferencesGroupItem {
                Text(
                    text = stringResource(R.string.summary_video_duplicate),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            items(mediaList) { group ->
                val itemList = remember { group.mediaList }
                PreferencesGroup {
                    for (index in itemList.indices) {
                        val item = itemList[index]

                        if (index > 0) {
                            PreferencesDivider()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlideImage(
                                modifier = Modifier
                                    .size(64.dp),
                                model = item.uri,
                                contentDescription = "",
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1F),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(text = item.name)
                                item.metadata?.let { metadata ->
                                    Row {
                                        Text(
                                            text = metadata.sizeFormat,
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 4.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = metadata.durationFormat,
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(horizontal = 4.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Text(text = item.path)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Image(
                                painter = painterResource(R.drawable.archive_24),
                                contentDescription = "",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        archive(group, item)
                                    }
                                    .padding(6.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

    }

    private class DuplicateGroup {
        val mediaList = SnapshotStateList<MediaInfo.File>()

        val size: Int
            get() {
                return mediaList.size
            }

        fun add(file: MediaInfo.File) {
            mediaList.add(file)
        }

        fun remove(file: MediaInfo.File) {
            mediaList.remove(file)
        }

    }

}