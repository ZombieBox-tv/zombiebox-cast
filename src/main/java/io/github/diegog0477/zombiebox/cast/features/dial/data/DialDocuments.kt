package io.github.diegog0477.zombiebox.cast.features.dial.data

import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/** Numeric, same-host HTTP only: SSDP advertisements cannot redirect control elsewhere. */
object DialDocuments {
    fun endpoint(raw: String, address: String): String {
        require(raw.length <= 1024)
        val uri = URI(raw)
        require(
            uri.scheme == "http" &&
                uri.host == address &&
                uri.userInfo == null &&
                uri.fragment == null
        )
        require(uri.port == -1 || uri.port in 1..65535)
        return uri.toASCIIString()
    }

    fun field(bytes: ByteArray, name: String): String {
        require(bytes.size <= 32768 && bytes.none { it == 0.toByte() })
        val xml = bytes.toString(Charsets.UTF_8)
        // No DTD/entity/CDATA processing. Ordinary XML text and escaped characters suffice.
        require(!xml.contains("<!"))
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw IllegalArgumentException("External XML entity") }
        val nodes = builder.parse(ByteArrayInputStream(bytes)).getElementsByTagNameNS("*", name)
        require(nodes.length == 1)
        return nodes.item(0).textContent.trim().filter { !it.isISOControl() }.take(120)
    }
}
