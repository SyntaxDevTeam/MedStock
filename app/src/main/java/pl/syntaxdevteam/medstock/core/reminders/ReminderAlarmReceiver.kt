package pl.syntaxdevteam.medstock.core.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.ui.alerty.ringing.ReminderRingingActivity

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return
        val repository = MedicationReminderRepository(context)
        val reminder = repository.findById(reminderId) ?: return
        Log.i(TAG, "Received reminder alarm id=$reminderId action=${intent.action}")

        when (intent.action) {
            ReminderScheduler.ACTION_TAKE_DOSE -> {
                repository.addDoseEvent(reminder, ReminderDoseEvent.ACTION_TAKEN)
                ReminderScheduler(context).cancelSnooze(reminderId)
                cancelNotification(context, reminderId)
                notifyRingingResolved(context, reminderId)
                return
            }

            ReminderScheduler.ACTION_SNOOZE_REMINDER -> {
                repository.addDoseEvent(reminder, ReminderDoseEvent.ACTION_SNOOZED)
                ReminderScheduler(context).scheduleSnooze(reminderId)
                cancelNotification(context, reminderId)
                notifyRingingResolved(context, reminderId)
                return
            }

            ReminderScheduler.ACTION_SKIP_DOSE -> {
                repository.addDoseEvent(reminder, ReminderDoseEvent.ACTION_SKIPPED)
                ReminderScheduler(context).cancelSnooze(reminderId)
                cancelNotification(context, reminderId)
                notifyRingingResolved(context, reminderId)
                return
            }
        }

        if (!reminder.enabled) {
            Log.i(TAG, "Ignoring disabled reminder alarm id=$reminderId")
            return
        }
        ensureChannel(context, reminder.soundName)
        showNotification(context, reminder)

        val isSnooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_SNOOZE, false)
        if (!isSnooze && reminder.repeatsOnAnyDay()) {
            ReminderScheduler(context).schedule(reminder)
        } else if (!isSnooze && !reminder.repeatsOnAnyDay()) {
            repository.setEnabled(reminder.id, false)
        }
    }

    private fun showNotification(context: Context, reminder: pl.syntaxdevteam.medstock.ui.alerty.reminders.MedicationReminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            openFullScreenActivity(context, reminder.id)
            return
        }
        val medicationNames = reminder.medications.joinToString { it.name }.ifBlank {
            context.getString(R.string.reminder_notification_no_medications)
        }
        val title = reminder.label.ifBlank { context.getString(R.string.reminder_notification_title) }
        val fullScreenIntent = ringingPendingIntent(context, reminder.id)
        val notification = NotificationCompat.Builder(context, channelId(reminder.soundName))
            .setSmallIcon(R.drawable.ic_alarm_black_24dp)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_notification_message, medicationNames))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.reminder_notification_message, medicationNames)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(customSoundUri(context, reminder.soundName))
            .setVibrate(VIBRATION_PATTERN)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(
                R.drawable.ic_alarm_black_24dp,
                context.getString(R.string.reminder_action_taken),
                actionPendingIntent(context, reminder.id, ReminderScheduler.ACTION_TAKE_DOSE, 101)
            )
            .addAction(
                R.drawable.ic_alarm_black_24dp,
                context.getString(R.string.reminder_action_snooze),
                actionPendingIntent(context, reminder.id, ReminderScheduler.ACTION_SNOOZE_REMINDER, 102)
            )
            .addAction(
                R.drawable.ic_alarm_black_24dp,
                context.getString(R.string.reminder_action_skip),
                actionPendingIntent(context, reminder.id, ReminderScheduler.ACTION_SKIP_DOSE, 103)
            )
            .build()
        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
    }

    private fun openFullScreenActivity(context: Context, reminderId: Long) {
        runCatching {
            context.startActivity(ReminderRingingActivity.intent(context, reminderId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error ->
            Log.w(TAG, "Unable to open ringing activity for reminder id=$reminderId", error)
        }
    }

    private fun ringingPendingIntent(context: Context, reminderId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        reminderId.toInt(),
        ReminderRingingActivity.intent(context, reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionPendingIntent(context: Context, reminderId: Long, action: String, offset: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt() + offset,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannel(context: Context, soundName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId(soundName),
            context.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_notification_channel_description)
            setSound(
                customSoundUri(context, soundName),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun cancelNotification(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    private fun notifyRingingResolved(context: Context, reminderId: Long) {
        context.sendBroadcast(Intent(ACTION_RINGING_RESOLVED).apply {
            setPackage(context.packageName)
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        })
    }

    companion object {
        const val ACTION_RINGING_RESOLVED = "pl.syntaxdevteam.medstock.action.RINGING_RESOLVED"
        private const val TAG = "ReminderAlarmReceiver"
        private const val CHANNEL_ID_PREFIX = "medication_reminders_alarm"
        private val VIBRATION_PATTERN = longArrayOf(0L, 500L, 300L, 500L, 1_000L)

        fun customSoundUri(context: Context, soundName: String? = ReminderSoundCatalog.DEFAULT_SOUND_NAME): Uri = ReminderSoundCatalog.soundUri(context, soundName)

        fun channelId(soundName: String?): String = "$CHANNEL_ID_PREFIX.${soundName ?: ReminderSoundCatalog.DEFAULT_SOUND_NAME}"
    }
}
