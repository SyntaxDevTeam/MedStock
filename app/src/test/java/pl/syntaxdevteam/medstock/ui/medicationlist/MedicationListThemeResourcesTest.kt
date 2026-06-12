package pl.syntaxdevteam.medstock.ui.medicationlist

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationListThemeResourcesTest {

    @Test
    fun `medication cards use active palette surface instead of fixed status backgrounds`() {
        val source = File(
            "src/main/java/pl/syntaxdevteam/medstock/ui/medicationlist/MedicationListFragment.kt"
        ).readText()

        assertTrue(source.contains("MaterialColors.getColor(card, R.attr.medColorSurfaceCardSoft)"))
        assertTrue(source.contains("MaterialColors.getColor(card, R.attr.medColorPrimary)"))
        assertTrue(source.contains("MaterialColors.layer("))
        assertTrue(source.contains("STATUS_CONTAINER_OVERLAY_ALPHA = 0.07f"))
        assertFalse(source.contains("R.color.stock_status_ok_background"))
        assertFalse(source.contains("R.color.stock_status_low_background"))
        assertFalse(source.contains("R.color.stock_status_empty_background"))

        listOf("values", "values-night").forEach { qualifier ->
            val colors = File("src/main/res/$qualifier/colors.xml").readText()
            assertFalse(colors.contains("stock_status_ok_stroke"))
            assertFalse(colors.contains("stock_status_ok_background"))
        }
    }

    @Test
    fun `medication card text follows active palette`() {
        val layout = File("src/main/res/layout/item_user_medication.xml").readText()

        assertTrue(layout.contains("android:textColor=\"?attr/medColorTextPrimary\""))
        assertTrue(layout.contains("android:textColor=\"?attr/medColorTextSecondary\""))
    }
}
