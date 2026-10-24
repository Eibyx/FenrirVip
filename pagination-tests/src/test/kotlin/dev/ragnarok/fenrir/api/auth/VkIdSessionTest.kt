package dev.ragnarok.fenrir.api.auth

import dev.ragnarok.fenrir.api.exceptions.AuthException
import dev.ragnarok.fenrir.api.exceptions.NeedValidationException
import org.junit.Assert.*
import org.junit.Test

class VkIdSessionTest {
    @Test fun deliveryMethodsUseTheActualVkEndpointCapitalization() {
        assertEquals("Sms", VkIdSession.otpSuffix("sms"))
        assertEquals("Email", VkIdSession.otpSuffix("email"))
        assertEquals("Push", VkIdSession.otpSuffix("push"))
        assertEquals("CallReset", VkIdSession.otpSuffix("callreset"))
        assertThrows(IllegalArgumentException::class.java) { VkIdSession.otpSuffix("password") }
    }

    private fun account(session: VkIdSession, sid: String? = "account-A") {
        assertTrue(session.account(session.beginAccount()!!, sid))
    }

    private fun confirmed(session: VkIdSession) {
        account(session)
        val send = session.begin(VkIdSession.Operation.SEND_OTP)!!
        assertEquals("account-A", send.sid)
        session.otpSent(send, "otp-B")
        val check = session.begin(VkIdSession.Operation.CHECK_OTP)!!
        assertEquals("otp-B", check.sid)
        session.confirmed(check, "confirmed-C")
    }

    private fun challenge(session: VkIdSession) {
        account(session)
        val token = session.beginToken(passwordMethod = true, passwordRequired = true)!!
        assertEquals("account-A", token.sid)
        session.challenge(token, "oauth-D")
    }

    @Test fun passwordUsesOnlyTheFreshAccountStageSid() {
        val session = VkIdSession()
        account(session)
        val request = session.beginToken(true, true)!!
        assertEquals("password", request.grant)
        assertEquals("account-A", request.sid)
        assertFalse(request.needsCode)
    }

    @Test fun passwordCanStartWhenAccountValidationReturnsNoSid() {
        val session = VkIdSession()
        account(session, null)
        assertNull(session.beginToken(true, true)!!.sid)
    }

    @Test fun explicitPasswordSelectionUsesTheLatestStageRotation() {
        val session = VkIdSession()
        account(session)
        session.otpSent(session.begin(VkIdSession.Operation.SEND_OTP)!!, "otp-B")
        assertEquals("otp-B", session.beginToken(true, true)!!.sid)
    }

    @Test fun rotatedOtpSidAndConfirmedExchangeAreUsedInSequence() {
        val session = VkIdSession()
        confirmed(session)
        val token = session.beginToken(false, false)!!
        assertEquals("confirmed-C", token.sid)
        assertEquals("without_password", token.grant)
        assertFalse(token.needsCode)
    }

    @Test fun confirmedPasswordRequirementUsesPhoneConfirmationGrant() {
        val session = VkIdSession()
        confirmed(session)
        assertEquals("phone_confirmation_sid", session.beginToken(false, true)!!.grant)
    }

    @Test fun optionalSendSidPreservesOnlyTheActualRequestSession() {
        val session = VkIdSession()
        account(session)
        session.otpSent(session.begin(VkIdSession.Operation.SEND_OTP)!!, null)
        assertEquals("account-A", session.begin(VkIdSession.Operation.CHECK_OTP)!!.sid)
    }

    @Test fun missingCheckResultCannotExchangeThePossiblyConsumedOtpSid() {
        val session = VkIdSession()
        account(session)
        val check = session.begin(VkIdSession.Operation.CHECK_OTP)!!
        assertThrows(VkIdSession.MissingSession::class.java) { session.confirmed(check, null) }
        session.failed(check, uncertain = true)
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(false, false) }
    }

    @Test fun authenticatorCodeCanVerifyWithoutSendingAnOtp() {
        val session = VkIdSession()
        account(session)
        val check = session.begin(VkIdSession.Operation.CHECK_OTP)!!
        assertEquals("account-A", check.sid)
        assertTrue(session.confirmed(check, "confirmed-C"))
    }

    @Test fun oauthChallengeReplacesTheNamespaceWithoutChangingTheGrant() {
        val session = VkIdSession()
        confirmed(session)
        val initial = session.beginToken(false, false)!!
        session.challenge(initial, "oauth-D")
        val next = session.beginToken(false, false)!!
        assertEquals("oauth-D", next.sid)
        assertEquals(initial.grant, next.grant)
        assertTrue(next.needsCode)
        assertThrows(VkIdSession.MissingSession::class.java) {
            session.failed(next)
            session.begin(VkIdSession.Operation.SEND_OTP)
        }
    }

    @Test fun missingOAuthChallengeSidIsNeverReplacedByThePreviousEcosystemSid() {
        val session = VkIdSession()
        confirmed(session)
        val initial = session.beginToken(false, false)!!
        assertThrows(VkIdSession.MissingSession::class.java) { session.challenge(initial, " ") }
    }

    @Test fun validatePhoneBlocksCodeSubmissionUntilItsNewSidIsInstalled() {
        val session = VkIdSession()
        challenge(session)
        val phone = session.begin(VkIdSession.Operation.VALIDATE_PHONE)!!
        assertEquals("oauth-D", phone.sid)
        assertNull(session.beginToken(true, true)) // Regression: old UI already stopped loading.
        session.phoneValidated(phone, "oauth-E")
        assertEquals("oauth-E", session.beginToken(true, true)!!.sid)
    }

    @Test fun duplicateClicksDoNotStartTwoRequestsOrDoubleConsumeSid() {
        val session = VkIdSession()
        assertNotNull(session.beginAccount())
        assertNull(session.beginAccount())
        session.reset()
        confirmed(session)
        assertNotNull(session.beginToken(false, false))
        assertNull(session.beginToken(false, false))
    }

    @Test fun anOldAccountResponseCannotReplaceTheNewAttempt() {
        val session = VkIdSession()
        val old = session.beginAccount()!!
        session.reset()
        account(session, "new-user-session")
        assertFalse(session.account(old, "stale-user-session"))
        assertEquals("new-user-session", session.begin(VkIdSession.Operation.SEND_OTP)!!.sid)
    }

    @Test fun aLatePhoneResponseCannotOverwriteARevalidatedAccount() {
        val session = VkIdSession()
        challenge(session)
        val old = session.begin(VkIdSession.Operation.VALIDATE_PHONE)!!
        session.reset()
        account(session, "new-user-session")
        assertFalse(session.phoneValidated(old, "stale-oauth-session"))
        assertEquals("new-user-session", session.begin(VkIdSession.Operation.CHECK_OTP)!!.sid)
    }

    @Test fun wrongCodeKeepsTheOAuthChallengeAndRequiresANewCode() {
        val session = VkIdSession()
        challenge(session)
        val wrong = session.beginToken(true, true)!!
        session.failed(wrong)
        val retry = session.beginToken(true, true)!!
        assertEquals("oauth-D", retry.sid)
        assertTrue(retry.needsCode) // Regression: old error handler set tokenNeedCode=false.
    }

    @Test fun uncertainConfirmedTokenExchangeCannotBeReplayed() {
        val session = VkIdSession()
        confirmed(session)
        session.failed(session.beginToken(false, false)!!, uncertain = true)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(false, false) }
    }

    @Test fun uncertainOAuthCodeExchangeCannotBeAutomaticallyReplayed() {
        val session = VkIdSession()
        challenge(session)
        session.failed(session.beginToken(true, true)!!, uncertain = true)
        assertFalse(session.needsTokenCode)
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(true, true) }
    }

    @Test fun anExplicitCaptchaCanBeSolvedBeforeAManualRetry() {
        val session = VkIdSession()
        confirmed(session)
        session.failed(session.beginToken(false, false)!!, explicitCaptcha = true)
        assertEquals("confirmed-C", session.beginToken(false, false)!!.sid)
    }

    @Test fun successCannotBeDeliveredTwice() {
        val session = VkIdSession()
        confirmed(session)
        val token = session.beginToken(false, false)!!
        assertTrue(session.succeeded(token))
        assertFalse(session.succeeded(token))
        assertEquals(VkIdSession.Phase.FINISHED, session.phase)
    }

    @Test fun unknownServerThrottleCannotBeClearedByEditingOrRestarting() {
        val session = VkIdSession()
        session.block()
        session.reset()
        assertTrue(session.blocked)
        assertNull(session.beginAccount())
    }

    @Test fun explicitRetryAfterExpiresWithoutStartingAnyAutomaticRequest() {
        var now = 1000L
        val session = VkIdSession(now = { now })
        session.block(30)
        now += 29999
        assertNull(session.beginAccount())
        now++
        assertFalse(session.busy)
        assertNotNull(session.beginAccount())
    }

    @Test fun numericEcosystemErrorAndOAuthErrorBothRecognizeInvalidSid() {
        assertEquals(AuthFailurePolicy.Kind.SID_INVALID, AuthFailurePolicy.classify(100, null, "sid is invalid"))
        assertEquals(AuthFailurePolicy.Kind.SID_INVALID, AuthFailurePolicy.classify(null, "invalid_grant", "sid is invalid"))
        assertEquals(AuthFailurePolicy.Kind.SID_INVALID, AuthFailurePolicy.classify(null, "sid_expired", null))
        assertEquals(AuthFailurePolicy.Kind.OTHER, AuthFailurePolicy.classify(100, null, "inside validation request"))
    }

    @Test fun rejectedAccountTokenExchangeDoesNotReuseTheAccountSid() {
        val session = VkIdSession()
        account(session)
        session.failed(session.beginToken(true, true)!!)
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(true, true) }
    }

    @Test fun explicitServerExpiryPreventsSubmittingAnExpiredChallenge() {
        var epoch = 1000L
        val session = VkIdSession(epochSeconds = { epoch })
        account(session)
        session.challenge(session.beginToken(true, true)!!, "oauth-D", expiresAt = 1010)
        epoch = 1010
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(true, true) }
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
    }

    @Test fun alreadyExpiredServerChallengeIsNotAccepted() {
        val session = VkIdSession(epochSeconds = { 1000 })
        account(session)
        val token = session.beginToken(true, true)!!
        assertThrows(VkIdSession.MissingSession::class.java) { session.challenge(token, "oauth-D", 999) }
    }

    @Test fun absentExpiryDoesNotInventALocalSidTtl() {
        var epoch = 1000L
        val session = VkIdSession(epochSeconds = { epoch })
        account(session)
        session.challenge(session.beginToken(true, true)!!, "oauth-D")
        epoch += 24 * 60 * 60
        assertEquals("oauth-D", session.beginToken(true, true)!!.sid)
    }

    @Test fun throttleWinsOverSidTextAndNoFiveHourTtlIsInvented() {
        for (code in listOf(6, 9, 29))
            assertEquals(AuthFailurePolicy.Kind.THROTTLED, AuthFailurePolicy.classify(code, null, "sid is invalid"))
        assertEquals(AuthFailurePolicy.Kind.THROTTLED, AuthFailurePolicy.classify(null, null, null, 429))
        assertEquals(AuthFailurePolicy.Kind.THROTTLED, AuthFailurePolicy.classify(null, "invalid_request", "Too many auth attempts. Try again later"))
    }

    @Test fun wrappedAuthAndNeedValidationExceptionsAreRecovered() {
        val oauth = AuthException("invalid_grant", "sid is invalid")
        val challenge = NeedValidationException(null, "2fa_sms", null, "private-oauth-sid", null)
        for (error in listOf(oauth, challenge)) {
            val wrapped = RuntimeException(RuntimeException(error))
            assertSame(error, AuthFailurePolicy.cause(wrapped) { it is AuthException || it is NeedValidationException })
        }
    }

    @Test fun diagnosticStateAndRequestStringDoNotExposeSid() {
        val secret = "private-session-555-CANARY"
        val session = VkIdSession()
        account(session, secret)
        val request = session.begin(VkIdSession.Operation.SEND_OTP)!!
        assertFalse(session.diagnosticState().contains(secret))
        assertFalse(request.toString().contains(secret))
        assertEquals("other", AuthFailurePolicy.safeOAuthCode("phone-password-token-2fa-CANARY"))
    }

    @Test fun authenticationHeadersAndBodiesAreNeverPassedToTheGenericLogger() {
        for (path in listOf("/token", "/method/auth.validateAccount", "/method/ecosystem.sendOtpSms",
            "/method/ecosystem.checkOtp", "/method/auth.validatePhone", "/get_anonym_token"))
            assertTrue(path, AuthFailurePolicy.suppressBodyLogging(path))
        assertFalse(AuthFailurePolicy.suppressBodyLogging("/method/wall.getComments"))
    }
}
