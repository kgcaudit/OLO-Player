package org.olo.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.olo.player.ui.components.CpHeader
import org.olo.player.ui.theme.OloTheme

/**
 * A stand-in detail screen for a place in the frame that is drawn but not yet
 * built (a playlist category, a settings sub-tree). It states plainly that the
 * section is on the way, so the navigation is honest rather than a dead tap.
 */
@Composable
fun ComingSoon(title: String, note: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = OloTheme.colors
    Column(Modifier.fillMaxSize()) {
        CpHeader(title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("준비 중", color = c.accent, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(
                note,
                color = c.muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
