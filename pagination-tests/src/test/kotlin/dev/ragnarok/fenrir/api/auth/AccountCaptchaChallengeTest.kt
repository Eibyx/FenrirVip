package dev.ragnarok.fenrir.api.auth

import org.junit.Assert.*
import org.junit.Test

class AccountCaptchaChallengeTest {
    @Test fun webChallengeUsesOnlyTheExplicitSolutionAndNeverAnAuthenticationSid() {
        val session = VkIdSession()
        val request = session.beginAccount()!!
        session.failed(request, explicitCaptcha = true)
        val challenge = AccountCaptchaChallenge(request, "https://vk.ru/captcha", "captcha-SID", "image")
        assertEquals(AccountCaptchaChallenge.Kind.WEB, challenge.kind)
        assertNull(challenge.webAnswer(request, null))
        assertNull(challenge.webAnswer(request, " "))
        val answer = challenge.webAnswer(request, "solved-web-token")!!
        assertEquals("solved-web-token", answer.successToken)
        assertNull(answer.sid)
        assertNull(answer.key)
        assertFalse(session.busy) // Solving the challenge does not submit a request.
        assertThrows(VkIdSession.MissingSession::class.java) { session.beginToken(true, true) }
    }

    @Test fun imageChallengeUsesItsOwnCaptchaSidAndTheUserAnswer() {
        val session = VkIdSession()
        val request = session.beginAccount()!!
        session.failed(request, explicitCaptcha = true)
        val challenge = AccountCaptchaChallenge(request, null, "captcha-SID", "https://api.vk.ru/captcha.php")
        assertEquals(AccountCaptchaChallenge.Kind.IMAGE, challenge.kind)
        assertNull(challenge.imageAnswer(request, " "))
        val answer = challenge.imageAnswer(request, " image-code ")!!
        assertEquals("captcha-SID", answer.sid)
        assertEquals("image-code", answer.key)
        assertNull(answer.successToken)
        assertFalse(session.busy)
        assertEquals(VkIdSession.Phase.EMPTY, session.phase)
    }

    @Test fun lateCaptchaCompletionsCannotBeAppliedToANewerChallenge() {
        val session = VkIdSession()
        val old = session.beginAccount()!!
        session.failed(old, explicitCaptcha = true)
        val current = session.beginAccount()!!
        val web = AccountCaptchaChallenge(current, "https://vk.ru/captcha", null, null)
        val image = AccountCaptchaChallenge(current, null, "new-captcha-sid", "https://api.vk.ru/captcha.php")
        assertNull(web.webAnswer(old, "stale-web-token"))
        assertNull(image.imageAnswer(old, "stale-code"))
        assertNotNull(web.webAnswer(current, "current-web-token"))
        assertNotNull(image.imageAnswer(current, "current-code"))
    }

    @Test fun absentOrIncompleteChallengeCannotProduceAReplayAnswer() {
        val request = VkIdSession().beginAccount()!!
        for ((sid, image) in listOf(null to null, "captcha-sid" to null, null to "https://vk.ru/image")) {
            val challenge = AccountCaptchaChallenge(request, null, sid, image)
            assertEquals(AccountCaptchaChallenge.Kind.UNAVAILABLE, challenge.kind)
            assertNull(challenge.webAnswer(request, "token"))
            assertNull(challenge.imageAnswer(request, "answer"))
        }
    }

    @Test fun answerTypesCannotBeMixedAndDoNotExposeSensitiveStrings() {
        val request = VkIdSession().beginAccount()!!
        val web = AccountCaptchaChallenge(request, "https://vk.ru/private-url-CANARY", null, null)
        val image = AccountCaptchaChallenge(request, null, "private-sid-CANARY", "private-image-CANARY")
        assertNull(web.imageAnswer(request, "secret"))
        assertNull(image.webAnswer(request, "secret"))
        for (value in listOf(web, image, web.webAnswer(request, "private-token-CANARY"),
            image.imageAnswer(request, "private-code-CANARY"))) {
            assertFalse(value.toString().contains("CANARY"))
        }
    }
}
