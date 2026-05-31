package pl.syntaxdevteam.medstock.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import pl.syntaxdevteam.medstock.ui.alerty.ringing.ReminderRingingActivity
import pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder
import java.util.Calendar

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: MedicationReminder) {
        cancel(reminder.id)
        if (!reminder.enabled) return
        val triggerAtMillis = ReminderTriggerCalculator.nextTriggerAtMillis(reminder)
        val intent = reminderPendingIntent(reminder.id, isSnooze = false)
        scheduleAlarm(triggerAtMillis, intent, reminder.id)
    }

    fun scheduleSnooze(reminderId: Long, minutes: Int = SNOOZE_MINUTES) {
        val triggerAtMillis = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        val intent = reminderPendingIntent(reminderId, isSnooze = true)
        scheduleAlarm(triggerAtMillis, intent, reminderId)
    }

    fun cancel(reminderId: Long) {
        cancelPending(reminderId.toInt(), ACTION_FIRE_REMINDER)
        cancelSnooze(reminderId)
    }

    fun cancelSnooze(reminderId: Long) {
        cancelPending(snoozeRequestCode(reminderId), ACTION_FIRE_REMINDER)
    }

    private fun reminderPendingIntent(reminderId: Long, isSnooze: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (isSnooze) snoozeRequestCode(reminderId) else reminderId.toInt(),
        Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleAlarm(triggerAtMillis: Long, operation: PendingIntent, reminderId: Long) {
        if (canScheduleExactAlarm()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            Log.i(TAG, "Scheduled exact reminder alarm id=$reminderId triggerAtMillis=$triggerAtMillis")
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, ringingPendingIntent(reminderId)),
                operation
            )
            Log.i(TAG, "Scheduled alarm-clock reminder id=$reminderId triggerAtMillis=$triggerAtMillis because exact alarm permission is unavailable")
        }
    }

    private fun ringingPendingIntent(reminderId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        reminderId.toInt(),
        ReminderRingingActivity.intent(context, reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canScheduleExactAlarm(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun cancelPending(requestCode: Int, action: String) {
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderAlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (intent != null) {
            alarmManager.cancel(intent)
            intent.cancel()
        }
    }

    fun rescheduleAll() {
        MedicationReminderRepository(context).getAll().filter { it.enabled }.forEach(::schedule)
    }

    fun exactAlarmSettingsIntent(): Intent? = null

    companion object {
        const val ACTION_FIRE_REMINDER = "pl.syntaxdevteam.medstock.action.FIRE_MEDICATION_REMINDER"
        const val ACTION_TAKE_DOSE = "pl.syntaxdevteam.medstock.action.TAKE_MEDICATION_DOSE"
        const val ACTION_SNOOZE_REMINDER = "pl.syntaxdevteam.medstock.action.SNOOZE_MEDICATION_REMINDER"
        const val ACTION_SKIP_DOSE = "pl.syntaxdevteam.medstock.action.SKIP_MEDICATION_DOSE"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val SNOOZE_MINUTES = 10
        private const val TAG = "ReminderScheduler"

        fun snoozeRequestCode(reminderId: Long): Int = reminderId.toInt() xor 0x5A5A0000
    }
}

internal object ReminderTriggerCalculator {
    private const val DAYS_IN_WEEK = 7

    fun nextTriggerAtMillis(
        reminder: MedicationReminder,
        nowMillis: Long = System.currentTimeMillis(),
        calendarFactory: () -> Calendar = { Calendar.getInstance() }
    ): Long {
        val now = calendarFactory().apply { timeInMillis = nowMillis }
        val candidate = calendarFactory().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
        }
        if (!reminder.repeatsOnAnyDay()) {
            if (!candidate.after(now)) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }
        repeat(DAYS_IN_WEEK + 1) {
            val appDay = appDayIndex(candidate.get(Calendar.DAY_OF_WEEK))
            val enabledForDay = reminder.dayMask and (1 shl appDay) != 0
            if (enabledForDay && candidate.after(now)) return candidate.timeInMillis
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        candidate.add(Calendar.DAY_OF_YEAR, 1)
        return candidate.timeInMillis
    }

    private fun appDayIndex(calendarDayOfWeek: Int): Int = when (calendarDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6
    }
}
