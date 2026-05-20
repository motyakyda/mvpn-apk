package com.motya.mvpn.data

data class VlessProfile(
    val raw: String,
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val params: Map<String, String>,
) {
    val transport: String get() = params["type"].orEmpty().ifBlank { "tcp" }
    val security: String get() = params["security"].orEmpty().ifBlank { "none" }
}
