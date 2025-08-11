package com.kybers.play.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralizado de lease de streaming para garantizar la política de una sola conexión
 * activa por dispositivo, cumpliendo con los requisitos de proveedores IPTV.
 */
@Singleton
class StreamingLeaseManager @Inject constructor(
    private val clock: Clock = Clock.systemUTC()
) {
    
    private val mutex = Mutex()
    private var owner: String? = null
    private var cooldownUntil: Instant? = null

    private val _state = MutableStateFlow<LeaseState>(LeaseState.None)
    val state: StateFlow<LeaseState> = _state.asStateFlow()

    /**
     * Intenta adquirir el lease de streaming de manera exclusiva
     * @param ownerId Identificador único del propietario del lease
     * @return true si se adquirió exitosamente, false si ya está ocupado o en cooldown
     */
    suspend fun tryAcquire(ownerId: String): Boolean = mutex.withLock {
        val now = Instant.now(clock)
        
        // Verificar si estamos en período de cooldown
        if (cooldownUntil?.isAfter(now) == true) {
            return false
        }
        
        // Si no hay propietario actual, adquirir el lease
        if (owner == null) {
            owner = ownerId
            _state.value = LeaseState.Acquired(ownerId)
            return true
        }
        
        // Si el mismo propietario intenta adquirir nuevamente, permitirlo
        if (owner == ownerId) {
            _state.value = LeaseState.Acquired(ownerId)
            return true
        }
        
        return false
    }

    /**
     * Fuerza la adquisición del lease, liberando el anterior si existe
     * @param ownerId Identificador único del nuevo propietario
     */
    suspend fun forceAcquire(ownerId: String) = mutex.withLock {
        owner = ownerId
        cooldownUntil = null
        _state.value = LeaseState.Acquired(ownerId)
    }

    /**
     * Libera el lease y establece un período de cooldown
     * @param ownerId Identificador del propietario que libera el lease
     * @param cooldown Duración del cooldown (por defecto 2 segundos)
     */
    suspend fun release(ownerId: String, cooldown: Duration = Duration.ofSeconds(2)) = mutex.withLock {
        if (owner == ownerId) {
            owner = null
            cooldownUntil = Instant.now(clock).plus(cooldown)
            _state.value = LeaseState.None
        }
    }

    /**
     * Ejecuta una operación con lease garantizado
     * @param ownerId Identificador del propietario
     * @param operation Operación a ejecutar
     * @return Resultado de la operación o null si no se pudo adquirir el lease
     */
    suspend fun <T> withLease(ownerId: String, operation: suspend () -> T): T? {
        return if (tryAcquire(ownerId)) {
            try {
                operation()
            } finally {
                // No liberar automáticamente - debe ser manual para mantener la sesión
            }
        } else {
            null
        }
    }

    /**
     * Obtiene el tiempo restante de cooldown en milisegundos
     * @return Tiempo restante en ms, 0 si no hay cooldown activo
     */
    fun getCooldownRemainingMs(): Long = mutex.tryLock().let { acquired ->
        if (!acquired) return 0L
        
        try {
            val now = Instant.now(clock)
            val remaining = cooldownUntil?.let { cooldownEnd ->
                if (cooldownEnd.isAfter(now)) {
                    Duration.between(now, cooldownEnd).toMillis()
                } else {
                    0L
                }
            } ?: 0L
            remaining
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Verifica si un propietario específico tiene el lease actualmente
     */
    fun hasLease(ownerId: String): Boolean {
        return owner == ownerId
    }

    /**
     * Estados posibles del lease de streaming
     */
    sealed interface LeaseState {
        /** No hay lease activo */
        data object None : LeaseState
        
        /** Lease adquirido por el propietario especificado */
        data class Acquired(val ownerId: String) : LeaseState
    }
}