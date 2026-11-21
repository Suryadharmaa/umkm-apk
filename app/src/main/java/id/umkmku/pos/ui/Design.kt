package id.umkmku.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.umkmku.pos.feature.CatalogState

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val page = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val touch = 48.dp
}

val HeaderBlue = Color(0xFF124FAC)
val HeaderDeep = Color(0xFF143A79)
val HeaderSecondary = Color(0xFFDDE9FF)

private val palette = lightColorScheme(
    primary = Color(0xFF1454BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE9FF),
    onPrimaryContainer = Color(0xFF102A56),
    secondaryContainer = Color(0xFFE4EDFC),
    onSecondaryContainer = Color(0xFF102A56),
    background = Color(0xFFF6F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF2FA),
    onSurface = Color(0xFF17243A),
    onSurfaceVariant = Color(0xFF43536B),
    outline = Color(0xFF697992),
    error = Color(0xFFA51D2B),
    errorContainer = Color(0xFFFDEBED),
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)

private val typography = Typography(
    displaySmall = type(36, 44, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    headlineSmall = type(24, 32, FontWeight.SemiBold),
    titleLarge = type(20, 28, FontWeight.SemiBold),
    titleMedium = type(18, 24, FontWeight.SemiBold),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 20),
    labelLarge = type(14, 20, FontWeight.Medium),
    labelSmall = type(12, 16, FontWeight.Medium),
)

@Composable
fun WarungTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = palette,
        typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, contentPadding: androidx.compose.ui.unit.Dp = Space.page, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) { Column(Modifier.padding(contentPadding), content = { content() }) }
}

@Composable
fun MessagePanel(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun CatalogStatus(state: CatalogState, onRetry: () -> Unit) {
    when (state) {
        CatalogState.Loading -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Memuat katalog…")
            }
        }
        CatalogState.Error -> MessagePanel("Katalog gagal dimuat", "Coba baca kembali data di perangkat ini.", "Coba lagi", onRetry)
        is CatalogState.Ready -> Unit
    }
}
