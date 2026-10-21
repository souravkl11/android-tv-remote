package com.ryzen.tvremote.protocol

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String
)

class DiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryManager"
        const val SERVICE_TYPE_V2 = "_androidtvremote2._tcp."
        const val SERVICE_TYPE_V1 = "_androidtvremote._tcp."
        const val SERVICE_TYPE_CAST = "_googlecast._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var activeListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (_isScanning.value) return

        try {
            multicastLock = wifiManager?.createMulticastLock("TVRemoteMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName} (${service.serviceType})")
                resolveService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                _discoveredDevices.value = _discoveredDevices.value.filterNot {
                    it.name == service.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: Error code: $errorCode")
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: Error code: $errorCode")
                _isScanning.value = false
            }
        }

        activeListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE_V2, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovering services: ${e.message}", e)
            _isScanning.value = false
        }
    }

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        synchronized(this) {
            resolveQueue.addLast(serviceInfo)
            processNextResolve()
        }
    }

    private fun processNextResolve() {
        synchronized(this) {
            if (isResolving || resolveQueue.isEmpty()) return
            val next = resolveQueue.removeFirst()
            isResolving = true

            try {
                nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: error $errorCode")
                        synchronized(this@DiscoveryManager) {
                            isResolving = false
                            processNextResolve()
                        }
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        try {
                            val host = resolved.host?.hostAddress
                            val port = resolved.port
                            val cleanName = resolved.serviceName.replace("\\032", " ").trim()

                            if (!host.isNullOrEmpty()) {
                                val device = DiscoveredDevice(
                                    name = cleanName,
                                    host = host,
                                    port = port,
                                    serviceType = resolved.serviceType
                                )

                                val currentList = _discoveredDevices.value.toMutableList()
                                val existingIndex = currentList.indexOfFirst { it.host == host }
                                if (existingIndex >= 0) {
                                    currentList[existingIndex] = device
                                } else {
                                    currentList.add(device)
                                }
                                _discoveredDevices.value = currentList
                                Log.i(TAG, "Resolved Android TV: $cleanName at $host:$port")
                            }
                        } finally {
                            synchronized(this@DiscoveryManager) {
                                isResolving = false
                                processNextResolve()
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Error initiating resolveService: ${e.message}")
                isResolving = false
                processNextResolve()
            }
        }
    }

    fun stopDiscovery() {
        activeListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop service discovery: ${e.message}")
            }
            activeListener = null
        }

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null

        synchronized(this) {
            resolveQueue.clear()
            isResolving = false
        }
        _isScanning.value = false
    }
}
