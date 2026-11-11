package com.lito.planify.dashboard.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** Descobreix serveis Cast locals i resol els atributs TXT; no inicia sessions Spotify. */
internal class CastDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    val names = mutableStateListOf<String>()
    val details = mutableStateListOf<String>()
    val scanning = mutableStateOf(false)
    val error = mutableStateOf("")
    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val known = mutableSetOf<String>()
    private var resolving = false

    private fun txt(info: NsdServiceInfo, key: String): String =
        info.attributes[key]?.toString(Charsets.UTF_8)?.trim().orEmpty()

    private fun resolveNext() {
        if (resolving || listener == null || pending.isEmpty()) return
        val next = pending.removeFirst()
        resolving = true
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val service = info.serviceName.trim()
                    val friendly = txt(info, "fn").ifBlank { service }
                    val model = txt(info, "md")
                    val capabilities = txt(info, "ca")
                    val label = buildString {
                        append(friendly)
                        if (service != friendly) append(" (").append(service).append(")")
                        if (model.isNotBlank()) append(" · ").append(model)
                        if (capabilities.isNotBlank()) append(" · ca=").append(capabilities)
                    }
                    if (listener != null) {
                        details.removeAll { it.substringBefore(" · ").substringBefore(" (") == friendly }
                        details.add(label)
                        if (friendly !in names) names.add(friendly)
                    }
                    resolving = false
                    resolveNext()
                }
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    resolving = false
                    resolveNext()
                }
            })
        } catch (_: Exception) {
            resolving = false
            resolveNext()
        }
    }

    fun start() {
        if (listener != null) return
        names.clear()
        details.clear()
        known.clear()
        pending.clear()
        resolving = false
        error.value = ""
        val current = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { scanning.value = true }
            override fun onDiscoveryStopped(serviceType: String) { scanning.value = false }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.trim()
                if (name.isNotEmpty() && name !in names) names.add(name)
                if (known.add(name)) {
                    pending.addLast(serviceInfo)
                    resolveNext()
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.trim()
                names.remove(name)
                known.remove(name)
                details.removeAll { it.contains("($name)") || it.substringBefore(" · ") == name }
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                error.value = "No s'ha pogut iniciar la detecció Cast (codi $errorCode)."
                scanning.value = false
                runCatching { nsd.stopServiceDiscovery(this) }
                listener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                scanning.value = false
            }
        }
        listener = current
        try {
            nsd.discoverServices("_googlecast._tcp.", NsdManager.PROTOCOL_DNS_SD, current)
        } catch (e: Exception) {
            listener = null
            error.value = "No es pot cercar dispositius Cast: " + (e.message ?: "error desconegut")
        }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        scanning.value = false
        pending.clear()
        runCatching { nsd.stopServiceDiscovery(current) }
    }
}
