package io.github.diegog0477.zombiebox.cast.features.casting.presentation.viewmodel

import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.CastGrant
import io.github.diegog0477.zombiebox.cast.features.casting.domain.model.Receiver

data class CastState(
    val receivers: List<Receiver> = emptyList(),
    val selected: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
    val grant: CastGrant? = null,
)
