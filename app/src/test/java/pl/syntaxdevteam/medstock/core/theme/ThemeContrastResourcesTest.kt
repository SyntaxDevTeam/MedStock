package pl.syntaxdevteam.medstock.core.theme

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ThemeContrastResourcesTest {

    @Test
    fun `button content colors meet WCAG AA contrast in every theme and palette`() {
        listOf("values", "values-night").forEach { qualifier ->
            val colors = readColors(File("src/main/res/$qualifier/colors.xml"))
            val styles = readStyles(File("src/main/res/$qualifier/themes.xml"))

            themeNames.forEach { themeName ->
                val theme = styles.getValue(themeName)
                assertContrast(
                    qualifier = qualifier,
                    themeName = themeName,
                    background = resolveColor(theme.getValue("medColorPrimary"), theme, colors),
                    foreground = resolveColor(theme.getValue("medColorOnPrimary"), theme, colors),
                    role = "primary button"
                )
                assertContrast(
                    qualifier = qualifier,
                    themeName = themeName,
                    background = resolveColor(theme.getValue("medColorPrimarySoft"), theme, colors),
                    foreground = resolveColor(theme.getValue("medColorOnPrimarySoft"), theme, colors),
                    role = "support button"
                )
            }
        }
    }

    private fun assertContrast(
        qualifier: String,
        themeName: String,
        background: Int,
        foreground: Int,
        role: String
    ) {
        val ratio = contrastRatio(background, foreground)
        assertTrue(
            "$qualifier/$themeName has $ratio:1 contrast for $role; expected at least 4.5:1",
            ratio >= MINIMUM_NORMAL_TEXT_CONTRAST
        )
    }

    private fun readColors(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return document.getElementsByTagName("color").asElements().associate { element ->
            element.getAttribute("name") to element.textContent.trim()
        }
    }

    private fun readStyles(file: File): Map<String, Map<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val declaredStyles = document.getElementsByTagName("style").asElements().associate { style ->
            style.getAttribute("name") to style.getElementsByTagName("item").asElements().associate { item ->
                item.getAttribute("name") to item.textContent.trim()
            }
        }
        val baseTheme = declaredStyles.getValue("Theme.MedStock")
        return declaredStyles.mapValues { (name, items) ->
            if (name == "Theme.MedStock") items else baseTheme + items
        }
    }

    private fun resolveColor(
        value: String,
        theme: Map<String, String>,
        colors: Map<String, String>
    ): Int = when {
        value.startsWith("#") -> value.takeLast(6).toInt(16)
        value.startsWith("@color/") -> resolveColor(colors.getValue(value.substringAfter("@color/")), theme, colors)
        value.startsWith("?attr/") -> resolveColor(theme.getValue(value.substringAfter("?attr/")), theme, colors)
        else -> error("Unsupported color value: $value")
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).map { item(it) as Element }

    private companion object {
        const val MINIMUM_NORMAL_TEXT_CONTRAST = 4.5
        val themeNames = listOf(
            "Theme.MedStock",
            "Theme.MedStock.Ocean.NoActionBar",
            "Theme.MedStock.Berry.NoActionBar",
            "Theme.MedStock.Sage.NoActionBar",
            "Theme.MedStock.Lavender.NoActionBar"
        )
    }
}
