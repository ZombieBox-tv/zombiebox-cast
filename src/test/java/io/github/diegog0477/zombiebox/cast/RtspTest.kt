package io.github.diegog0477.zombiebox.cast

import java.io.DataInputStream
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RtspTest {
    @Test
    fun publishesVideoAndAudioOverAuthenticatedInterleavedTracks() {
        val listener = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val future =
            executor.submit<Boolean> {
                listener.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = DataInputStream(socket.getInputStream())
                    val output = socket.getOutputStream()
                    for (expected in listOf("ANNOUNCE", "SETUP", "SETUP", "RECORD")) {
                        val line = input.readLine()
                        assertTrue(line.startsWith(expected + " "))
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val header = input.readLine()
                            if (header.isEmpty()) break
                            headers[header.substringBefore(':').lowercase()] =
                                header.substringAfter(':').trim()
                        }
                        assertEquals("Basic test", headers["authorization"])
                        val body = ByteArray(headers.getValue("content-length").toInt())
                        input.readFully(body)
                        if (expected == "ANNOUNCE") {
                            assertTrue(String(body).contains("MPEG4-GENERIC/44100/2"))
                            assertTrue(String(body).contains("config=1210"))
                        }
                        output.write(
                            "RTSP/1.0 200 OK\r\nCSeq: ${headers.getValue("cseq")}\r\nSession: test;timeout=30\r\nContent-Length: 0\r\n\r\n"
                                .toByteArray()
                        )
                        output.flush()
                    }
                    for (channel in listOf(0, 2)) {
                        assertEquals(36, input.readUnsignedByte())
                        assertEquals(channel, input.readUnsignedByte())
                        val packet = ByteArray(input.readUnsignedShort())
                        input.readFully(packet)
                        assertEquals(if (channel == 0) 96 else 97, packet[1].toInt() and 127)
                        if (channel == 2) {
                            assertEquals(16, packet[13].toInt())
                            assertEquals(
                                3,
                                ((packet[14].toInt() and 255) shl 5) or
                                    ((packet[15].toInt() and 255) shr 3),
                            )
                        }
                    }
                    true
                }
            }
        val publisher =
            RtspPublisher("127.0.0.1", listener.localPort, "zombie/" + "a".repeat(32), "Basic test")
        try {
            publisher.connect(byteArrayOf(0x67, 0x42, 0, 0x1f), byteArrayOf(0x68, 0), true) {
                Base64.getEncoder().encodeToString(it)
            }
            publisher.frame(listOf(byteArrayOf(0x65, 1, 2)), 1000000)
            publisher.audio(byteArrayOf(1, 2, 3), 1000000)
            assertTrue(future.get(5, TimeUnit.SECONDS))
        } finally {
            publisher.close()
            listener.close()
            executor.shutdownNow()
        }
    }
}
