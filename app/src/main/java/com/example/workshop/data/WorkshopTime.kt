package com.example.workshop.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * One place for every date and time the app saves
 * or calculates with.
 *
 * Times are saved WITH their date, for example
 * "2026-11-21 09:15 AM", so working time is correct
 * even when a shift runs past midnight.
 *
 * Records saved by version 1 only had the time
 * ("09:15 AM"). Those are still read, using the
 * record's own date.
 */
object WorkshopTime {

    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val DATE_TIME_PATTERN = "yyyy-MM-dd hh:mm a"
    private const val LEGACY_TIME_PATTERN = "hh:mm a"

    // Saved values always use English AM/PM so every
    // phone can read them, whatever its language.
    private val storageLocale = Locale.US

    private fun dateFormat() =
        SimpleDateFormat(DATE_PATTERN, storageLocale)

    private fun dateTimeFormat() =
        SimpleDateFormat(DATE_TIME_PATTERN, storageLocale).apply {
            isLenient = false
        }

    /* "2026-11-21" */
    fun today(): String = dateFormat().format(Date())

    /* The day before today, "2026-11-20" */
    fun yesterday(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat().format(calendar.time)
    }

    /* "2026-11-21 09:15 AM" */
    fun now(): String = dateTimeFormat().format(Date())

    /*
     * Read a saved date-time.
     *
     * fallbackDate is used only for old time-only
     * values, which are assumed to be on that day.
     */
    fun parse(
        value: String?,
        fallbackDate: String? = null
    ): Date? {

        if (value.isNullOrBlank()) {
            return null
        }

        try {
            return dateTimeFormat().parse(value)
        } catch (e: Exception) {
            // Not the new format; try the old one below.
        }

        return try {
            val date = fallbackDate ?: today()
            dateTimeFormat().parse("$date $value")
                ?: parseLegacyWithDeviceLocale(value, date)
        } catch (e: Exception) {
            parseLegacyWithDeviceLocale(value, fallbackDate ?: today())
        }
    }

    // Version 1 saved times using the phone's language.
    private fun parseLegacyWithDeviceLocale(
        value: String,
        date: String
    ): Date? {
        return try {
            val parsedTime =
                SimpleDateFormat(
                    LEGACY_TIME_PATTERN,
                    Locale.getDefault()
                ).parse(value) ?: return null

            val timeCalendar = Calendar.getInstance().apply { time = parsedTime }
            val dayCalendar =
                Calendar.getInstance()

            dayCalendar.time = dateFormat().parse(date) ?: return null

            dayCalendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            dayCalendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
            dayCalendar.set(Calendar.SECOND, 0)
            dayCalendar.set(Calendar.MILLISECOND, 0)
            dayCalendar.time
        } catch (e: Exception) {
            null
        }
    }

    /*
     * Whole minutes from start to end (never negative).
     * A missing end means "now".
     */
    fun minutesBetween(
        start: String?,
        end: String?,
        fallbackDate: String? = null
    ): Long {

        val startDate = parse(start, fallbackDate) ?: return 0L
        val endDate = parse(end, fallbackDate) ?: Date()

        return ((endDate.time - startDate.time) / 60_000L)
            .coerceAtLeast(0L)
    }

    /*
     * Text for the screen: "21 Nov, 09:15 AM".
     */
    fun display(
        value: String?,
        fallbackDate: String? = null
    ): String {

        val date = parse(value, fallbackDate) ?: return "—"

        return SimpleDateFormat(
            "dd MMM, hh:mm a",
            Locale.getDefault()
        ).format(date)
    }

    /* 135 → "2h 15m" */
    fun formatMinutes(minutes: Long): String {
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
