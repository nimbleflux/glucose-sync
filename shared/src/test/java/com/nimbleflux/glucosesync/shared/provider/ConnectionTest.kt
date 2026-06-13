package com.nimbleflux.glucosesync.shared.provider

import com.nimbleflux.glucosesync.shared.api.model.MonitorConnection
import com.nimbleflux.glucosesync.shared.api.model.SensorStatus
import com.nimbleflux.glucosesync.shared.provider.libre.LibreConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTest {

    @Test
    fun monitorConnection_idReflectsUid() {
        val conn = MonitorConnection(uid = 42L)
        assertEquals("42", conn.id)
    }

    @Test
    fun monitorConnection_displayNameFallsBackToRealName() {
        assertEquals("Alice", MonitorConnection(real_name = "Alice").displayName)
        assertEquals("Alias", MonitorConnection(alias = "Alias", real_name = "Alice").displayName)
    }

    @Test
    fun monitorConnection_sensorActiveOnlyWhenGlucosePositive() {
        assertFalse(MonitorConnection(sensor_status = SensorStatus(glucose = null)).sensorActive)
        assertFalse(MonitorConnection(sensor_status = SensorStatus(glucose = 0.0)).sensorActive)
        assertTrue(MonitorConnection(sensor_status = SensorStatus(glucose = 5.5)).sensorActive)
    }

    @Test
    fun monitorConnection_exposesLastGlucose() {
        assertNull(MonitorConnection().lastGlucoseMmol)
        assertEquals(5.5, MonitorConnection(sensor_status = SensorStatus(glucose = 5.5)).lastGlucoseMmol!!, 0.0001)
    }

    @Test
    fun libreConnection_idReflectsPatientIdNotServerId() {
        val conn = LibreConnection(serverId = "abc-server", patientId = "patient-123")
        assertEquals("patient-123", conn.id)
    }

    @Test
    fun libreConnection_displayNameJoinsFirstAndLast() {
        val conn = LibreConnection(firstName = "Alice", lastName = "Smith")
        assertEquals("Alice Smith", conn.displayName)
    }

    @Test
    fun libreConnection_displayNameTrimsWhenOnlyFirstName() {
        assertEquals("Alice", LibreConnection(firstName = "Alice").displayName)
    }

    @Test
    fun bothImplementations_areConnections() {
        val a: Connection = MonitorConnection(uid = 1L)
        val b: Connection = LibreConnection(patientId = "x")
        assertEquals("1", a.id)
        assertEquals("x", b.id)
    }
}
