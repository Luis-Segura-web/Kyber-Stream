package com.kybers.play.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rate limiting interceptor for TMDB API requests
 * Prevents exceeding API rate limits by controlling request frequency
 */
class RateLimitInterceptor(
    private val maxRequestsPerSecond: Int = 40, // TMDB allows 40 requests per 10 seconds
    private val timeWindowSeconds: Long = 10
) : Interceptor {

    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val windowStartTimes = ConcurrentHashMap<String, AtomicLong>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Only apply rate limiting to TMDB requests
        if (host.contains("themoviedb.org")) {
            enforceRateLimit(host)
        }

        return chain.proceed(request)
    }

    private fun enforceRateLimit(host: String) {
        val currentTime = System.currentTimeMillis()
        val timeWindowMs = timeWindowSeconds * 1000

        // Get or create counters for this host
        val requestCount = requestCounts.computeIfAbsent(host) { AtomicInteger(0) }
        val windowStartTime = windowStartTimes.computeIfAbsent(host) { AtomicLong(currentTime) }

        // Check if we need to reset the window
        if (currentTime - windowStartTime.get() >= timeWindowMs) {
            // Reset the window
            windowStartTime.set(currentTime)
            requestCount.set(0)
        }

        // Check if we've exceeded the rate limit
        val currentCount = requestCount.incrementAndGet()
        if (currentCount > maxRequestsPerSecond) {
            // Calculate how long to wait
            val timeUntilReset = timeWindowMs - (currentTime - windowStartTime.get())
            if (timeUntilReset > 0) {
                try {
                    Thread.sleep(timeUntilReset)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                // Reset after waiting
                windowStartTime.set(System.currentTimeMillis())
                requestCount.set(1)
            }
        }
    }
}