package com.bgcontrol.plus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bgcontrol.plus.util.PackageUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.bgcontrol.plus.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches simples de ícone e de origem do pacote.
 *
 * Sem eles cada rolagem da lista relê o PackageManager e redesenha o bitmap de
 * cada linha. São poucos aplicativos por aparelho, então guardar tudo em
 * memória custa pouco e evita trabalho repetido a cada quadro.
 */
private val cacheIcones = mutableMapOf<String, ImageBitmap?>()
private val cacheSistema = mutableMapOf<String, Boolean>()

/** Ícone real do aplicativo, no contêiner arredondado do layout. */
@Composable
fun AppIconTile(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cornerRadius: Dp = 12.dp,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val context = LocalContext.current
    val icon by produceState(initialValue = cacheIcones[packageName], packageName) {
        if (cacheIcones.containsKey(packageName)) {
            value = cacheIcones[packageName]
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            PackageUtils.getAppIcon(context, packageName)
                ?.toBitmap(width = 128, height = 128)
                ?.asImageBitmap()
        }.also { cacheIcones[packageName] = it }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .padding(8.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}


/** Diz, de forma assíncrona, se o pacote faz parte do sistema. */
@Composable
fun rememberIsSystemApp(packageName: String): Boolean {
    val context = LocalContext.current
    val doSistema by produceState(initialValue = cacheSistema[packageName] ?: false, packageName) {
        cacheSistema[packageName]?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            PackageUtils.isSystemPackage(context, packageName)
        }.also { cacheSistema[packageName] = it }
    }
    return doSistema
}

/**
 * Ícone do aplicativo com um selo discreto no canto quando é do sistema.
 *
 * O selo fica sobre o ícone, e não embaixo: texto solto debaixo de cada ícone
 * desalinhava as linhas da lista e ficava difícil de ler.
 */
@Composable
fun AppIconWithBadge(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val doSistema = rememberIsSystemApp(packageName)

    Box(modifier = modifier.size(size)) {
        AppIconTile(packageName = packageName, size = size)
        if (doSistema) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = stringResource(R.string.system_app),
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Segunda linha dos cartões: o pacote e, quando for do sistema, a marcação
 * "App do sistema" separada por um ponto — tudo em uma linha só.
 */
@Composable
fun AppSecondaryLine(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val doSistema = rememberIsSystemApp(packageName)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (doSistema) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = stringResource(R.string.system_app),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                maxLines = 1
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = packageName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
