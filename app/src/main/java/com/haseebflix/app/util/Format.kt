package com.haseebflix.app.util

import com.haseebflix.app.domain.TitleSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun runtimeText(min: Int?): String? = when {
    min == null || min <= 0 -> null
    min < 60 -> "${min}m"
    else -> "${min / 60}h ${min % 60}m"
}

fun shortMonthDay(date: String): String? = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)).uppercase()
}.getOrNull()

fun longDate(date: String?): String? = runCatching {
    LocalDate.parse(date!!).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}.getOrNull()

fun daysUntil(date: String): Long? = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date)) }.getOrNull()

/** "Today", "Yesterday", "3 days ago", or a date. */
fun relativeDate(date: String?): String {
    val d = runCatching { LocalDate.parse(date!!) }.getOrNull() ?: return ""
    val diff = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Yesterday"
        diff in 2..6 -> "$diff days ago"
        diff < 0 -> "Airs ${longDate(date)}"
        else -> longDate(date).orEmpty()
    }
}

fun relativeTime(millis: Long): String {
    val m = (System.currentTimeMillis() - millis) / 60_000
    return when {
        m < 1 -> "Just now"
        m < 60 -> "${m}m ago"
        m < 1440 -> "${m / 60}h ago"
        m < 2880 -> "Yesterday"
        else -> "${m / 1440} days ago"
    }
}

fun dayBucket(millis: Long): Int {
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val diff = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when { diff <= 0 -> 0; diff < 7 -> 1; else -> 2 }
}

fun compactCount(n: Int): String = when {
    n >= 1000 -> String.format(Locale.US, "%.1fk+", n / 1000f).replace(".0k", "k")
    else -> "$n+"
}

fun TitleSummary.metaLine(): String =
    listOfNotNull(year.ifBlank { null }, genres.take(2).joinToString(", ").ifBlank { null }).joinToString(" • ")

fun shareUrl(type: String, id: Int) = "https://www.themoviedb.org/$type/$id"

fun isFuture(date: String?): Boolean = runCatching { LocalDate.parse(date!!).isAfter(LocalDate.now()) }.getOrDefault(false)
