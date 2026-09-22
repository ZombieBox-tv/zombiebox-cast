package io.github.diegog0477.zombiebox.cast.features.dial.data

import io.github.diegog0477.zombiebox.cast.features.dial.domain.model.DialDevice
import io.github.diegog0477.zombiebox.cast.features.dial.domain.repository.DialRepository
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

/** DIAL discovers/launches our Client only; it never transfers pairing credentials. */
class LanDialRepository : DialRepository {
    @Volatile private var closed = false
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var connection: HttpURLConnection? = null
    private var known = emptyList<DialDevice>()
    private val failedUntil = mutableMapOf<String, Long>()

    override fun discover(): List<DialDevice> {
        check(!closed)
        val locations = linkedMapOf<String, Pair<String, String>>()
        val request =
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n"
                .toByteArray(Charsets.US_ASCII)
        DatagramSocket().use { udp ->
            socket = udp
            udp.soTimeout = 300
            udp.send(
                DatagramPacket(
                    request,
                    request.size,
                    InetAddress.getByName("239.255.255.250"),
                    1900,
                )
            )
            val until = System.nanoTime() + 2000000000L
            var packets = 0
            while (!closed && System.nanoTime() < until && locations.size < 8 && packets < 64) {
                val packet = DatagramPacket(ByteArray(4096), 4096)
                try {
                    udp.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                packets++
                val peer = packet.address
                if (peer !is Inet4Address || !peer.isSiteLocalAddress || peer.isLoopbackAddress)
                    continue
                try {
                    val lines =
                        String(packet.data, 0, packet.length, Charsets.US_ASCII).split("\r\n")
                    require(lines.first().startsWith("HTTP/1.1 200"))
                    val headers =
                        lines
                            .drop(1)
                            .filter { it.contains(':') }
                            .associate {
                                it.substringBefore(':').trim().lowercase(java.util.Locale.US) to
                                    it.substringAfter(':').trim()
                            }
                    require(headers["st"] == "urn:dial-multiscreen-org:service:dial:1")
                    val usn = headers.getValue("usn").take(256)
                    val host = peer.hostAddress!!
                    locations[usn] =
                        host to DialDocuments.endpoint(headers.getValue("location"), host)
                } catch (_: Exception) {
                    /* Ignore malformed LAN advertisements. */
                }
            }
        }
        socket = null
        val devices = ArrayList<DialDevice>()
        for ((id, address) in locations) {
            if (closed) break
            try {
                val response = request(address.second)
                check(response.code == 200)
                val app = DialDocuments.endpoint(response.applicationURL, address.first)
                require(URI(app).query == null)
                val endpoint = app.trimEnd('/') + "/ZombieBox"
                val status = request(endpoint)
                if (
                    status.code == 200 &&
                        DialDocuments.field(status.body, "state") in listOf("running", "stopped")
                ) {
                    devices.add(
                        DialDevice(id, DialDocuments.field(response.body, "friendlyName"), endpoint)
                    )
                }
            } catch (_: Exception) {
                /* Missing/OEM-unavailable contracts remain a fallback. */
            }
        }
        known = devices.toList()
        return known
    }

    override fun launch(device: DialDevice): Boolean {
        check(!closed && known.contains(device))
        if ((failedUntil[device.id] ?: 0) > System.nanoTime()) return false
        return try {
            val response = request(device.endpoint, "screen=home")
            check(response.code in listOf(200, 201))
            var running = false
            repeat(3) {
                if (!running && !closed) {
                    val status = request(device.endpoint)
                    running =
                        status.code == 200 && DialDocuments.field(status.body, "state") == "running"
                    if (!running) Thread.sleep(300)
                }
            }
            check(running && !closed)
            failedUntil.remove(device.id)
            true
        } catch (_: Exception) {
            if (failedUntil.size >= 32) failedUntil.clear()
            failedUntil[device.id] = System.nanoTime() + 600000000000L
            false
        }
    }

    private data class Response(val code: Int, val body: ByteArray, val applicationURL: String)

    private fun request(address: String, payload: String? = null): Response {
        check(!closed)
        val http = URL(address).openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        connection = http
        try {
            check(!closed)
            http.connectTimeout = 1500
            http.readTimeout = 1500
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.setRequestProperty("Origin", "package:io.github.diegog0477.zombiebox.cast")
            if (payload != null) {
                val bytes = payload.toByteArray(Charsets.US_ASCII)
                http.requestMethod = "POST"
                http.doOutput = true
                http.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                http.setFixedLengthStreamingMode(bytes.size)
                http.outputStream.use { it.write(bytes) }
            }
            val code = http.responseCode
            val body = ByteArrayOutputStream()
            if (code in 200..299)
                http.inputStream.use { input ->
                    val buffer = ByteArray(2048)
                    val deadline = System.nanoTime() + 2000000000L
                    while (true) {
                        check(!closed && System.nanoTime() < deadline)
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(body.size() + count <= 32768)
                        body.write(buffer, 0, count)
                    }
                }
            return Response(
                code,
                body.toByteArray(),
                http.getHeaderField("Application-URL").orEmpty(),
            )
        } finally {
            http.disconnect()
            connection = null
        }
    }

    override fun close() {
        closed = true
        socket?.close()
        connection?.disconnect()
    }
}
