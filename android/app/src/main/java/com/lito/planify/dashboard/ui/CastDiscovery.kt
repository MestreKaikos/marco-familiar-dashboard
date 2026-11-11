package com.lito.planify.dashboard.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * Detecció local de receptors Google Cast mitjançant mDNS.
 * No inicia ni controla sessions Spotify: això continua depenent de Spotify Connect.
 */
internal class CastDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    val names = mutableStateListOf<String>()
    val scanning = mutableStateOf(false)
    val error = mutableStateOf("")
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        names.clear()
        error.value = ""
        val current = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { scanning.value = true }
            override fun onDiscoveryStopped(serviceType: String) { scanning.value = false }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.trim()
                if (name.isNotEmpty() && name !in names) names.add(name)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                names.remove(serviceInfo.serviceName.trim())
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
        runCatching { nsd.stopServiceDiscovery(current) }
    }
}
