package com.motya.mvpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState { Disconnected, Connecting, Connected, Error }

data class VpnState(
    val state: ConnectionState = ConnectionState.Disconnected,
    val message: String = "Готов к милому туннелю",
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
)

object VpnStatus {
    private val _state = MutableStateFlow(VpnState())
    val state = _state.asStateFlow()

    fun update(value: VpnState) {
        _state.value = value
    }
}
