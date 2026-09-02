package mihon.reader.source

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object SecureXml {
    private const val XINCLUDE_NAMESPACE = "http://www.w3.org/2001/XInclude"

    fun parse(documentName: String, input: InputStream): Document {
        val bytes = readBounded(documentName, input)
        val textPrefix = bytes.toString(Charsets.UTF_8).uppercase()
        if ("<!DOCTYPE" in textPrefix || "<!ENTITY" in textPrefix) {
            throw ReaderFailure.XmlRejected(documentName)
        }
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { publicId, systemId ->
                    throw SAXException("External entity resolution blocked: $publicId $systemId")
                }
            }
            builder.parse(InputSource(ByteArrayInputStream(bytes))).also {
                validateTree(documentName, it)
            }
        } catch (error: ReaderFailure) {
            throw error
        } catch (error: Throwable) {
            throw ReaderFailure.XmlRejected(documentName, error)
        }
    }

    private fun readBounded(documentName: String, input: InputStream): ByteArray {
        input.use {
            val maximum = ReaderLimits.MAX_XML_BYTES
            val bytes = it.readNBytes((maximum + 1).toInt())
            if (bytes.size.toLong() > maximum) {
                throw ReaderFailure.LimitExceeded("XML bytes for $documentName", maximum, bytes.size.toLong())
            }
            return bytes
        }
    }

    private fun validateTree(documentName: String, document: Document) {
        if (document.doctype != null) throw ReaderFailure.XmlRejected(documentName)
        val root = document.documentElement ?: throw ReaderFailure.XmlRejected(documentName)
        val stack = ArrayDeque<Pair<Node, Int>>()
        stack.add(root to 1)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (depth > ReaderLimits.MAX_XML_DEPTH) throw ReaderFailure.XmlRejected(documentName)
            if (node is Element) {
                if (node.namespaceURI == XINCLUDE_NAMESPACE && node.localName == "include") {
                    throw ReaderFailure.XmlRejected(documentName)
                }
                for (index in 0 until node.attributes.length) {
                    val attribute = node.attributes.item(index)
                    if (attribute.localName == "schemaLocation" || attribute.localName == "noNamespaceSchemaLocation") {
                        throw ReaderFailure.XmlRejected(documentName)
                    }
                }
            }
            for (index in 0 until node.childNodes.length) {
                val child = node.childNodes.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) stack.add(child to depth + 1)
            }
        }
    }
}
