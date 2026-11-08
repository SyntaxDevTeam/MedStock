package pl.syntaxdevteam.medstock

import android.app.Application
import pl.syntaxdevteam.medstock.core.account.DriveBackupScheduler
import pl.syntaxdevteam.medstock.core.i18n.LocaleManager
import pl.syntaxdevteam.medstock.core.theme.ThemeManager
import pl.syntaxdevteam.medstock.core.stock.StockAlertWorker

class MedStockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStoredTheme(this)
        LocaleManager.applyStoredLanguage(this)
        DriveBackupScheduler.reconcile(this)
        StockAlertWorker.schedule(this)
    }
}
