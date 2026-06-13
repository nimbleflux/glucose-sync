package com.nimbleflux.glucosesync.shared.provider

/**
 * Common abstraction over a monitored patient/connection presented by a provider.
 *
 * Implemented by both [com.nimbleflux.glucosesync.shared.api.model.MonitorConnection]
 * and [com.nimbleflux.glucosesync.shared.provider.libre.LibreConnection] so the
 * patient-picker UI can treat them uniformly without downcasting the provider.
 */
interface Connection {
    val id: String
    val displayName: String
    val sensorActive: Boolean
    val lastGlucoseMmol: Double?
    val displayUnit: String
}
