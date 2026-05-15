package com.lollipop.auditory.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lollipop.auditory.audio.AudioServiceHelper
import com.lollipop.auditory.audio.LocalAudioController
import com.lollipop.auditory.model.LocalAudioViewModel
import com.lollipop.common.tools.LLog

@Composable
fun MainPage(innerPadding: PaddingValues) {
    val viewModel = LocalAudioViewModel.current // 获取ViewModel
    val songs by viewModel.songs.collectAsStateWithLifecycle() // 获取歌曲列表
    val controller = LocalAudioController.current
    val log = remember { LLog("MainPage") }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
        }
        items(songs, key = { it.id }) { song ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0x33FF0000), shape = MaterialTheme.shapes.large)
                    .clickable {
                        controller.option {
                            it.setMediaItem(AudioServiceHelper.toMediaItem(song))
                            log.i("setMediaItem: ${song.displayName}, ${song.uri}")
                            it.prepare()
                            it.play()
                        }
                    }
                    .padding(16.dp)
            ) {
                Text(song.displayName, fontSize = 16.sp)
                Text(song.artist, fontSize = 12.sp)
                Text(song.album, fontSize = 12.sp)
            }
        }
        item {
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }
}