package io.github.diegog0477.zombiebox.cast.features.mediaqueue.domain.model

import java.net.URI

data class MediaQueue(
    val phase: String = "NONE",
    val index: Int = 0,
    val count: Int = 0,
    val title: String = "",
) {
    val active
        get() = phase == "PREPARING" || phase == "PLAYING"
}

object QueueInput {
    fun parse(text: String): List<String> {
        require(text.length <= 65536)
        val urls = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(urls.size in 1..16)
        for (raw in urls) {
            val uri = URI(raw)
            require(
                raw.length <= 4096 &&
                    uri.scheme in listOf("http", "https") &&
                    !uri.host.isNullOrEmpty() &&
                    uri.userInfo == null &&
                    uri.fragment == null
            )
        }
        return urls
    }
}
