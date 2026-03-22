package models

import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent
import java.io.StringWriter
import javax.xml.stream.XMLOutputFactory

object DecisionTreeXmlParser {

    fun parse(reader: XMLEventReader): DecisionTreeXml {
        var startElement: StartElement? = null
        while (reader.hasNext()) {
            val event = reader.nextEvent()
            if (event.isStartElement) {
                startElement = event.asStartElement()
                if (startElement.name.localPart == "DecisionTree") {
                    break
                } else {
                    error("Expected root element <DecisionTree>, got <${startElement.name.localPart}>")
                }
            }
        }

        if (startElement == null) {
            error("No root element found")
        }

        var inputVariables: MutableList<InputVariableXml> = mutableListOf()
        var rootThoughtBranch: ThoughtBranchNodeXml? = null

        while (reader.hasNext()) {
            val event = reader.peek()
            if (event.isStartElement) {
                val name = event.asStartElement().name.localPart
                when (name) {
                    "InputVariables" -> {
                        inputVariables = parseInputVariables(reader)
                    }
                    "ThoughtBranch" -> {
                        rootThoughtBranch = parseThoughtBranch(reader)
                    }
                    else -> reader.nextEvent() // skip unknown
                }
            } else if (event.isEndElement && event.asEndElement().name.localPart == "DecisionTree") {
                reader.nextEvent()
                break
            } else {
                reader.nextEvent()
            }
        }

        return DecisionTreeXml(
            inputVariables = inputVariables,
            rootThoughtBranch = rootThoughtBranch
                ?: error("Root <ThoughtBranch> is missing")
        )
    }

    private fun parseInputVariables(reader: XMLEventReader): MutableList<InputVariableXml> {
        expectStartElement(reader, "InputVariables")
        val vars = mutableListOf<InputVariableXml>()

        while (reader.hasNext()) {
            val event = reader.peek()
            if (event.isEndElement && event.asEndElement().name.localPart == "InputVariables") {
                reader.nextEvent()
                break
            }
            if (event.isStartElement) {
                val start = event.asStartElement()
                val tagName = start.name.localPart
                reader.nextEvent()
                when (tagName) {
                    "DecisionTreeVarDecl" -> {
                        val attrs = readAttributes(start)
                        vars.add(InputVariableXml.Simple(
                            name = attrs["name"] ?: error("Missing 'name' in $tagName"),
                            type = attrs["type"] ?: error("Missing 'type' in $tagName"),
                            attributes = attrs,
                            originalTag = tagName
                        ))
                        reader.nextTag() // </DecisionTreeVarDecl>
                    }
                    "AdditionalVarDecl" -> {
                        val attrs = readAttributes(start)
                        val exprXml = readSubtreeContent(reader)
                        vars.add(InputVariableXml.WithExpression(
                            name = attrs["name"] ?: error("Missing 'name' in $tagName"),
                            type = attrs["type"] ?: error("Missing 'type' in $tagName"),
                            attributes = attrs,
                            originalTag = tagName,
                            expressionXml = exprXml
                        ))
                    }
                    else -> {
                        // skip unknown input var type
                        skipSubtree(reader, tagName)
                    }
                }
            } else {
                reader.nextEvent()
            }
        }
        return vars
    }

    private fun parseThoughtBranch(reader: XMLEventReader): ThoughtBranchNodeXml {
        val start = expectStartElement(reader, "ThoughtBranch")
        val attrs = readAttributes(start)
        val innerXml = readSubtreeContent(reader)
        return ThoughtBranchNodeXml(attrs, innerXml)
    }

    // Вспомогательные функции

    private fun expectStartElement(reader: XMLEventReader, expectedName: String): StartElement {
        val event = reader.nextEvent()
        if (event.isStartElement) {
            val start = event.asStartElement()
            if (start.name.localPart == expectedName) return start
        }
        error("Expected <$expectedName>, got $event")
    }

    private fun readAttributes(start: StartElement): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val attrs = start.attributes
        while (attrs.hasNext()) {
            val attr = attrs.next()
            map[attr.name.localPart] = attr.value
        }
        return map
    }

    private fun readSubtreeContent(reader: XMLEventReader): String {
        val writer = StringWriter()
        val xmlOut = XMLOutputFactory.newInstance().createXMLStreamWriter(writer)

        var depth = 1
        while (reader.hasNext() && depth > 0) {
            val event = reader.nextEvent()
            when (event.eventType) {
                XMLEvent.START_ELEMENT -> {
                    val start = event.asStartElement()
                    val ns = start.name.namespaceURI
                    val local = start.name.localPart
                    if (ns.isEmpty()) {
                        xmlOut.writeStartElement(local)
                    } else {
                        xmlOut.writeStartElement(start.name.prefix, local, ns)
                    }
                    val attrs = start.attributes
                    while (attrs.hasNext()) {
                        val attr = attrs.next()
                        val attrNs = attr.name.namespaceURI
                        if (attrNs.isEmpty()) {
                            xmlOut.writeAttribute(attr.name.localPart, attr.value)
                        } else {
                            xmlOut.writeAttribute(attr.name.prefix, attrNs, attr.name.localPart, attr.value)
                        }
                    }
                    depth++
                }
                XMLEvent.END_ELEMENT -> {
                    depth--
                    if (depth > 0) {
                        xmlOut.writeEndElement()
                    }
                }
                XMLEvent.CHARACTERS -> {
                    xmlOut.writeCharacters(event.asCharacters().data)
                }
                XMLEvent.COMMENT -> {
                }
            }
        }
        xmlOut.close()
        return writer.toString().trim { it <= ' ' }
    }

    private fun skipSubtree(reader: XMLEventReader, tagName: String) {
        var depth = 1
        while (reader.hasNext() && depth > 0) {
            val event = reader.nextEvent()
            if (event.isStartElement) depth++
            else if (event.isEndElement) depth--
        }
    }
}