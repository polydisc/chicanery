package shopping.backend.service

import munit.CatsEffectSuite

class AuthTokenServiceSpec extends CatsEffectSuite:

    private val auth = AuthTokenService.Live("test-secret", 3600)

    test("issue then verify round-trips to the user id"):
        assertIO(auth.issue(42L).flatMap(auth.verify), Some(42L))

    test("verify rejects a garbage token"):
        assertIO(auth.verify("not.a.jwt"), None)

    test("verify rejects a token signed with a different secret"):
        val other = AuthTokenService.Live("other-secret", 3600)
        assertIO(other.issue(1L).flatMap(auth.verify), None)
