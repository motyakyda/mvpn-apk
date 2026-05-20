package com.motya.mvpn.data

import android.net.Uri
import java.net.URLDecoder

object VlessParser {
    fun parse(raw: String): VlessProfile {
        val text = raw.trim()
        require(text.startsWith("vless://", ignoreCase = true)) { "Нужна ссылка vless://" }

        val uri = Uri.parse(text)
        val id = uri.userInfo?.substringBefore(':').orEmpty()
        require(id.isNotBlank()) { "В ссылке нет UUID" }

        val host = uri.host.orEmpty()
        require(host.isNotBlank()) { "В ссылке нет адреса сервера" }

        val port = uri.port.takeIf { it > 0 } ?: 443
        val params = uri.queryParameterNames.associateWith { key -> uri.getQueryParameter(key).orEmpty() }
        val fragmentName = uri.fragment?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }.orEmpty()
        val name = fragmentName.ifBlank { "$host:$port" }

        return VlessProfile(raw = text, id = id, name = name, host = host, port = port, params = params)
    }
}
