package com.nimbleflux.glucosesync.shared.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseErrorTest {

    @Test
    fun invalidCredentials_isSingleton() {
        assertTrue(GlucoseError.InvalidCredentials === GlucoseError.InvalidCredentials)
    }

    @Test
    fun invalidCredentials_hasDescriptiveMessage() {
        assertEquals("Invalid credentials", GlucoseError.InvalidCredentials.message)
    }

    @Test
    fun sessionExpired_carriesNoCause() {
        assertNull(GlucoseError.SessionExpired.cause)
    }

    @Test
    fun networkError_wrapsCause() {
        val cause = java.io.IOException("timeout")
        val err = GlucoseError.NetworkError(cause)
        assertEquals("No internet connection", err.message)
        assertEquals(cause, err.cause)
    }

    @Test
    fun serverError_preservesCodeAndMessage() {
        val err = GlucoseError.ServerError(code = 403, message = "Forbidden")
        assertEquals(403, err.code)
        assertEquals("Forbidden", err.message)
    }

    @Test
    fun parseError_acceptsOptionalCause() {
        val withCause = GlucoseError.ParseError("missing field", java.io.IOException("boom"))
        assertNotNull(withCause.cause)
        val withoutCause = GlucoseError.ParseError("missing field")
        assertNull(withoutCause.cause)
    }

    @Test
    fun allVariants_areSubclassesOfException() {
        val variants: List<GlucoseError> = listOf(
            GlucoseError.InvalidCredentials,
            GlucoseError.TermsNotAccepted,
            GlucoseError.SessionExpired,
            GlucoseError.NotLoggedIn,
            GlucoseError.NoPatientSelected,
            GlucoseError.NoPatients,
            GlucoseError.NoData,
            GlucoseError.NetworkError(java.io.IOException("x")),
            GlucoseError.ServerError(500, "boom"),
            GlucoseError.ParseError("x"),
            GlucoseError.Unknown("x")
        )
        variants.forEach { assertTrue(it is Exception) }
    }

    @Test
    fun sealedHierarchy_whenExhaustive_compilesWithoutElse() {
        val error: GlucoseError = GlucoseError.SessionExpired
        val label: String = when (error) {
            is GlucoseError.InvalidCredentials -> "bad creds"
            is GlucoseError.TermsNotAccepted -> "terms"
            is GlucoseError.SessionExpired -> "expired"
            is GlucoseError.NotLoggedIn -> "logged out"
            is GlucoseError.NoPatientSelected -> "no patient"
            is GlucoseError.NoPatients -> "empty"
            is GlucoseError.NoData -> "no data"
            is GlucoseError.NetworkError -> "net"
            is GlucoseError.ServerError -> "server ${error.code}"
            is GlucoseError.ParseError -> "parse"
            is GlucoseError.Unknown -> "unknown"
        }
        assertEquals("expired", label)
    }
}
