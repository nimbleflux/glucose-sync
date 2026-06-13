package com.nimbleflux.glucosesync.shared.provider

/**
 * Typed error hierarchy for provider operations.
 *
 * Extends Exception so it flows naturally through `Result<...>` and
 * coroutine exception handling, while letting callers do `when (error)`
 * for typed handling instead of string-matching on `message`.
 */
sealed class GlucoseError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    data object InvalidCredentials : GlucoseError("Invalid credentials")

    data object TermsNotAccepted : GlucoseError(
        "Terms not accepted. Please accept terms in the LibreLinkUp app first."
    )

    data object SessionExpired : GlucoseError("Session expired. Please sign in again.")

    data object NotLoggedIn : GlucoseError("Not logged in")

    data object NoPatientSelected : GlucoseError("No patient selected")

    data object NoPatients : GlucoseError("No patients found")

    data object NoData : GlucoseError("No data returned by provider")

    class NetworkError(cause: Throwable) : GlucoseError("No internet connection", cause)

    class ServerError(val code: Int, message: String) : GlucoseError(message)

    class ParseError(message: String, cause: Throwable? = null) : GlucoseError(message, cause)

    class Unknown(message: String, cause: Throwable? = null) : GlucoseError(message, cause)
}
