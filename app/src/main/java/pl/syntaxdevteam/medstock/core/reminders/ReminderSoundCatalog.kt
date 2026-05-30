package pl.syntaxdevteam.medstock.core.reminders

import android.content.Context
import android.net.Uri
import pl.syntaxdevteam.medstock.R
import java.util.Locale

object ReminderSoundCatalog {
    const val DEFAULT_SOUND_NAME = "dzwonki"

    data class ReminderSound(
        val name: String,
        val resId: Int,
        val label: String,
    )

    fun sounds(context: Context): List<ReminderSound> {
        val resources = context.resources
        val packageName = context.packageName
        return R.raw::class.java.fields
            .mapNotNull { field ->
                val resId = field.getInt(null).takeIf { it != 0 } ?: return@mapNotNull null
                val name = resources.getResourceEntryName(resId)
                ReminderSound(name = name, resId = resId, label = name.toSoundLabel())
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .ifEmpty {
                listOf(ReminderSound(DEFAULT_SOUND_NAME, resources.getIdentifier(DEFAULT_SOUND_NAME, "raw", packageName), DEFAULT_SOUND_NAME.toSoundLabel()))
            }
    }

    fun selectedSound(context: Context, requestedName: String?): ReminderSound {
        val allSounds = sounds(context)
        return allSounds.firstOrNull { it.name == requestedName }
            ?: allSounds.firstOrNull { it.name == DEFAULT_SOUND_NAME }
            ?: allSounds.first()
    }

    fun soundUri(context: Context, soundName: String?): Uri {
        val sound = selectedSound(context, soundName)
        return Uri.parse("android.resource://${context.packageName}/${sound.resId}")
    }

    private fun String.toSoundLabel(): String = split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
        .ifBlank { this }
}
