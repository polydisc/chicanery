package shopping.backend.service

import cats.effect.IO
import java.time.Clock
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import scala.util.Success

// Issues and verifies stateless HS256 JWTs whose subject is the user id. Time
// reads (issuedNow/expiresIn, expiry validation) are side effects -> suspended
// in IO.
trait AuthTokenService:
    def issue(userId: Long): IO[String]
    def verify(token: String): IO[Option[Long]]

object AuthTokenService:

    final class Live(secret: String, expirySeconds: Long)
        extends AuthTokenService:

        // `issuedNow`/`expiresIn` read this clock; `Jwt.decode` validates expiry
        // against its own system clock. Both are system UTC and the reads happen
        // inside the IO blocks below, so they stay suspended.
        private given clock: Clock = Clock.systemUTC()

        def issue(userId: Long): IO[String] = IO {
            val claim = JwtClaim()
                .about(userId.toString)
                .issuedNow
                .expiresIn(expirySeconds)
            Jwt.encode(claim, secret, JwtAlgorithm.HS256)
        }

        def verify(token: String): IO[Option[Long]] = IO {
            Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)) match
                case Success(claim) =>
                    claim.subject.flatMap(_.toLongOption)
                case _ =>
                    None
        }
