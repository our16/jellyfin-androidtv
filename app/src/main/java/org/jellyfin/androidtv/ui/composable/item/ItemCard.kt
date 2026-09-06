package org.jellyfin.androidtv.ui.composable.item

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme

@Composable
@Stable
fun ItemCard(
	modifier: Modifier = Modifier,
	focused: Boolean = false,
	image: @Composable BoxScope.() -> Unit,
	overlay: (@Composable BoxScope.() -> Unit)? = null,
	shape: Shape = JellyfinTheme.shapes.medium,
) {
	val surface by animateColorAsState(
		targetValue = if (focused) JellyfinTheme.colorScheme.focusedSurface else JellyfinTheme.colorScheme.surface,
		label = "ItemCardSurface",
	)
	val borderColor by animateColorAsState(
		targetValue = if (focused) Color.White else Color.Transparent,
		label = "ItemCardBorder",
	)

	Box(
		modifier = modifier
			.clip(shape)
			.background(surface, shape)
	) {
		image()

		if (overlay != null) {
			Box(
				modifier = Modifier.fillMaxSize(),
				content = overlay
			)
		}

		// Focus highlight drawn on top of the image content so it is always
		// visible, even against dark posters on the black background
		Box(
			modifier = Modifier
				.fillMaxSize()
				.border(3.dp, borderColor, shape)
		)
	}
}
