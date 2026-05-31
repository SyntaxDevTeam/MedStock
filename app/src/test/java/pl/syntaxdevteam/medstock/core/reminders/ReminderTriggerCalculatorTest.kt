package pl.syntaxdevteam.medstock.core.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder
import java.util.Calendar
import java.util.TimeZone

class ReminderTriggerCalculatorTest {

    private val timeZone: TimeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun oneTimeReminderAtCurrentMinuteSchedulesTomorrow() {
        val now = millis(2026, Calendar.JUNE, 1, 19, 4)
        val reminder = reminder(hour = 19, minute = 4, dayMask = 0)

        val trigger = ReminderTriggerCalculator.nextTriggerAtMillis(reminder, now, ::calendar)

        assertEquals(millis(2026, Calendar.JUNE, 2, 19, 4), trigger)
    }

    @Test
    fun repeatingReminderTodayInFutureSchedulesToday() {
        val now = millis(2026, Calendar.JUNE, 1, 18, 55)
        val mondayMask = 1 shl 0
        val reminder = reminder(hour = 19, minute = 4, dayMask = mondayMask)

        val trigger = ReminderTriggerCalculator.nextTriggerAtMillis(reminder, now, ::calendar)

        assertEquals(millis(2026, Calendar.JUNE, 1, 19, 4), trigger)
    }

    @Test
    fun repeatingReminderAfterTodaysTimeSchedulesNextEnabledDay() {
        val now = millis(2026, Calendar.JUNE, 1, 19, 5)
        val mondayAndWednesdayMask = (1 shl 0) or (1 shl 2)
        val reminder = reminder(hour = 19, minute = 4, dayMask = mondayAndWednesdayMask)

        val trigger = ReminderTriggerCalculator.nextTriggerAtMillis(reminder, now, ::calendar)

        assertEquals(millis(2026, Calendar.JUNE, 3, 19, 4), trigger)
    }

    private fun reminder(hour: Int, minute: Int, dayMask: Int): MedicationReminder = MedicationReminder(
        id = 1L,
        hour = hour,
        minute = minute,
        dayMask = dayMask,
        enabled = true,
        label = "",
        soundName = ReminderSoundCatalog.DEFAULT_SOUND_NAME,
        medicationIds = emptyList(),
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long = calendar().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun calendar(): Calendar = Calendar.getInstance(timeZone)
}
