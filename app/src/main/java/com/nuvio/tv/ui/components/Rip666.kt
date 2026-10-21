package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R

/** 666TV : petite tombe « R.I.P. » pour les écrans vides (aucun flux, aucun résultat…). */
@Composable
fun RipTomb666(height: Dp = 160.dp, modifier: Modifier = Modifier) {
    val width = height * (630f / 973f)
    Box(modifier = modifier.size(width, height)) {
        // fenêtre de la tombe (mêmes proportions que pour les affiches)
        Box(
            modifier = Modifier
                .offset(x = width * 0.2381f, y = height * 0.2456f)
                .size(width * (0.7651f - 0.2381f), height * (0.7379f - 0.2456f))
                .background(Color(0xFF0E0E0E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "R.I.P.",
                style = Font666.horror(MaterialTheme.typography.titleLarge).copy(fontSize = (height.value * 0.14f).sp),
                color = Font666.Green
            )
        }
        Image(
            painter = painterResource(R.drawable.frame_tombe_666),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
