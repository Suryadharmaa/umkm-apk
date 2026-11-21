package id.umkmku.pos

import id.umkmku.pos.domain.qrisCrc
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Deliberately non-routable local test data. No provider account or transaction is used.
internal fun fixturePayload(name: String = "FIXTURE UJI NONBAYAR"): String {
    fun field(id: String, value: String) = id + value.length.toString().padStart(2, '0') + value
    val body = field("00", "01") + field("01", "11") +
        field("26", field("00", "INVALID.TEST")) +
        field("51", field("00", "ID.CO.QRIS.WWW") + field("02", "FIXTURE_ONLY")) +
        field("52", "0000") + field("53", "360") + field("58", "ID") +
        field("59", name) + field("60", "FIXTURE") + "6304"
    return body + qrisCrc(body)
}

internal fun fixtureMerchantTime(time: LocalDateTime = LocalDateTime.now()): String =
    time.format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT))
