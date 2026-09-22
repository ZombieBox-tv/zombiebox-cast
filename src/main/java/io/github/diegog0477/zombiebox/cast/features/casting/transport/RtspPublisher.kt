package io.github.diegog0477.zombiebox.cast.features.casting.transport

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

/** TCP interleaved RTSP avoids UDP/NAT assumptions and bounds startup waits. */
class RtspPublisher(
    private val host: String,
    private val port: Int,
    private val path: String,
    private val authorization: String,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val socket = Socket()
    private lateinit var input: BufferedInputStream
    private lateinit var output: BufferedOutputStream
    private val uri = "rtsp://${if(host.contains(':')) "[$host]" else host}:$port/$path"
    private var sequence = 1
    private var session = ""
    private val rtp = RtpH264(java.util.Random().nextInt())
    private val aac = RtpAac(java.util.Random().nextInt())
    private var audioEnabled = false
    private var videoEnabled = false
    private var audioChannel = 2
    @Volatile
    var lastMediaNanos = nanoTime()
        private set

    private var lastKeepAlive = 0L

    fun connect(
        sps: ByteArray,
        pps: ByteArray,
        audio: Boolean = false,
        base64: (ByteArray) -> String,
    ) {
        require(sps.size >= 4 && pps.isNotEmpty())
        val profile = sps.drop(1).take(3).joinToString("") { "%02x".format(it.toInt() and 255) }
        val video =
            "m=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\na=fmtp:96 packetization-mode=1;profile-level-id=$profile;sprop-parameter-sets=${base64(sps)},${base64(pps)}\r\na=control:trackID=0\r\n"
        connectTracks(video + if (audio) audioDescription(1) else "", true, audio)
    }

    fun connectAudio() = connectTracks(audioDescription(0), false, true)

    private fun audioDescription(track: Int) =
        "m=audio 0 RTP/AVP 97\r\na=rtpmap:97 MPEG4-GENERIC/44100/2\r\na=fmtp:97 streamtype=5;profile-level-id=1;mode=AAC-hbr;config=1210;SizeLength=13;IndexLength=3;IndexDeltaLength=3\r\na=control:trackID=$track\r\n"

    private fun connectTracks(tracks: String, video: Boolean, audio: Boolean) {
        require(path.matches(Regex("zombie/[a-f0-9]{32}")))
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.soTimeout = 5000
        socket.tcpNoDelay = true
        input = BufferedInputStream(socket.getInputStream())
        output = BufferedOutputStream(socket.getOutputStream())
        audioEnabled = audio
        videoEnabled = video
        audioChannel = if (video) 2 else 0
        val sdp =
            "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=Zombie Cast\r\nc=IN IP4 0.0.0.0\r\nt=0 0\r\na=control:*\r\n" +
                tracks
        request("ANNOUNCE", uri, mapOf("Content-Type" to "application/sdp"), sdp)
        val count = (if (video) 1 else 0) + (if (audio) 1 else 0)
        repeat(count) { track ->
            val channel = track * 2
            val headers =
                request(
                    "SETUP",
                    "$uri/trackID=$track",
                    mapOf(
                        "Transport" to
                            "RTP/AVP/TCP;unicast;interleaved=$channel-${channel + 1};mode=record"
                    ),
                )
            if (track == 0)
                session = headers["session"]?.substringBefore(';') ?: error("RTSP session missing")
        }
        request("RECORD", uri)
        lastKeepAlive = nanoTime()
        lastMediaNanos = lastKeepAlive
    }

    private fun keepAlive() {
        if (nanoTime() - lastKeepAlive > 15000000000L) {
            request("OPTIONS", uri)
            lastKeepAlive = nanoTime()
        }
    }

    @Synchronized
    fun frame(nals: List<ByteArray>, presentationUs: Long) {
        check(videoEnabled)
        for ((index, nal) in nals.withIndex()) for (packet in
            rtp.packets(nal, presentationUs * 90 / 1000, index == nals.lastIndex)) {
            output.write(36)
            output.write(0)
            output.write(packet.size shr 8)
            output.write(packet.size and 255)
            output.write(packet)
        }
        output.flush()
        lastMediaNanos = nanoTime()
        keepAlive()
    }

    @Synchronized
    fun audio(frame: ByteArray, presentationUs: Long) {
        check(audioEnabled)
        val packet = aac.packet(frame, presentationUs * 44100 / 1000000)
        output.write(36)
        output.write(audioChannel)
        output.write(packet.size shr 8)
        output.write(packet.size and 255)
        output.write(packet)
        output.flush()
        lastMediaNanos = nanoTime()
        keepAlive()
    }

    private fun request(
        method: String,
        target: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
    ): Map<String, String> {
        val text =
            StringBuilder(
                "$method $target RTSP/1.0\r\nCSeq: ${sequence++}\r\nAuthorization: $authorization\r\n"
            )
        if (session.isNotEmpty()) text.append("Session: $session\r\n")
        for ((key, value) in headers) text.append("$key: $value\r\n")
        val bytes = body.toByteArray(Charsets.UTF_8)
        text.append("Content-Length: ${bytes.size}\r\n\r\n")
        output.write(text.toString().toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
        val deadline = System.nanoTime() + 5000000000L
        var remaining = 1048576
        fun read(): Int {
            check(--remaining >= 0 && System.nanoTime() < deadline) { "RTSP response limit" }
            val value = input.read()
            if (value < 0) error("RTSP closed")
            return value
        }
        var first = read()
        while (first == 36) {
            read()
            val length = (read() shl 8) or read()
            repeat(length) { read() }
            first = read()
        }
        var budget = 16384
        fun line(initial: Int? = null): String {
            val value = StringBuilder()
            var next = initial ?: read()
            while (next != 10) {
                if (--budget < 0) error("RTSP headers too large")
                if (next != 13) value.append(next.toChar())
                next = read()
            }
            return value.toString()
        }
        val status = line(first).split(' ')
        val result = HashMap<String, String>()
        while (true) {
            val line = line()
            if (line.isEmpty()) break
            val split = line.indexOf(':')
            if (split > 0)
                result[line.substring(0, split).lowercase(Locale.US)] =
                    line.substring(split + 1).trim()
        }
        val length = result["content-length"]?.toInt() ?: 0
        require(length in 0..65536)
        repeat(length) { read() }
        check(status.size > 1 && status[1] == "200") { "RTSP request failed" }
        return result
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
