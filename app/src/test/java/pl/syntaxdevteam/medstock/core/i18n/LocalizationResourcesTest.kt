package pl.syntaxdevteam.medstock.core.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {

    @Test
    fun `manual language modes have complete localized resources`() {
        val baseResources = resourceFile("values/strings.xml").readAndroidResources()
        val localizedResourceFiles = mapOf(
            "pl" to resourceFile("values-pl/strings.xml"),
            "de" to resourceFile("values-de/string.xml"),
            "fr" to resourceFile("values-fr/strings.xml")
        )
        val translatedLanguageTags = AppLanguageMode.entries
            .mapNotNull(AppLanguageMode::languageTag)
            .filterNot { languageTag -> languageTag == "en" }
            .toSet()

        assertEquals(translatedLanguageTags, localizedResourceFiles.keys)
        localizedResourceFiles.forEach { (languageTag, localizedFile) ->
            assertTrue(
                "Missing localized strings for $languageTag at ${localizedFile.path}",
                localizedFile.isFile
            )

            val localizedResources = localizedFile.readAndroidResources()
            assertEquals(
                "$languageTag string resources differ from base resources",
                baseResources.strings.keys,
                localizedResources.strings.keys
            )
            assertEquals(
                "$languageTag plural resources differ from base resources",
                baseResources.plurals.keys,
                localizedResources.plurals.keys
            )
            localizedResources.plurals.forEach { (pluralName, localizedQuantities) ->
                assertTrue(
                    "$languageTag plural $pluralName must define the required other quantity",
                    localizedQuantities.containsKey("other")
                )
            }
        }
    }

    @Test
    fun `manual language resources preserve format placeholders`() {
        val baseResources = resourceFile("values/strings.xml").readAndroidResources()
        val localizedResourceFiles = mapOf(
            "pl" to resourceFile("values-pl/strings.xml"),
            "de" to resourceFile("values-de/string.xml"),
            "fr" to resourceFile("values-fr/strings.xml")
        )

        localizedResourceFiles.forEach { (languageTag, localizedFile) ->
            val localizedResources = localizedFile.readAndroidResources()
            baseResources.strings.forEach { (name, baseValue) ->
                assertEquals(
                    "$languageTag placeholders differ for string $name",
                    baseValue.formatPlaceholders(),
                    localizedResources.strings.getValue(name).formatPlaceholders()
                )
            }
            baseResources.plurals.forEach { (name, baseItems) ->
                val localizedItems = localizedResources.plurals.getValue(name)
                localizedItems.forEach { (quantity, localizedValue) ->
                    val localizedPlaceholders = localizedValue.formatPlaceholders()
                    val sameQuantityPlaceholders = baseItems[quantity]?.formatPlaceholders()
                    val otherQuantityPlaceholders = baseItems["other"]?.formatPlaceholders()
                    assertTrue(
                        "$languageTag placeholders differ for plural $name/$quantity",
                        localizedPlaceholders == sameQuantityPlaceholders ||
                            localizedPlaceholders == otherQuantityPlaceholders
                    )
                }
            }
        }
    }

    @Test
    fun `french strings do not fall back to english for key screens`() {
        val english = resourceFile("values/strings.xml").readAndroidResources().strings
        val french = resourceFile("values-fr/strings.xml").readAndroidResources().strings

        listOf(
            "action_settings",
            "menu_medication_list",
            "settings_language_title",
            "settings_palette_title",
            "reminder_editor_title",
            "account_drive_title",
            "settings_show_inactive_pharmacies"
        ).forEach { stringName ->
            assertNotEquals(
                "$stringName should have a French translation instead of English fallback",
                english.getValue(stringName),
                french.getValue(stringName)
            )
        }
    }

    private fun resourceFile(relativePath: String): File = File("src/main/res/$relativePath")

    private fun File.readAndroidResources(): AndroidResources {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
        val root = document.documentElement
        val strings = buildMap {
            val stringNodes = root.getElementsByTagName("string")
            for (index in 0 until stringNodes.length) {
                val element = stringNodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
        val plurals = buildMap {
            val pluralNodes = root.getElementsByTagName("plurals")
            for (pluralIndex in 0 until pluralNodes.length) {
                val pluralElement = pluralNodes.item(pluralIndex) as Element
                val items = buildMap {
                    val itemNodes = pluralElement.getElementsByTagName("item")
                    for (itemIndex in 0 until itemNodes.length) {
                        val itemElement = itemNodes.item(itemIndex) as Element
                        put(itemElement.getAttribute("quantity"), itemElement.textContent)
                    }
                }
                put(pluralElement.getAttribute("name"), items)
            }
        }

        return AndroidResources(strings = strings, plurals = plurals)
    }

    private fun String.formatPlaceholders(): Map<String, Int> =
        FORMAT_PLACEHOLDER_REGEX.findAll(this)
            .map { match -> match.value }
            .groupingBy { placeholder -> placeholder }
            .eachCount()

    private data class AndroidResources(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>
    )

    private companion object {
        val FORMAT_PLACEHOLDER_REGEX =
            Regex("%(?:\\d+\\$)?(?:[-#+ 0,(<]*)?(?:\\d+)?(?:\\.\\d+)?[a-zA-Z%]")
    }
}
