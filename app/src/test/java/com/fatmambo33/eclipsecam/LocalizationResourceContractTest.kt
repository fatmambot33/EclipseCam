package com.fatmambo33.eclipsecam

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourceContractTest {
    @Test
    fun frenchResourcesMatchEnglishKeysAndFormatters() {
        val english = readStrings(resourceFile("values/strings.xml"))
        val french = readStrings(resourceFile("values-fr/strings.xml"))

        assertEquals(
            "French resources must cover exactly the English translatable string set.",
            english.keys,
            french.keys,
        )

        english.keys.forEach { name ->
            assertEquals(
                "Formatter signature differs for $name.",
                formatterSignature(english.getValue(name)),
                formatterSignature(french.getValue(name)),
            )
        }
    }

    @Test
    fun resourceFilesContainNoDuplicateStringNames() {
        listOf("values/strings.xml", "values-fr/strings.xml").forEach { relativePath ->
            val file = resourceFile(relativePath)
            val names = allStringNames(file)
            assertEquals(
                "$relativePath contains duplicate string resource names.",
                names.toSet().size,
                names.size,
            )
        }
    }

    private fun resourceFile(relativePath: String): File {
        val fromModule = File("src/main/res/$relativePath")
        if (fromModule.isFile) return fromModule
        val fromRoot = File("app/src/main/res/$relativePath")
        assertTrue("Missing Android resource file: $relativePath", fromRoot.isFile)
        return fromRoot
    }

    private fun readStrings(file: File): Map<String, String> =
        stringElements(file)
            .filter { it.getAttribute("translatable").lowercase() != "false" }
            .associate { it.getAttribute("name") to it.textContent }
            .toSortedMap()

    private fun allStringNames(file: File): List<String> =
        stringElements(file).map { it.getAttribute("name") }

    private fun stringElements(file: File): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun formatterSignature(value: String): List<String> =
        FORMAT_TOKEN.findAll(value)
            .map(MatchResult::value)
            .filter { it != "%%" }
            .toList()

    private companion object {
        val FORMAT_TOKEN = Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]")
    }
}
