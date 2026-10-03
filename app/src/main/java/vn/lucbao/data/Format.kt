package vn.lucbao.data

import java.util.Locale

object Format {
    /** 1234 -> "1,2 N", 1_200_000 -> "1,2 Tr" */
    fun count(n: Long): String = when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 1_000_000 -> short(n / 1_000.0) + " N"
        n < 1_000_000_000 -> short(n / 1_000_000.0) + " Tr"
        else -> short(n / 1_000_000_000.0) + " T"
    }

    private fun short(v: Double): String {
        val s = if (v >= 100) String.format(Locale.ROOT, "%.0f", v)
        else String.format(Locale.ROOT, "%.1f", v)
        return s.removeSuffix(".0").replace('.', ',')
    }

    fun views(n: Long): String = if (n < 0) "" else "${count(n)} lượt xem"

    /** seconds -> "3:07" / "1:02:03" */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun millis(ms: Long): String = duration(ms / 1000).ifEmpty { "0:00" }

    /** Joins non-empty parts with " · " */
    fun dot(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    fun hours(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 60) "$minutes phút" else String.format(
            Locale.ROOT, "%.1f giờ", minutes / 60.0
        ).replace(".0 ", " ").replace('.', ',')
    }
}
