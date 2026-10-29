package com.nndd_re.util

import java.net.URI

/** 接続先 (scheme / host / port)。REのQRやURL貼り付けから組み立てる */
data class ServerAddress(val scheme: String, val host: String, val port: Int) {
    companion object {
        /**
         * `http://192.168.0.5:12345/library` / `https://pc.tailnet.ts.net/library` / `192.168.0.5:12345` / `192.168.0.5`
         * のいずれも受け付ける。解釈できなければ null。
         */
        fun parse(raw: String, defaultPort: Int = 12345): ServerAddress? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            val hasScheme = s.contains("://")
            val uri = runCatching { URI(if (hasScheme) s else "http://$s") }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = when {
                uri.port > 0 -> uri.port
                scheme == "https" -> 443
                hasScheme -> 80 // REのURLはポート省略時に既定ポートを指す
                else -> defaultPort
            }
            return ServerAddress(scheme, host, port)
        }
    }
}
