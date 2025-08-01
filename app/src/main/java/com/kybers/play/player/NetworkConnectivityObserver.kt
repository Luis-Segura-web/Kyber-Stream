package com.kybers.play.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network connectivity observer to handle connection changes during playback
 */
class NetworkConnectivityObserver(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkConnectivity"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _networkType = MutableStateFlow(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()
    
    private var onNetworkLost: (() -> Unit)? = null
    private var onNetworkAvailable: (() -> Unit)? = null
    private var onNetworkChanged: ((NetworkType) -> Unit)? = null
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available")
            updateNetworkStatus()
            onNetworkAvailable?.invoke()
        }
        
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost")
            _isConnected.value = false
            _networkType.value = NetworkType.NONE
            onNetworkLost?.invoke()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val previousType = _networkType.value
            updateNetworkStatus()
            val currentType = _networkType.value
            
            if (previousType != currentType) {
                Log.d(TAG, "Network type changed from $previousType to $currentType")
                onNetworkChanged?.invoke(currentType)
            }
        }
    }
    
    enum class NetworkType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        OTHER
    }
    
    /**
     * Set network event callbacks
     */
    fun setNetworkCallbacks(
        onNetworkLost: () -> Unit,
        onNetworkAvailable: () -> Unit,
        onNetworkChanged: (NetworkType) -> Unit
    ) {
        this.onNetworkLost = onNetworkLost
        this.onNetworkAvailable = onNetworkAvailable
        this.onNetworkChanged = onNetworkChanged
    }
    
    /**
     * Start monitoring network connectivity
     */
    fun startMonitoring() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            
            connectivityManager.registerNetworkCallback(request, networkCallback)
            updateNetworkStatus() // Initial status check
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring", e)
        }
    }
    
    /**
     * Stop monitoring network connectivity
     */
    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitoring", e)
        }
    }
    
    /**
     * Check if currently connected to network
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connection", e)
            false
        }
    }
    
    /**
     * Update network status and type
     */
    private fun updateNetworkStatus() {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            if (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
                _isConnected.value = true
                
                _networkType.value = when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                    else -> NetworkType.OTHER
                }
                
                Log.d(TAG, "Network connected: ${_networkType.value}")
            } else {
                _isConnected.value = false
                _networkType.value = NetworkType.NONE
                Log.d(TAG, "Network disconnected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating network status", e)
            _isConnected.value = false
            _networkType.value = NetworkType.NONE
        }
    }
}