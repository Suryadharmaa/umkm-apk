package id.umkmku.pos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun MerchantNavigation(selected: Tab, onSelect: (Tab) -> Unit, rail: Boolean = false) = MerchantTheme {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.testTag("app-navigation"),
    ) {
        val insets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        if (rail) {
            Column(
                Modifier.windowInsetsPadding(insets).width(120.dp).padding(Space.sm).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Tab.entries.forEach { item ->
                    Destination(item, selected, onSelect, Modifier.fillMaxWidth())
                }
            }
        } else {
            BoxWithConstraints(Modifier.windowInsetsPadding(insets).fillMaxWidth().padding(horizontal = Space.xs)) {
                val style = MaterialTheme.typography.labelSmall
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val labelWidth = Tab.entries.maxOf { with(density) { measurer.measure(it.title, style).size.width.toDp() } }
                val available = (maxWidth - Space.xs * 4) / 5
                val rows = if (labelWidth + Space.sm <= available && available >= Space.touch) {
                    listOf(Tab.entries)
                } else {
                    Tab.entries.chunked(3)
                }
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            row.forEach { item ->
                                Destination(item, selected, onSelect, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Destination(item: Tab, selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val active = item == selected
    val ink = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.heightIn(min = Space.touch)
            .merchantFocus(MerchantStyle.control, MaterialTheme.colorScheme.primary)
            .selectable(active, role = Role.Tab, onClick = { onSelect(item) })
            .testTag("destination-${item.route}").padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.width(44.dp).heightIn(min = 28.dp).background(
                if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                MerchantStyle.control,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(item.icon, null, Modifier.size(24.dp), tint = ink) }
        Text(item.title, style = MaterialTheme.typography.labelSmall, color = ink, textAlign = TextAlign.Center)
    }
}
