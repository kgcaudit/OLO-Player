package org.olo.player.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.olo.player.ui.theme.OloTheme

/**
 * A file-kind tile, ported from OLO Explorer's FlatIcon: a rounded square filled
 * with the kind's hue, carrying its two-tone white glyph.
 *
 * The glyph is drawn with `tint = Color.Unspecified` so the artwork's own two
 * tones of white survive (a solid tint would flatten it to one). Kinds are told
 * apart by hue, so the same shape reads differently per kind.
 */
@Composable
fun TileIcon(
    @DrawableRes glyph: Int,
    colour: Color,
    modifier: Modifier = Modifier,
    size: Int = 40,
    cornerRadius: Int = 12,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(colour),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(glyph),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}

/** The tile for a [FileKind]: its glyph over its hue. */
@Composable
fun FileTile(kind: FileKind, modifier: Modifier = Modifier, size: Int = 40, cornerRadius: Int = 12) {
    TileIcon(kind.glyph, tileColorFor(kind), modifier, size, cornerRadius)
}

/** The hue a [FileKind] is drawn in, from the OLO tile palette. */
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
