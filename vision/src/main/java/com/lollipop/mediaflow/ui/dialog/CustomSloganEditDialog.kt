package com.lollipop.mediaflow.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lollipop.mediaflow.R
import com.lollipop.mediaflow.tools.Preferences

class CustomSloganEditDialog : ComposeHalfDialog() {

    @Composable
    override fun DialogContent() {
        var customSloganValue by remember { mutableStateOf(Preferences.customSloganValue.get()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F),
                label = {
                    Text(text = stringResource(R.string.label_home_slogan_custom))
                },
                shape = MaterialTheme.shapes.large,
                value = customSloganValue,
                onValueChange = {
                    customSloganValue = it
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        Preferences.customSloganValue.set(customSloganValue)
                        dismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            }
        }
    }

}