package com.nndd_re.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object NetworkDiscovery {
    suspend fun scan(context: Context, port: Int = 12345): List<String> = withContext(Dispatchers.IO) {
        val localIp = NetworkInterface.getNetworkInterfaces()
            ?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.filterIsInstance<Inet4Address>()
            ?.filterNot { it.isLoopbackAddress }
            ?.firstOrNull()
            ?: return@withContext emptyList()

        val parts = localIp.hostAddress?.split(".") ?: return@withContext emptyList()
        if (parts.size != 4) return@withContext emptyList()
        val base = "${parts[0]}.${parts[1]}.${parts[2]}"

        coroutineScope {
            (1..254).map { i ->
                async {
                    val candidate = "$base.$i"
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(candidate, port), 500)
                        socket.close()
                        candidate
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
