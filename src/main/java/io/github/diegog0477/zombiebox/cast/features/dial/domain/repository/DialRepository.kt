package io.github.diegog0477.zombiebox.cast.features.dial.domain.repository

import io.github.diegog0477.zombiebox.cast.features.dial.domain.model.DialDevice

interface DialRepository {
    fun discover(): List<DialDevice>

    fun launch(device: DialDevice): Boolean

    fun close()
}
