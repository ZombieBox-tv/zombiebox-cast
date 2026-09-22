package io.github.diegog0477.zombiebox.cast.features.mediaqueue.data

import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model.MediaQueue
import io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.repository.MediaQueueRepository
import io.github.diegog0477.zombiebox.shared.companion.CompanionTransport
import io.github.diegog0477.zombiebox.shared.companion.MediaQueueWire
import io.github.diegog0477.zombiebox.shared.companion.WireMediaQueue
import java.util.UUID

class GatewayMediaQueueRepository(base: String, device: String, token: String) :
    MediaQueueRepository {
    private val transport = CompanionTransport(base, device, token)
    private val wire = MediaQueueWire(transport)

    override fun status() = map(wire.status())

    override fun start(urls: List<String>) =
        map(wire.start(UUID.randomUUID().toString().replace("-", ""), urls))

    override fun stop() = wire.stop()

    override fun close() = transport.close()

    private fun map(value: WireMediaQueue) =
        MediaQueue(value.phase, value.index, value.count, value.title)
}
