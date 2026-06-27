package shopping.backend.service

import munit.CatsEffectSuite

class PasswordHasherSpec extends CatsEffectSuite:

    // Low work factor keeps the test fast; production uses the default (12).
    private val hasher = PasswordHasher.BCryptHasher(logRounds = 4)

    test("hash then verify round-trips"):
        assertIO(
          hasher.hash("s3cret").flatMap(hasher.verify("s3cret", _)),
          true
        )

    test("verify rejects a wrong password"):
        assertIO(
          hasher.hash("s3cret").flatMap(hasher.verify("wrong", _)),
          false
        )
