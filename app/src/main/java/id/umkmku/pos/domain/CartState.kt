package id.umkmku.pos.domain

internal const val MAX_CART_PRODUCTS = 200
internal const val MAX_CART_QUANTITY = 9999

internal fun restoreCart(entries: List<*>): Map<String, Int> {
    val restored = linkedMapOf<String, Int>()
    for (entry in entries.take(MAX_CART_PRODUCTS)) {
        val encoded = entry as? String ?: continue
        val id = encoded.substringBefore(':')
        val count = encoded.substringAfter(':', "").toIntOrNull()
        if (id.isNotBlank() && id.length <= 64 && count != null && count in 1..MAX_CART_QUANTITY) {
            restored.putIfAbsent(id, count)
        }
    }
    return restored
}
