package net.mamby.health.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import net.mamby.health.R

@Composable
fun DropdownTrailingIcon(expanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "Dropdown indicator rotation",
    )
    Icon(
        painter = painterResource(R.drawable.ic_lucide_chevron_down),
        contentDescription = null,
        modifier = modifier.rotate(rotation),
    )
}
