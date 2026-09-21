package io.github.diegog0477.zombiebox.cast.features.casting.domain.model

data class CastGrant(
    val id: String,
    val host: String,
    val port: Int,
    val path: String,
    val user: String,
    val token: String,
)
