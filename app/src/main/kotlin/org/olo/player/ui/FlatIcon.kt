package org.olo.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.olo.player.ui.theme.OloTheme

/**
 * The hue a [FileKind] is drawn in, from the OLO tile palette. Shared by the
 * poster/thumbnail fallbacks (PosterArt) and the detail hero poster so a kind
 * reads the same colour everywhere.
 *
 * (The former FileTile/TileIcon helpers here were unused -- tiles are drawn
 * inline at each call site -- so only this colour lookup remains.)
 */
@Composable
fun tileColorFor(kind: FileKind): Color {
    val c = OloTheme.colors
    return when (kind) {
        FileKind.FOLDER -> c.tileFolder
        FileKind.IMAGE -> c.tileImage
        FileKind.VIDEO -> c.tileVideo
        FileKind.AUDIO -> c.tileAudio
        FileKind.DOCUMENT -> c.tileDocument
        FileKind.ARCHIVE -> c.tileArchive
        FileKind.CODE -> c.tileCode
        FileKind.APP -> c.tileApp
        FileKind.OTHER -> c.tileOther
    }
}
