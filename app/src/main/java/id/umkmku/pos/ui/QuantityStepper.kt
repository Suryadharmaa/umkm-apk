package id.umkmku.pos.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.vectorResource
import id.umkmku.pos.R
import id.umkmku.pos.domain.MAX_CART_QUANTITY

@Composable
internal fun QuantityStepper(name: String, quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (quantity > 0) {
            IconButton({ onChange(-1) }, Modifier.size(Space.touch)) {
                Icon(ImageVector.vectorResource(R.drawable.ic_remove), "Kurangi $name",
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(quantity.toString(), Modifier.semantics {
                contentDescription = "$quantity item $name"
                liveRegion = LiveRegionMode.Polite
            }, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        }
        IconButton({ onChange(1) }, Modifier.size(Space.touch), enabled = quantity < MAX_CART_QUANTITY) {
            Icon(Icons.Default.Add, "Tambah $name",
                tint = if (quantity < MAX_CART_QUANTITY) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}
