package dev.ragnarok.fenrir.dialog.directauth

import android.util.Log
import dev.ragnarok.fenrir.BuildConfig
import dev.ragnarok.fenrir.api.auth.AuthFailurePolicy
import dev.ragnarok.fenrir.api.auth.VkIdSession
import dev.ragnarok.fenrir.api.exceptions.ApiException
import dev.ragnarok.fenrir.api.exceptions.AuthException
import dev.ragnarok.fenrir.api.rest.HttpException

internal object VkIdDiagnostics {
    fun cause(error: Throwable): Throwable {
        return AuthFailurePolicy.cause(error) { result ->
            result is ApiException || result is AuthException ||
                result is dev.ragnarok.fenrir.api.exceptions.NeedValidationException ||
                result is dev.ragnarok.fenrir.api.exceptions.VKIdCaptchaNeedException ||
                result is HttpException
        }
    }

    fun kind(error: Throwable): AuthFailurePolicy.Kind {
        val value = cause(error)
        return AuthFailurePolicy.classify(
            apiCode(value),
            (value as? AuthException)?.code, value.message, (value as? HttpException)?.code
        )
    }

    private fun apiCode(error: Throwable?) = (error as? ApiException)?.error?.errorCode
        ?: (error as? AuthException)?.code?.toIntOrNull()?.takeIf { it in 0..99999 }

    fun record(request: VkIdSession.Request, event: Event, session: VkIdSession, error: Throwable? = null, verificationMethod: String? = null): String {
        val value = error?.let(::cause)
        val apiCode = apiCode(value)?.toString() ?: "none"
        val http = (value as? HttpException)?.code?.toString() ?: "none"
        val oauth = when (value) {
            is dev.ragnarok.fenrir.api.exceptions.NeedValidationException -> "need_validation"
            is dev.ragnarok.fenrir.api.exceptions.VKIdCaptchaNeedException -> "need_captcha"
            else -> AuthFailurePolicy.safeOAuthCode((value as? AuthException)?.code)
        }
        val method = verificationMethod?.takeIf { it in setOf("password", "push", "email", "sms", "callreset", "reserve_code", "codegen") } ?: "other"
        val endpoint = if (request.operation == VkIdSession.Operation.SEND_OTP && method in setOf("sms", "push", "email", "callreset"))
            request.operation.method + VkIdSession.otpSuffix(method) else request.operation.method
        val result = "build=${BuildConfig.SOURCE_REVISION.take(12)} variant=${BuildConfig.BUILD_TYPE} " +
            "method=$endpoint verification=$method event=${event.name} api_code=$apiCode oauth=$oauth http=$http " +
            "kind=${value?.let(::kind)?.name ?: "none"} ${session.diagnosticState()}"
        // Never pass Throwable, URLs, request/response bodies, or opaque identifiers to Log.
        Log.i("FenrirAuth", result)
        return result
    }

    enum class Event { START, SUCCESS, ERROR, STALE }
}
