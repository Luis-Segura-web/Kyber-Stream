package com.kybers.play.ui.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.kybers.play.data.preferences.PreferenceManager

/**
 * Provides intelligent settings recommendations based on device capabilities and network conditions
 */
class DynamicSettingsManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    
    /**
     * Recommends optimal buffer size based on current network conditions
     */
    fun getRecommendedBufferSize(): String {
        val networkType = getCurrentNetworkType()
        return when (networkType) {
            NetworkType.WIFI -> "LARGE"
            NetworkType.CELLULAR_5G -> "LARGE"
            NetworkType.CELLULAR_4G -> "MEDIUM"
            NetworkType.CELLULAR_3G -> "SMALL"
            NetworkType.UNKNOWN -> "MEDIUM"
        }
    }
    
    /**
     * Recommends hardware acceleration setting based on device capabilities
     */
    fun getRecommendedHardwareAcceleration(): Boolean {
        // Enable hardware acceleration for modern devices (API 24+)
        // and when the device has sufficient processing power
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    /**
     * Recommends sync frequency based on usage patterns
     */
    fun getRecommendedSyncFrequency(): Int {
        // For now, return a sensible default
        // In a real implementation, this could analyze usage patterns
        return when (getCurrentNetworkType()) {
            NetworkType.WIFI -> 6  // More frequent sync on WiFi
            NetworkType.CELLULAR_5G -> 12
            NetworkType.CELLULAR_4G -> 12
            NetworkType.CELLULAR_3G -> 24  // Less frequent on slower networks
            NetworkType.UNKNOWN -> 12
        }
    }
    
    /**
     * Suggests theme based on time of day and user preferences
     */
    fun getSuggestedTheme(): String {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        
        // Suggest dark theme during evening/night hours (6 PM to 6 AM)
        return if (currentHour >= 18 || currentHour <= 6) {
            "DARK"
        } else {
            "LIGHT"
        }
    }
    
    /**
     * Gets adaptive settings recommendations as a map
     */
    fun getAdaptiveRecommendations(): Map<String, Any> {
        return mapOf(
            "bufferSize" to getRecommendedBufferSize(),
            "hardwareAcceleration" to getRecommendedHardwareAcceleration(),
            "syncFrequency" to getRecommendedSyncFrequency(),
            "suggestedTheme" to getSuggestedTheme(),
            "networkType" to getCurrentNetworkType().name
        )
    }
    
    /**
     * Applies recommended settings automatically
     */
    fun applyRecommendedSettings() {
        val recommendations = getAdaptiveRecommendations()
        
        // Only apply if user hasn't manually customized these settings
        if (shouldApplyRecommendation("bufferSize")) {
            preferenceManager.saveNetworkBuffer(recommendations["bufferSize"] as String)
        }
        
        if (shouldApplyRecommendation("hardwareAcceleration")) {
            preferenceManager.saveHwAcceleration(recommendations["hardwareAcceleration"] as Boolean)
        }
        
        if (shouldApplyRecommendation("syncFrequency")) {
            preferenceManager.saveSyncFrequency(recommendations["syncFrequency"] as Int)
        }
    }
    
    private fun getCurrentNetworkType(): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.UNKNOWN
        
        val network = connectivityManager.activeNetwork ?: return NetworkType.UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.UNKNOWN
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Try to determine cellular type based on link speed
                val linkSpeed = capabilities.linkDownstreamBandwidthKbps
                when {
                    linkSpeed >= 50000 -> NetworkType.CELLULAR_5G  // 50 Mbps+
                    linkSpeed >= 5000 -> NetworkType.CELLULAR_4G   // 5 Mbps+
                    else -> NetworkType.CELLULAR_3G
                }
            }
            else -> NetworkType.UNKNOWN
        }
    }
    
    private fun shouldApplyRecommendation(settingKey: String): Boolean {
        // Check if the user has manually changed this setting
        // For now, we'll assume they haven't if it's still at default values
        return when (settingKey) {
            "bufferSize" -> preferenceManager.getNetworkBuffer() == "MEDIUM"
            "hardwareAcceleration" -> preferenceManager.getHwAcceleration() == true
            "syncFrequency" -> preferenceManager.getSyncFrequency() == 12
            else -> false
        }
    }
}

enum class NetworkType {
    WIFI,
    CELLULAR_5G,
    CELLULAR_4G,
    CELLULAR_3G,
    UNKNOWN
}