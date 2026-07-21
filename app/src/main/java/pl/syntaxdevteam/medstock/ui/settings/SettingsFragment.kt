package pl.syntaxdevteam.medstock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.settings.DeveloperModeManager
import pl.syntaxdevteam.medstock.core.theme.AppColorPalette
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode
import pl.syntaxdevteam.medstock.core.theme.ThemeManager
import pl.syntaxdevteam.medstock.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var isRenderingUiState = false
    private var isViewStateRestored = false
    private var isPaletteRecreationPending = false

    private val exportDatabaseLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) exportDatabaseToUri(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.settingsDeveloperActions.visibility = if (DeveloperModeManager.isEnabled(requireContext())) {
            View.VISIBLE
        } else {
            View.GONE
        }

        settingsViewModel.uiState.observe(viewLifecycleOwner, ::renderUiState)

        binding.settingsThemeModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || !canHandlePreferenceChange()) return@addOnButtonCheckedListener
            val themeMode = when (checkedId) {
                R.id.settings_theme_auto_button -> AppThemeMode.AUTO
                R.id.settings_theme_on_button -> AppThemeMode.ON
                R.id.settings_theme_off_button -> AppThemeMode.OFF
                else -> return@addOnButtonCheckedListener
            }
            settingsViewModel.setThemeMode(themeMode)
        }

        binding.settingsPaletteGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || !canHandlePreferenceChange()) return@addOnButtonCheckedListener
            val colorPalette = when (checkedId) {
                R.id.settings_palette_green_button -> AppColorPalette.GREEN
                R.id.settings_palette_ocean_button -> AppColorPalette.OCEAN
                R.id.settings_palette_berry_button -> AppColorPalette.BERRY
                R.id.settings_palette_sage_button -> AppColorPalette.SAGE
                R.id.settings_palette_lavender_button -> AppColorPalette.LAVENDER
                else -> return@addOnButtonCheckedListener
            }
            if (ThemeManager.getColorPalette(requireContext()) != colorPalette) {
                settingsViewModel.setColorPalette(colorPalette)
                schedulePaletteRecreation(colorPalette)
            }
        }

        binding.settingsShowInactivePharmaciesSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (canHandlePreferenceChange()) {
                settingsViewModel.setShowInactivePharmacies(isChecked)
            }
        }

        binding.settingsLanguageModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || !canHandlePreferenceChange()) return@addOnButtonCheckedListener
            val languageMode = when (checkedId) {
                R.id.settings_language_auto_button -> AppLanguageMode.AUTO
                R.id.settings_language_pl_button -> AppLanguageMode.POLISH
                R.id.settings_language_en_button -> AppLanguageMode.ENGLISH
                R.id.settings_language_de_button -> AppLanguageMode.GERMAN
                R.id.settings_language_fr_button -> AppLanguageMode.FRENCH
                else -> return@addOnButtonCheckedListener
            }
            settingsViewModel.setLanguageMode(languageMode)
        }

        binding.settingsThemeHelp.setOnClickListener {
            showHelp(R.string.settings_theme_title, R.string.settings_theme_help_message)
        }
        binding.settingsPaletteHelp.setOnClickListener {
            showHelp(R.string.settings_palette_title, R.string.settings_palette_help_message)
        }
        binding.settingsLanguageHelp.setOnClickListener {
            showHelp(R.string.settings_language_title, R.string.settings_language_help_message)
        }
        binding.settingsForceUpdateButton.setOnClickListener {
            (activity as? pl.syntaxdevteam.medstock.MainActivity)?.triggerCatalogForceUpdate()
        }
        binding.settingsExportDatabaseButton.setOnClickListener {
            exportDatabaseLauncher.launch(getString(R.string.settings_export_database_default_filename))
        }

        return binding.root
    }

    private fun showHelp(titleResId: Int, messageResId: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(titleResId)
            .setMessage(messageResId)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        settingsViewModel.uiState.value?.let(::renderUiState)
        isViewStateRestored = true
    }

    private fun renderUiState(state: SettingsUiState) {
        isRenderingUiState = true
        try {
            setCheckedThemeMode(state.themeMode)
            setCheckedColorPalette(state.colorPalette)
            setCheckedLanguageMode(state.languageMode)
            if (binding.settingsShowInactivePharmaciesSwitch.isChecked != state.showInactivePharmacies) {
                binding.settingsShowInactivePharmaciesSwitch.isChecked = state.showInactivePharmacies
            }
        } finally {
            isRenderingUiState = false
        }
    }

    private fun canHandlePreferenceChange(): Boolean = isViewStateRestored && !isRenderingUiState

    private fun schedulePaletteRecreation(colorPalette: AppColorPalette) {
        if (isPaletteRecreationPending) return
        isPaletteRecreationPending = true
        viewLifecycleOwner.lifecycleScope.launch {
            yield()
            isPaletteRecreationPending = false
            if (
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED) &&
                ThemeManager.getColorPalette(requireContext()) == colorPalette
            ) {
                requireActivity().recreate()
            }
        }
    }

    private fun setCheckedThemeMode(themeMode: AppThemeMode) {
        val checkedButtonId = when (themeMode) {
            AppThemeMode.AUTO -> R.id.settings_theme_auto_button
            AppThemeMode.ON -> R.id.settings_theme_on_button
            AppThemeMode.OFF -> R.id.settings_theme_off_button
        }
        if (binding.settingsThemeModeGroup.checkedButtonId != checkedButtonId) {
            binding.settingsThemeModeGroup.check(checkedButtonId)
        }
    }

    private fun setCheckedColorPalette(colorPalette: AppColorPalette) {
        val checkedButtonId = when (colorPalette) {
            AppColorPalette.GREEN -> R.id.settings_palette_green_button
            AppColorPalette.OCEAN -> R.id.settings_palette_ocean_button
            AppColorPalette.BERRY -> R.id.settings_palette_berry_button
            AppColorPalette.SAGE -> R.id.settings_palette_sage_button
            AppColorPalette.LAVENDER -> R.id.settings_palette_lavender_button
        }
        if (binding.settingsPaletteGroup.checkedButtonId != checkedButtonId) {
            binding.settingsPaletteGroup.check(checkedButtonId)
        }
    }

    private fun setCheckedLanguageMode(languageMode: AppLanguageMode) {
        val checkedButtonId = when (languageMode) {
            AppLanguageMode.AUTO -> R.id.settings_language_auto_button
            AppLanguageMode.POLISH -> R.id.settings_language_pl_button
            AppLanguageMode.ENGLISH -> R.id.settings_language_en_button
            AppLanguageMode.GERMAN -> R.id.settings_language_de_button
            AppLanguageMode.FRENCH -> R.id.settings_language_fr_button
        }
        if (binding.settingsLanguageModeGroup.checkedButtonId != checkedButtonId) {
            binding.settingsLanguageModeGroup.check(checkedButtonId)
        }
    }

    override fun onDestroyView() {
        isViewStateRestored = false
        isPaletteRecreationPending = false
        super.onDestroyView()
        _binding = null
    }

    private fun exportDatabaseToUri(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val dbPath = RegistryIngestDatabaseHelper.getInstance(context).readableDatabase.path
            val sourceFile = java.io.File(dbPath)
            if (!sourceFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.settings_export_database_missing, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val wasSuccessful = runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream -> inputStream.copyTo(outputStream) }
                    true
                } ?: false
            }.getOrElse { false }

            withContext(Dispatchers.Main) {
                val message = if (wasSuccessful) {
                    R.string.settings_export_database_success
                } else {
                    R.string.settings_export_database_failed
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

}
