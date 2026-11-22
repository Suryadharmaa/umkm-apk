package id.umkmku.pos.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.umkmku.pos.feature.CatalogState

// Approved Phase 6 tokens. Apply the new type scale in migrated routes only.
private val merchantColors = lightColorScheme(
    primary = Color(0xFF1454BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE9FF),
    onPrimaryContainer = Color(0xFF102A56),
    secondary = Color(0xFF43536B),
    secondaryContainer = Color(0xFFEDF2FA),
    onSecondaryContainer = Color(0xFF17243A),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF17243A),
    surface = Color.White,
    onSurface = Color(0xFF17243A),
    surfaceVariant = Color(0xFFEDF2FA),
    onSurfaceVariant = Color(0xFF43536B),
    outline = Color(0xFF697992),
    error = Color(0xFFA51D2B),
    errorContainer = Color(0xFFFDEBED),
    onErrorContainer = Color(0xFFA51D2B),
)

internal object MerchantStyle {
    val header = Color(0xFF143A79)
    val slot = RoundedCornerShape(8.dp)
    val control = RoundedCornerShape(12.dp)
    val panel = RoundedCornerShape(16.dp)
    val overlay = RoundedCornerShape(24.dp)
    val fieldHeight = 56.dp
    val rowHeight = 64.dp
    val focus = 2.dp
}

private fun merchantType(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)

private val merchantTypography = Typography(
    displaySmall = merchantType(32, 40, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    headlineSmall = merchantType(24, 32, FontWeight.SemiBold),
    titleLarge = merchantType(20, 28, FontWeight.SemiBold),
    titleMedium = merchantType(16, 24, FontWeight.SemiBold),
    bodyLarge = merchantType(16, 24),
    bodyMedium = merchantType(14, 20),
    labelLarge = merchantType(14, 20, FontWeight.SemiBold),
    labelSmall = merchantType(12, 16, FontWeight.Medium),
)

@Composable
internal fun MerchantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = merchantColors,
        typography = merchantTypography,
        shapes = Shapes(small = MerchantStyle.control, medium = MerchantStyle.panel, large = MerchantStyle.overlay),
        content = content,
    )
}

internal fun Modifier.merchantFocus(shape: Shape, color: Color) = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }.then(
        if (focused) Modifier.border(MerchantStyle.focus, color, shape) else Modifier,
    )
}

@Composable
internal fun MerchantAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Space.touch).merchantFocus(MerchantStyle.panel, MaterialTheme.colorScheme.onSurface),
        shape = MerchantStyle.panel,
        colors = colors,
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
        Text(label, Modifier.padding(start = if (icon == null) 0.dp else Space.sm))
    }
}

@Composable
internal fun MerchantIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Space.touch).merchantFocus(MerchantStyle.control, MaterialTheme.colorScheme.primary),
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary) }
}

@Composable
internal fun MerchantSearch(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = MerchantStyle.fieldHeight),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = if (value.isEmpty()) null else {
            { MerchantIconButton(Icons.Default.Close, "Hapus pencarian", { onValueChange("") }) }
        },
        singleLine = true,
        shape = MerchantStyle.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
internal fun MerchantSection(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun MerchantMoney(formatted: String, modifier: Modifier = Modifier) {
    Text(formatted, modifier, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
}

@Composable
internal fun MerchantNotice(
    title: String, detail: String, action: String? = null, onAction: () -> Unit = {}, loading: Boolean = false,
) {
    Panel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, contentPadding = Space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) TextButton(onAction, Modifier.heightIn(min = Space.touch)) { Text(action) }
        }
    }
}

@Composable
internal fun MerchantCatalogStatus(state: CatalogState, retry: () -> Unit) {
    when (state) {
        CatalogState.Loading -> MerchantNotice("Memuat katalog…", "Membaca produk di perangkat.", loading = true)
        CatalogState.Error -> MerchantNotice("Katalog gagal dimuat", "Coba baca kembali data di perangkat ini.", "Coba lagi", retry)
        is CatalogState.Ready -> Unit
    }
}

internal enum class SaleBadge(val text: String, val ink: Color, val fill: Color) {
    CASH("Tunai tercatat", Color(0xFF12513A), Color(0xFFE2F4EA)),
    PENDING("Menunggu pembayaran QRIS", Color(0xFF694500), Color(0xFFFFF1CC)),
    CONFIRMED("QRIS dikonfirmasi manual", Color(0xFF12513A), Color(0xFFE2F4EA)),
    REFUNDED("Sudah dikembalikan penuh", Color(0xFF8B2134), Color(0xFFFBE8EE)),
}

@Composable
internal fun MerchantStatusChip(badge: SaleBadge) {
    Surface(shape = MerchantStyle.slot, color = badge.fill, contentColor = badge.ink) {
        Text(badge.text, Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            style = MaterialTheme.typography.labelSmall)
    }
}
