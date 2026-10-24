package dev.ragnarok.fenrir.api.auth

import org.junit.Assert.*
import org.junit.Test

class AccountFailureRegressionTest {
    private fun failure(kind: AuthFailurePolicy.Kind, uncertain: Boolean = false): AuthFailurePolicy.Recovery {
        val session = VkIdSession()
        val request = session.beginAccount()!!
        assertTrue(session.failed(request, uncertain = uncertain))
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        assertFalse(session.busy)
        return AuthFailurePolicy.recovery(kind, session.phase)
    }

    @Test fun ordinaryAccountFailureInEmptyPhaseMustNotClaimAnInvalidSid() {
        val session = VkIdSession()
        val request = session.beginAccount()!!
        assertEquals(VkIdSession.Operation.ACCOUNT, request.operation)
        assertNull(request.sid)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        val kind = AuthFailurePolicy.classify(100, null, "Invalid login parameter")
        session.failed(request)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, AuthFailurePolicy.recovery(kind, session.phase))
        assertFalse(session.busy) // No request is started by failure handling.
    }

    @Test fun accountNetworkFailureIsNotEvidenceOfAnInvalidSidEvenAfterReset() {
        val kind = AuthFailurePolicy.classify(null, null, "sid is invalid", networkFailure = true)
        assertEquals(AuthFailurePolicy.Kind.NETWORK, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind, uncertain = true))
    }

    @Test fun apiRejectionsKeepTheirCategoryAndRealCode() {
        for (code in listOf(100, 15, 10, 123456)) {
            val kind = AuthFailurePolicy.classify(code, null, "Invalid request")
            assertEquals(AuthFailurePolicy.Kind.API, kind)
            assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind))
            assertEquals("api_code=$code oauth=other http=none", AuthFailurePolicy.safeCodes(code, null, null))
        }
    }

    @Test fun rejectedAnonymousAuthenticationIsDistinctFromApiParametersAndSid() {
        val kind = AuthFailurePolicy.classify(5, null, "User authorization failed")
        assertEquals(AuthFailurePolicy.Kind.AUTHENTICATION, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind))
        assertEquals("api_code=5 oauth=other http=none", AuthFailurePolicy.safeCodes(5, null, null))
    }

    @Test fun oauthRejectionsPreserveTheAllowedCodeWithoutBeingRelabelledSid() {
        val kind = AuthFailurePolicy.classify(null, "invalid_client", "Client authorization failed")
        assertEquals(AuthFailurePolicy.Kind.AUTHENTICATION, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind))
        assertEquals("api_code=none oauth=invalid_client http=none", AuthFailurePolicy.safeCodes(null, "invalid_client", null))
    }

    @Test fun explicitServerSidErrorsAreStillRecognizedAtTheAccountEndpoint() {
        val kind = AuthFailurePolicy.classify(100, null, "sid is invalid")
        assertEquals(AuthFailurePolicy.Kind.SID_INVALID, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_SESSION, failure(kind))
        assertEquals("api_code=100 oauth=other http=none", AuthFailurePolicy.safeCodes(100, null, null))
    }

    @Test fun missingLocalSessionIsSeparateFromAServerSidRejection() {
        val kind = AuthFailurePolicy.classify(null, null, null, missingSession = true)
        assertEquals(AuthFailurePolicy.Kind.MISSING_SESSION, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_SESSION, failure(kind))
    }

    @Test fun httpFailuresPreserveTheirStatusAndDoNotBecomeSidErrors() {
        for (code in listOf(401, 403, 500, 503)) {
            val kind = AuthFailurePolicy.classify(null, null, null, code)
            assertEquals(AuthFailurePolicy.Kind.HTTP, kind)
            assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind, uncertain = true))
            assertEquals("api_code=none oauth=other http=$code", AuthFailurePolicy.safeCodes(null, null, code))
        }
    }

    @Test fun accountThrottlesRemainBlockedWithTheirOriginalCodes() {
        for (code in listOf(6, 9, 29)) {
            val kind = AuthFailurePolicy.classify(code, null, "sid is invalid")
            assertEquals(AuthFailurePolicy.Kind.THROTTLED, kind)
            assertEquals(AuthFailurePolicy.Recovery.BLOCK, failure(kind))
            assertTrue(AuthFailurePolicy.safeCodes(code, null, null).contains("api_code=$code"))
        }
        val http = AuthFailurePolicy.classify(null, null, null, 429)
        assertEquals(AuthFailurePolicy.Recovery.BLOCK, failure(http))
        assertEquals("api_code=none oauth=other http=429", AuthFailurePolicy.safeCodes(null, null, 429))
    }

    @Test fun captchaIsNotHiddenByTheEmptyAccountPhase() {
        val kind = AuthFailurePolicy.classify(null, null, null, captchaRequired = true)
        assertEquals(AuthFailurePolicy.Recovery.CAPTCHA, failure(kind))
    }

    @Test fun reportedCf8AccountApi14IsCaptchaBeforeAnyPasswordOrOtp() {
        val session = VkIdSession()
        val request = session.beginAccount()!!
        assertNull(request.sid)
        val kind = AuthFailurePolicy.classify(14, null, "Captcha needed")
        assertEquals(AuthFailurePolicy.Kind.CAPTCHA, kind)
        session.failed(request, explicitCaptcha = true)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        assertEquals(AuthFailurePolicy.Recovery.CAPTCHA, AuthFailurePolicy.recovery(kind, session.phase))
        assertEquals("api_code=14 oauth=other http=none", AuthFailurePolicy.safeCodes(14, null, null))
        assertFalse(session.busy)
    }

    @Test fun api14IsNotAnAuthenticationSidEvenIfItsDescriptionMentionsSid() {
        assertEquals(AuthFailurePolicy.Kind.CAPTCHA, AuthFailurePolicy.classify(14, null, "sid is invalid"))
    }

    @Test fun malformedResponseIsNotASessionExpiry() {
        val kind = AuthFailurePolicy.classify(null, null, null, invalidResponse = true)
        assertEquals(AuthFailurePolicy.Kind.INVALID_RESPONSE, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, failure(kind, uncertain = true))
    }

    @Test fun consumedTokenRestartsWithoutChangingTheActualFailureCategory() {
        val session = VkIdSession()
        session.account(session.beginAccount()!!, "account-session-CANARY")
        val token = session.beginToken(true, true)!!
        val kind = AuthFailurePolicy.classify(100, null, "Invalid parameter")
        session.failed(token)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        assertEquals(AuthFailurePolicy.Kind.API, kind)
        assertEquals(AuthFailurePolicy.Recovery.RESTART_REQUEST, AuthFailurePolicy.recovery(kind, session.phase))
        assertFalse(session.busy)
    }

    @Test fun existingWrongCodeChallengeCanStillBeRetriedOnlyManually() {
        val session = VkIdSession()
        session.account(session.beginAccount()!!, "account-A")
        session.challenge(session.beginToken(true, true)!!, "oauth-B")
        val request = session.beginToken(true, true)!!
        session.failed(request)
        val kind = AuthFailurePolicy.classify(null, "invalid_code", "Invalid code")
        assertEquals(AuthFailurePolicy.Recovery.RETRY_MANUALLY, AuthFailurePolicy.recovery(kind, session.phase))
        assertTrue(session.needsTokenCode)
        assertFalse(session.busy)
    }

    @Test fun safeCodesExcludeUnknownServerStringsAndSecrets() {
        val secret = "phone-password-token-sid-otp-CANARY"
        assertEquals("api_code=123456 oauth=other http=503", AuthFailurePolicy.safeCodes(123456, secret, 503))
    }
}
