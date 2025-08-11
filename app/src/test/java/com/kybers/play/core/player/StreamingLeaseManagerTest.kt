package com.kybers.play.core.player

import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tests unitarios para StreamingLeaseManager
 * Valida la funcionalidad crítica de single-connection policy
 */
class StreamingLeaseManagerTest {

    private val fixedTime = Instant.parse("2024-01-01T12:00:00Z")
    private val testClock = Clock.fixed(fixedTime, ZoneOffset.UTC)
    private val leaseManager = StreamingLeaseManager(testClock)

    @Test
    fun `should acquire lease when none exists`() = runTest {
        val result = leaseManager.tryAcquire("test-owner-1")
        assertTrue("Should successfully acquire lease", result)
        
        val state = leaseManager.state.value
        assertTrue("State should be Acquired", state is StreamingLeaseManager.LeaseState.Acquired)
        assertEquals("Owner should match", "test-owner-1", (state as StreamingLeaseManager.LeaseState.Acquired).ownerId)
    }

    @Test
    fun `should reject second acquisition attempt`() = runTest {
        // Primer lease
        assertTrue("First lease should succeed", leaseManager.tryAcquire("owner-1"))
        
        // Segundo lease debería fallar
        assertFalse("Second lease should fail", leaseManager.tryAcquire("owner-2"))
    }

    @Test
    fun `should allow same owner to reacquire`() = runTest {
        val ownerId = "same-owner"
        
        assertTrue("Initial acquisition should succeed", leaseManager.tryAcquire(ownerId))
        assertTrue("Same owner reacquisition should succeed", leaseManager.tryAcquire(ownerId))
    }

    @Test
    fun `force acquire should override existing lease`() = runTest {
        // Establecer lease inicial
        leaseManager.tryAcquire("original-owner")
        
        // Forzar nuevo lease
        leaseManager.forceAcquire("new-owner")
        
        val state = leaseManager.state.value
        assertTrue("State should be Acquired", state is StreamingLeaseManager.LeaseState.Acquired)
        assertEquals("New owner should have lease", "new-owner", (state as StreamingLeaseManager.LeaseState.Acquired).ownerId)
    }

    @Test
    fun `should enforce cooldown after release`() = runTest {
        val ownerId = "test-owner"
        
        // Adquirir y liberar
        leaseManager.tryAcquire(ownerId)
        leaseManager.release(ownerId, Duration.ofSeconds(5))
        
        // Verificar que está en cooldown
        assertTrue("Should be in cooldown", leaseManager.getCooldownRemainingMs() > 0)
        
        // Intentar adquirir durante cooldown debería fallar
        assertFalse("Should fail during cooldown", leaseManager.tryAcquire("another-owner"))
    }

    @Test
    fun `hasLease should return correct status`() = runTest {
        val ownerId = "test-owner"
        
        assertFalse("Should not have lease initially", leaseManager.hasLease(ownerId))
        
        leaseManager.tryAcquire(ownerId)
        assertTrue("Should have lease after acquisition", leaseManager.hasLease(ownerId))
        
        leaseManager.release(ownerId)
        assertFalse("Should not have lease after release", leaseManager.hasLease(ownerId))
    }

    @Test
    fun `withLease should execute operation with valid lease`() = runTest {
        val ownerId = "test-owner"
        var operationExecuted = false
        
        val result = leaseManager.withLease(ownerId) {
            operationExecuted = true
            "success"
        }
        
        assertEquals("Should return operation result", "success", result)
        assertTrue("Operation should have been executed", operationExecuted)
        assertTrue("Should still have lease", leaseManager.hasLease(ownerId))
    }

    @Test
    fun `withLease should return null when lease unavailable`() = runTest {
        // Ocupar lease
        leaseManager.tryAcquire("existing-owner")
        
        val result = leaseManager.withLease("different-owner") {
            fail("Operation should not execute")
            "should-not-happen"
        }
        
        assertNull("Should return null when lease unavailable", result)
    }
}