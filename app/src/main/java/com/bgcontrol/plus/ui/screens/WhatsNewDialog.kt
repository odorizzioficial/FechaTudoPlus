package com.bgcontrol.plus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bgcontrol.plus.BuildConfig
import com.bgcontrol.plus.R
import com.bgcontrol.plus.ui.theme.glassContainerColor

/**
 * Mostra a cada nova versão instalada, uma única vez, logo depois do
 * onboarding (ou na próxima abertura, se o onboarding já tinha sido feito
 * antes da atualização). A lista de novidades vem de um array de strings —
 * [R.array.whats_new_items] — que o desenvolvedor atualiza a cada release.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val itens = stringArrayResource(R.array.whats_new_items)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassContainerColor(),
        icon = {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME)
            )
        },
        text = {
            LazyColumn(modifier = Modifier.padding(top = 4.dp)) {
                items(itens.toList()) { item ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 6.dp, end = 10.dp)
                                .size(6.dp)
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_confirm))
            }
        }
    )
}
