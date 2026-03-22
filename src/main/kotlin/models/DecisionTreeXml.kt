package models

import javax.xml.stream.XMLOutputFactory
import java.io.StringWriter

import its.model.nodes.DecisionTree
import javax.xml.parsers.DocumentBuilderFactory
import its.model.nodes.xml.DecisionTreeXMLWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Типизированная XML-модель дерева решений, совместимая с форматом tree.xml.
 * Используется для сериализации/десериализации без изменения логики ядра.
 */
data class DecisionTreeXml(
    val inputVariables: List<InputVariableXml>,
    val rootThoughtBranch: ThoughtBranchNodeXml
) {
    fun toXmlString(): String {
        val writer = StringWriter()
        val xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(writer)
        xmlWriter.writeStartDocument("UTF-8", "1.0")
        xmlWriter.writeStartElement("DecisionTree")

        // InputVariables
        xmlWriter.writeStartElement("InputVariables")
        for (v in inputVariables) {
            when (v) {
                is InputVariableXml.Simple -> {
                    xmlWriter.writeEmptyElement(v.originalTag)
                    writeAttributes(xmlWriter, v.attributes)
                }
                is InputVariableXml.WithExpression -> {
                    xmlWriter.writeStartElement(v.originalTag)
                    writeAttributes(xmlWriter, v.attributes)
                    writeRawXml(xmlWriter, v.expressionXml)
                    xmlWriter.writeEndElement()
                }
            }
        }
        xmlWriter.writeEndElement() // InputVariables

        // ThoughtBranch
        xmlWriter.writeStartElement("ThoughtBranch")
        writeAttributes(xmlWriter, rootThoughtBranch.attributes)
        writeRawXml(xmlWriter, rootThoughtBranch.childrenXml)
        xmlWriter.writeEndElement()

        xmlWriter.writeEndElement() // DecisionTree
        xmlWriter.writeEndDocument()
        xmlWriter.close()
        return writer.toString()
    }

    private fun writeAttributes(
        xmlWriter: javax.xml.stream.XMLStreamWriter,
        attrs: Map<String, String>
    ) {
        for ((k, v) in attrs) {
            if (k != "originalTag") {
                xmlWriter.writeAttribute(k, v)
            }
        }
    }

    private fun writeRawXml(
        xmlWriter: javax.xml.stream.XMLStreamWriter,
        xml: String
    ) {
        val reader = javax.xml.stream.XMLInputFactory
            .newInstance()
            .createXMLEventReader(java.io.StringReader(xml))

        while (reader.hasNext()) {
            val event = reader.nextEvent()

            when (event.eventType) {
                javax.xml.stream.events.XMLEvent.START_ELEMENT -> {
                    val start = event.asStartElement()
                    val name = start.name

                    xmlWriter.writeStartElement(name.localPart)

                    val attrs = start.attributes
                    while (attrs.hasNext()) {
                        val attr = attrs.next()
                        xmlWriter.writeAttribute(attr.name.localPart, attr.value)
                    }
                }

                javax.xml.stream.events.XMLEvent.END_ELEMENT -> {
                    xmlWriter.writeEndElement()
                }

                javax.xml.stream.events.XMLEvent.CHARACTERS -> {
                    xmlWriter.writeCharacters(event.asCharacters().data)
                }
            }
        }
    }
    companion object {
        fun fromDomain(tree: DecisionTree): DecisionTreeXml {

            // 1. сериализуем дерево в XML строку
            val xml = DecisionTreeXMLWriter.decisionTreeToXmlString(tree)

            // 2. парсим строку в DOM
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml)))

            // 3. извлекаем InputVariables
            val inputVarsEl =
                doc.documentElement.getElementsByTagName("InputVariables").item(0)
                        as org.w3c.dom.Element

            val inputVariables = parseInputVariablesFromDom(inputVarsEl)

            // 4. извлекаем ThoughtBranch
            val thoughtBranchEl =
                doc.documentElement.getElementsByTagName("ThoughtBranch").item(0)
                        as org.w3c.dom.Element

            val childrenXml = buildString {
                val nList = thoughtBranchEl.childNodes
                for (i in 0 until nList.length) {
                    val node = nList.item(i)
                    if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                        append(domElementToString(node as org.w3c.dom.Element))
                    }
                }
            }.trim()

            return DecisionTreeXml(
                inputVariables = inputVariables,
                rootThoughtBranch = ThoughtBranchNodeXml(
                    attributes = getAttributes(thoughtBranchEl),
                    childrenXml = childrenXml
                )
            )
        }

        private fun parseInputVariablesFromDom(inputVarsEl: org.w3c.dom.Element): List<InputVariableXml> {
            val vars = mutableListOf<InputVariableXml>()
            val children = inputVarsEl.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val el = node as org.w3c.dom.Element
                    val tagName = el.tagName
                    val attrs = getAttributes(el)
                    when (tagName) {
                        "DecisionTreeVarDecl" -> {
                            vars.add(InputVariableXml.Simple(
                                name = attrs["name"] ?: error("Missing name"),
                                type = attrs["type"] ?: error("Missing type"),
                                attributes = attrs,
                                originalTag = tagName
                            ))
                        }
                        "AdditionalVarDecl" -> {
                            val exprXml = domElementToString(el.getElementsByTagName("Expression").item(0) as org.w3c.dom.Element)
                            vars.add(InputVariableXml.WithExpression(
                                name = attrs["name"] ?: error("Missing name"),
                                type = attrs["type"] ?: error("Missing type"),
                                attributes = attrs,
                                originalTag = tagName,
                                expressionXml = exprXml
                            ))
                        }
                    }
                }
            }
            return vars
        }

        private fun getAttributes(el: org.w3c.dom.Element): Map<String, String> {
            val attrs = mutableMapOf<String, String>()
            val attrMap = el.attributes
            for (i in 0 until attrMap.length) {
                val attr = attrMap.item(i)
                attrs[attr.nodeName] = attr.nodeValue
            }
            return attrs
        }

        private fun domElementToString(el: org.w3c.dom.Element): String {
            val transformer = TransformerFactory.newInstance().newTransformer()
            val result = StringWriter()
            transformer.transform(DOMSource(el), StreamResult(result))
            return result.toString().trim()
        }
    }
}

sealed class InputVariableXml {
    abstract val name: String
    abstract val type: String
    abstract val attributes: Map<String, String>
    abstract val originalTag: String

    data class Simple(
        override val name: String,
        override val type: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val originalTag: String = "DecisionTreeVarDecl"
    ) : InputVariableXml()

    data class WithExpression(
        override val name: String,
        override val type: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val originalTag: String = "AdditionalVarDecl",
        val expressionXml: String
    ) : InputVariableXml()
}

data class ThoughtBranchNodeXml(
    val attributes: Map<String, String> = emptyMap(),
    val childrenXml: String = ""
)