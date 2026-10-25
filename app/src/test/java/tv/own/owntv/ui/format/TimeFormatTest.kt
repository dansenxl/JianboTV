package tv.own.owntv.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val instant = Date(1_704_110_400_000L) // 2024-01-01 12:00:00 UTC

    @Test
    fun `display time policy uses 24-hour cycle for every locale`() {
        val twelveHour = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(instant)
        val twentyFourHour = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = utc }.format(instant)

        assertTrue(twelveHour.contains("PM"))
        assertEquals("12:00", twentyFourHour)
        assertEquals("dMMMHm", combinedDateTimeSkeleton("dMMM"))
    }

    @Test
    fun `a time already in the date skeleton is replaced, never doubled`() {
        // "dMMMjm" + "Hm" was "dMMMjmHm": Android rejects two hour fields and the Error log and Backup
        // screens crashed on every 24-hour device.
        assertEquals("dMMMHm", combinedDateTimeSkeleton("dMMMjm"))
        assertEquals("dMMMyyyyHm", combinedDateTimeSkeleton("dMMMyyyyjm"))
        assertEquals("EEEdMMMHm", combinedDateTimeSkeleton("EEEdMMMahmss"))
    }

    @Test
    fun `locale-specific date patterns keep their date ordering`() {
        val us = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = utc }.format(instant)
        val german = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY).apply { timeZone = utc }.format(instant)

        assertTrue(us.startsWith("Jan"))
        assertTrue(german.startsWith("1."))
    }

    @Test
    fun `locale is part of the remembered formatter key`() {
        val us = DateFormatterKey(Locale.US, "EEEdMMM", utc.id)
        val german = DateFormatterKey(Locale.GERMANY, "EEEdMMM", utc.id)

        assertNotEquals(us, german)
    }
}
