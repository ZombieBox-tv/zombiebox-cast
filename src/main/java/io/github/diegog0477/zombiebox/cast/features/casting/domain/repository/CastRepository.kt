package io.github.diegog0477.zombiebox.cast.features.casting.domain.repository

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastGrant
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver

interface CastRepository {
    fun pair(address: String, code: String)

    fun receivers(): List<Receiver>

    fun create(receiver: String): CastGrant

    fun stop(id: String)
}
