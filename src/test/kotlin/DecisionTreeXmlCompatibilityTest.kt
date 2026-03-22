import org.junit.jupiter.api.Test
import javax.xml.stream.XMLInputFactory
import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import its.model.nodes.xml.DecisionTreeXMLBuilder
import its.model.nodes.xml.DecisionTreeXMLWriter
import models.DecisionTreeXmlParser
import models.DecisionTreeXml
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import org.xml.sax.InputSource;
import java.io.StringWriter;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

class DecisionTreeXmlCompatibilityTest {

    @Test
    fun `Round-trip XML parsing, serialization and comparison`() {
        val originalXml = this::class.java.getResource("/tree.xml")!!.readText().trim()

        // 1. Десериализация (Старый способ)
        val originalTree = DecisionTreeXMLBuilder.fromXMLString(originalXml)

        // 2. Десериализация (Новый способ)
        val reader = XMLInputFactory.newInstance().createXMLEventReader(StringReader(originalXml))
        val xmlModel = DecisionTreeXmlParser.parse(reader)
        val regeneratedXml = xmlModel.toXmlString().trim()

        // 3. Сравнение объектов (Старый парсер на исходном и новом XML)
        val regeneratedTree = DecisionTreeXMLBuilder.fromXMLString(regeneratedXml)
        assertEquals(
            originalTree.toString().trim(),
            regeneratedTree.toString().trim(),
            "Объекты DecisionTree, полученные старым парсером из исходного и регенерированного XML, должны совпадать"
        )

        // 4. Сериализация (Старый способ)
        val oldSerializedXml = DecisionTreeXMLWriter.decisionTreeToXmlString(originalTree).trim()

        // 5. Сериализация (Новый способ)
        val xmlFromOldTree = DecisionTreeXml.fromDomain(originalTree)
        val newSerializedFromOldTreeXml = xmlFromOldTree.toXmlString().trim()

        // 6. Сравнение XML
        assertEquals(
            canon(originalXml),
            canon(oldSerializedXml),
            "Старая сериализация должна совпадать с исходной XML"
        )

        assertEquals(
            canon(originalXml),
            canon(newSerializedFromOldTreeXml),
            "Новая сериализация должна совпадать с исходной XML"
        )

        assertEquals(
            canon(oldSerializedXml),
            canon(newSerializedFromOldTreeXml),
            "Старая и новая сериализации должны совпадать"
        )
    }

    // Вспомогательная функция для нормализации XML
    private fun normalizeXml(xml: String): String {
        return xml
            .replace("\\s+".toRegex(), " ")
            .replace(">\\s+<".toRegex(), "><")
            .replace("\\s+/>".toRegex(), "/>")
            .trim()
    }

    fun canon(xml: String): String {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true

        val doc = dbf.newDocumentBuilder()
            .parse(org.xml.sax.InputSource(StringReader(xml)))

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty("omit-xml-declaration", "yes")
        transformer.setOutputProperty("indent", "no")

        val sw = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(sw))

        return sw.toString()
            .replace(">\\s+<".toRegex(), "><")  // убираем переносы между тегами
            .trim()
    }
}