package com.nuvio.tv.ui.components

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EmptyScreenState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    height: Dp = 400.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 666TV : une tombe « R.I.P. » à la place de l'icône.
        @Suppress("UNUSED_VARIABLE") val unusedIcon = icon
        RipTomb666(height = (height * 0.4f).coerceAtMost(170.dp))
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

        Text(
            text = title,
            style = Font666.horror(MaterialTheme.typography.headlineSmall, 1.2f),
            color = Font666.Green
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}
